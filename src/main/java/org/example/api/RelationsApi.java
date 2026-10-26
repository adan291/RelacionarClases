package org.example.api;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.api.ApiModels.FeedbackItem;
import org.example.api.ApiModels.FeedbackResponse;
import org.example.api.ApiModels.HealthResponse;
import org.example.api.ApiModels.Problem;
import org.example.api.ApiModels.SuggestRequest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;

/**
 * Rutas HTTP de la API de relaciones ({@code /api/v1}). Solo construye la aplicación Javalin;
 * arrancarla es cosa de {@link RelationsApiServer} (o de los tests con javalin-testtools).
 */
public final class RelationsApi {

    private static final Logger LOGGER = Logger.getLogger(RelationsApi.class.getName());

    public static final String BASE = "/api/v1";
    static final String JSON = "application/json";
    static final String PROBLEM_JSON = "application/problem+json";

    private RelationsApi() {
    }

    public static Javalin create(ApiConfig config, RelationSuggestionService suggestions, FeedbackService feedback) {
        // serializeNulls: el contrato devuelve "mappedBy": null
        Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

        return Javalin.create(cfg -> {
            cfg.jetty.host = config.host();
            cfg.jetty.port = config.port();
            cfg.http.maxRequestSize = ApiConfig.MAX_BODY_BYTES;
            cfg.startup.showJavalinBanner = false;

            cfg.routes.before(ctx -> applyCors(ctx, config));

            cfg.routes.options(BASE + "/*", ctx -> {
                String origin = ctx.header("Origin");
                if (origin != null && config.allowedOrigins().contains(origin)) {
                    ctx.status(204);
                } else {
                    problem(ctx, gson, 403, "Forbidden", "Origen no permitido para CORS: " + origin);
                }
            });

            cfg.routes.get(BASE + "/health", ctx ->
                json(ctx, gson, 200, new HealthResponse("ok", suggestions.isMlAvailable())));

            cfg.routes.post(BASE + "/relations/suggest", ctx -> {
                SuggestRequest request = readJson(ctx, gson, SuggestRequest.class);
                json(ctx, gson, 200, suggestions.suggest(request));
            });

            cfg.routes.post(BASE + "/relations/feedback", ctx -> {
                JsonElement body = parseBody(ctx);
                List<FeedbackItem> items = new ArrayList<>();
                if (body.isJsonArray()) {
                    for (JsonElement e : body.getAsJsonArray()) {
                        if (!e.isJsonObject()) {
                            throw ApiException.badRequest("Cada decisión debe ser un objeto JSON");
                        }
                        items.add(gson.fromJson(e, FeedbackItem.class));
                    }
                } else if (body.isJsonObject()) {
                    items.add(gson.fromJson(body, FeedbackItem.class));
                } else {
                    throw ApiException.badRequest("Se esperaba un objeto o una lista de objetos");
                }
                int recorded = feedback.record(items);
                json(ctx, gson, 202, new FeedbackResponse(recorded, true));
            });

            cfg.routes.exception(ApiException.class, (e, ctx) ->
                problem(ctx, gson, e.status(), e.title(), e.getMessage()));
            cfg.routes.exception(JsonParseException.class, (e, ctx) ->
                problem(ctx, gson, 400, "Bad Request", "JSON no válido: " + rootMessage(e)));
            cfg.routes.exception(HttpResponseException.class, (e, ctx) ->
                problem(ctx, gson, e.getStatus(), reason(e.getStatus()), e.getMessage()));
            cfg.routes.exception(Exception.class, (e, ctx) -> {
                LOGGER.log(Level.SEVERE, "Error no controlado en " + ctx.path(), e);
                problem(ctx, gson, 500, "Internal Server Error", "Error interno al procesar la petición");
            });
            cfg.routes.error(404, ctx -> {
                if (!PROBLEM_JSON.equals(ctx.res().getContentType())) {
                    problem(ctx, gson, 404, "Not Found", "No existe la ruta " + ctx.method() + " " + ctx.path());
                }
            });
        });
    }

    private static void applyCors(Context ctx, ApiConfig config) {
        String origin = ctx.header("Origin");
        if (origin != null && config.allowedOrigins().contains(origin)) {
            ctx.header("Access-Control-Allow-Origin", origin);
            ctx.header("Vary", "Origin");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
            ctx.header("Access-Control-Max-Age", "600");
        }
    }

    private static JsonElement parseBody(Context ctx) {
        String contentType = ctx.contentType();
        if (contentType != null && !contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            throw new ApiException(415, "Unsupported Media Type", "Content-Type debe ser application/json");
        }
        long declared = ctx.contentLength();
        if (declared > ApiConfig.MAX_BODY_BYTES) {
            throw ApiException.tooLarge("El cuerpo supera " + ApiConfig.MAX_BODY_BYTES + " bytes");
        }
        byte[] bytes = ctx.bodyAsBytes();
        if (bytes.length > ApiConfig.MAX_BODY_BYTES) {
            throw ApiException.tooLarge("El cuerpo supera " + ApiConfig.MAX_BODY_BYTES + " bytes");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("El cuerpo de la petición es obligatorio");
        }
        JsonElement element = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        if (element == null || element.isJsonNull()) {
            throw ApiException.badRequest("El cuerpo de la petición es obligatorio");
        }
        return element;
    }

    private static <T> T readJson(Context ctx, Gson gson, Class<T> type) {
        JsonElement body = parseBody(ctx);
        if (!body.isJsonObject()) {
            throw ApiException.badRequest("Se esperaba un objeto JSON");
        }
        return gson.fromJson(body, type);
    }

    private static void json(Context ctx, Gson gson, int status, Object body) {
        ctx.status(status).contentType(JSON).result(gson.toJson(body));
    }

    static void problem(Context ctx, Gson gson, int status, String title, String detail) {
        ctx.status(status).contentType(PROBLEM_JSON)
            .result(gson.toJson(new Problem("about:blank", title, status, detail)));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }

    private static String reason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type";
            default -> status >= 500 ? "Internal Server Error" : "Error";
        };
    }
}
