package org.example.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.ml.FeatureExtractor;
import org.example.ml.LogisticRegressionModel;
import org.example.ml.MLRelationPredictor;
import org.example.ml.ScoreCombiner;
import org.example.service.MemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;

/**
 * Contrato 3.2 de docs/FABRICA.md probado con javalin-testtools (servidor real en un puerto libre).
 */
class RelationsApiTest {

    private static final String EXAMPLE = """
        {
          "entities": [
            {
              "name": "User",
              "fields": [{ "name": "email", "type": "String" }],
              "relations": [{ "type": "OneToMany", "targetEntity": "Post", "fieldName": "posts" }]
            },
            { "name": "Post", "fields": [{ "name": "title", "type": "String" }, { "name": "authorId", "type": "Long" }], "relations": [] }
          ],
          "options": { "minConfidence": 0.5, "maxResults": 100, "useMemory": true, "useMl": true }
        }
        """;

    @TempDir
    Path tempDir;

    private MemoryService memory;
    private MLRelationPredictor predictor;
    private FeedbackService feedback;
    private Javalin app;

    @BeforeEach
    void setUp() {
        memory = new MemoryService(tempDir.resolve("memoria.json").toString());
        predictor = new MLRelationPredictor(memory, new FeatureExtractor(), new LogisticRegressionModel(42L),
            tempDir.resolve("model.json").toString(), 3);
        IntelligentRelationAnalyzer analyzer = new IntelligentRelationAnalyzer(memory, predictor,
            new ScoreCombiner(0.5, 0.3, 0.2), List.of(), List.of(".java"));
        feedback = new FeedbackService(memory, predictor, Duration.ofMillis(50));
        ApiConfig config = new ApiConfig("127.0.0.1", 0, ApiConfig.DEFAULT_ORIGINS, tempDir, 3, Duration.ofMillis(50));
        app = RelationsApi.create(config, new RelationSuggestionService(analyzer, memory, predictor), feedback);
    }

    @AfterEach
    void tearDown() {
        feedback.close();
    }

    private static JsonObject json(Response res) {
        return JsonParser.parseString(res.body().string()).getAsJsonObject();
    }

    private static Response post(io.javalin.testtools.HttpClient client, String path, String body, String origin) {
        return client.request(path, b -> {
            b.post(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
            if (origin != null) {
                b.header("Origin", origin);
            }
        });
    }

    private static void assertProblem(Response res, int status) {
        assertEquals(status, res.code());
        assertTrue(res.headers().get("Content-Type").get(0).startsWith("application/problem+json"));
        JsonObject p = json(res);
        assertEquals(status, p.get("status").getAsInt());
        assertNotNull(p.get("type").getAsString());
        assertNotNull(p.get("title").getAsString());
        assertNotNull(p.get("detail").getAsString());
    }

    @Test
    void healthReportsStatusAndMl() {
        JavalinTest.test(app, (server, client) -> {
            Response res = client.get("/api/v1/health");
            assertEquals(200, res.code());
            JsonObject body = json(res);
            assertEquals("ok", body.get("status").getAsString());
            assertFalse(body.get("mlAvailable").getAsBoolean(), "Sin decisiones no hay modelo");
        });
    }

    @Test
    void suggestContractExample() {
        JavalinTest.test(app, (server, client) -> {
            Response res = post(client, "/api/v1/relations/suggest", EXAMPLE, null);
            assertEquals(200, res.code(), res.body().string());
            JsonObject body = json(res);
            assertTrue(body.has("mlAvailable"));
            assertTrue(body.get("durationMs").getAsLong() >= 0);
            JsonArray suggestions = body.getAsJsonArray("suggestions");

            JsonObject postToUser = null;
            double previous = Double.MAX_VALUE;
            for (JsonElement e : suggestions) {
                JsonObject s = e.getAsJsonObject();
                double c = s.get("confidence").getAsDouble();
                assertTrue(c <= previous, "Ordenadas por confianza descendente");
                previous = c;
                assertTrue(c >= 0.5);
                assertTrue(Set.of("STRUCTURAL", "SEMANTIC", "ML", "MEMORY", "COMBINED")
                    .contains(s.get("origin").getAsString()));
                assertFalse(s.get("source").getAsString().equals("User") && s.get("target").getAsString().equals("Post"),
                    "La relación existente User -> Post no se devuelve como sugerencia");
                if (s.get("source").getAsString().equals("Post") && s.get("target").getAsString().equals("User")
                        && s.get("type").getAsString().equals("ManyToOne")) {
                    postToUser = s;
                }
            }
            assertNotNull(postToUser, "Debe sugerir el lado inverso Post -> User ManyToOne");
            JsonElement fieldName = postToUser.get("fieldName");
            assertNotNull(fieldName, "La sugerencia trae fieldName");
            assertEquals("author", fieldName.getAsString(), "Nombre tomado de authorId");
            assertTrue(postToUser.get("bidirectional").getAsBoolean());
            assertTrue(postToUser.has("mappedBy") && postToUser.get("mappedBy").isJsonNull(),
                "mappedBy aparece como null en el lado dueño");
        });
    }

    @Test
    void foreignKeyNamingSuggestsManyToOne() {
        String body = """
            { "entities": [
                { "name": "Customer", "fields": [{ "name": "name", "type": "String" }] },
                { "name": "Invoice", "fields": [{ "name": "customerId", "type": "Long" }] } ] }
            """;
        JavalinTest.test(app, (server, client) -> {
            JsonArray s = json(post(client, "/api/v1/relations/suggest", body, null)).getAsJsonArray("suggestions");
            boolean found = false;
            for (JsonElement e : s) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("source").getAsString().equals("Invoice") && o.get("target").getAsString().equals("Customer")
                        && o.get("type").getAsString().equals("ManyToOne")) {
                    found = true;
                    assertEquals("customer", o.get("fieldName").getAsString());
                }
            }
            assertTrue(found, s.toString());
        });
    }

    @Test
    void maxResultsAndMinConfidenceAreApplied() {
        String body = EXAMPLE.replace("\"minConfidence\": 0.5, \"maxResults\": 100", "\"minConfidence\": 0.0, \"maxResults\": 1");
        JavalinTest.test(app, (server, client) -> {
            JsonArray s = json(post(client, "/api/v1/relations/suggest", body, null)).getAsJsonArray("suggestions");
            assertEquals(1, s.size());
        });
    }

    @Test
    void invalidJsonIs400Problem() {
        JavalinTest.test(app, (server, client) ->
            assertProblem(post(client, "/api/v1/relations/suggest", "{ not json", null), 400));
    }

    @Test
    void validationErrorsAre400Problem() {
        JavalinTest.test(app, (server, client) -> {
            assertProblem(post(client, "/api/v1/relations/suggest", "{\"entities\": []}", null), 400);
            assertProblem(post(client, "/api/v1/relations/suggest",
                "{\"entities\": [{\"name\": \"1Bad\", \"fields\": []}]}", null), 400);
            assertProblem(post(client, "/api/v1/relations/suggest",
                "{\"entities\": [{\"name\": \"A\", \"relations\": [{\"type\": \"OneToMany\", \"targetEntity\": \"Ghost\"}]}]}", null), 400);
            assertProblem(post(client, "/api/v1/relations/suggest",
                "{\"entities\": [{\"name\": \"A\", \"relations\": [{\"type\": \"HasMany\", \"targetEntity\": \"A\"}]}]}", null), 400);
            assertProblem(post(client, "/api/v1/relations/suggest",
                "{\"entities\": [{\"name\": \"A\"}], \"options\": {\"minConfidence\": 2}}", null), 400);
        });
    }

    @Test
    void tooManyEntitiesIs413() {
        StringBuilder sb = new StringBuilder("{\"entities\": [");
        for (int i = 0; i <= ApiConfig.MAX_ENTITIES; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"name\": \"E").append(i).append("\"}");
        }
        sb.append("]}");
        JavalinTest.test(app, (server, client) ->
            assertProblem(post(client, "/api/v1/relations/suggest", sb.toString(), null), 413));
    }

    @Test
    void tooManyFieldsIs413() {
        StringBuilder sb = new StringBuilder("{\"entities\": [{\"name\": \"A\", \"fields\": [");
        for (int i = 0; i <= ApiConfig.MAX_FIELDS_PER_ENTITY; i++) {
            sb.append(i == 0 ? "" : ",").append("{\"name\": \"f").append(i).append("\", \"type\": \"String\"}");
        }
        sb.append("]}]}");
        JavalinTest.test(app, (server, client) ->
            assertProblem(post(client, "/api/v1/relations/suggest", sb.toString(), null), 413));
    }

    @Test
    void bodyOverOneMegabyteIs413() {
        String big = "{\"entities\": [], \"padding\": \"" + "x".repeat((int) ApiConfig.MAX_BODY_BYTES + 10) + "\"}";
        JavalinTest.test(app, (server, client) ->
            assertProblem(post(client, "/api/v1/relations/suggest", big, null), 413));
    }

    @Test
    void nonJsonContentTypeIs415() {
        JavalinTest.test(app, (server, client) -> {
            Response res = client.request("/api/v1/relations/suggest",
                b -> b.post(HttpRequest.BodyPublishers.ofString(EXAMPLE)).header("Content-Type", "text/plain"));
            assertProblem(res, 415);
        });
    }

    @Test
    void unknownRouteIs404Problem() {
        JavalinTest.test(app, (server, client) -> assertProblem(client.get("/api/v1/nope"), 404));
    }

    @Test
    void corsOnlyForAllowedOrigins() {
        JavalinTest.test(app, (server, client) -> {
            Response allowed = post(client, "/api/v1/relations/suggest", EXAMPLE, "http://localhost:3000");
            assertEquals(List.of("http://localhost:3000"), allowed.headers().get("Access-Control-Allow-Origin"));

            Response other = post(client, "/api/v1/relations/suggest", EXAMPLE, "http://evil.example");
            List<String> header = other.headers().get("Access-Control-Allow-Origin");
            assertTrue(header == null || header.isEmpty(), "Sin cabecera CORS para orígenes no permitidos");
        });
    }

    @Test
    void preflightAllowedAndRejected() throws Exception {
        JavalinTest.test(app, (server, client) -> {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            HttpRequest ok = HttpRequest.newBuilder(java.net.URI.create(client.getOrigin() + "/api/v1/relations/suggest"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://127.0.0.1:3000")
                .header("Access-Control-Request-Method", "POST").build();
            var okRes = http.send(ok, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(204, okRes.statusCode());
            assertEquals("http://127.0.0.1:3000", okRes.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

            HttpRequest bad = HttpRequest.newBuilder(java.net.URI.create(client.getOrigin() + "/api/v1/relations/suggest"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://evil.example").build();
            var badRes = http.send(bad, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(403, badRes.statusCode());
            assertNull(badRes.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        });
    }

    @Test
    void feedbackIs202AndRetrainsInBackground() {
        JavalinTest.test(app, (server, client) -> {
            Response one = post(client, "/api/v1/relations/feedback",
                "{ \"source\":\"Post\", \"target\":\"User\", \"type\":\"ManyToOne\", \"decision\":\"accepted\" }", null);
            assertEquals(202, one.code(), one.body().string());
            assertEquals("aceptada", memory.getMemoria().get("Post -> User : ManyToOne"));

            Response list = post(client, "/api/v1/relations/feedback", """
                [ { "source":"User", "target":"Post", "type":"ManyToMany", "decision":"rejected" },
                  { "source":"Comment", "target":"Post", "type":"ManyToOne", "decision":"accepted" } ]
                """, null);
            assertEquals(202, list.code());
            assertEquals(2, json(list).get("recorded").getAsInt());
            assertEquals("rechazada", memory.getMemoria().get("User -> Post : ManyToMany"));

            // 3 decisiones >= umbral 3: el reentreno con debounce de 50 ms deja un modelo listo
            long deadline = System.currentTimeMillis() + 10_000;
            while (!predictor.isModelReady() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(predictor.isModelReady(), "El modelo debe reentrenarse en segundo plano");
            assertTrue(json(client.get("/api/v1/health")).get("mlAvailable").getAsBoolean());
        });
    }

    @Test
    void feedbackValidation() {
        JavalinTest.test(app, (server, client) -> {
            assertProblem(post(client, "/api/v1/relations/feedback",
                "{ \"source\":\"Post\", \"target\":\"User\", \"type\":\"ManyToOne\", \"decision\":\"maybe\" }", null), 400);
            assertProblem(post(client, "/api/v1/relations/feedback", "42", null), 400);
            assertTrue(memory.getMemoria().isEmpty());
        });
    }

    @Test
    void rejectedMemoryDecisionIsNotSuggested() {
        memory.recordarDecision("Post -> User : ManyToOne", false);
        JavalinTest.test(app, (server, client) -> {
            JsonArray s = json(post(client, "/api/v1/relations/suggest", EXAMPLE, null)).getAsJsonArray("suggestions");
            for (JsonElement e : s) {
                JsonObject o = e.getAsJsonObject();
                assertFalse(o.get("source").getAsString().equals("Post") && o.get("target").getAsString().equals("User")
                    && o.get("type").getAsString().equals("ManyToOne"), s.toString());
            }
        });
    }

    @Test
    void concurrentSuggestionsAreIndependent() throws Exception {
        JavalinTest.test(app, (server, client) -> {
            List<Thread> threads = new ArrayList<>();
            List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 8; i++) {
                Thread t = new Thread(() -> {
                    try {
                        for (int k = 0; k < 5; k++) {
                            Response r = post(client, "/api/v1/relations/suggest", EXAMPLE, null);
                            assertEquals(200, r.code());
                        }
                    } catch (Throwable ex) {
                        errors.add(ex);
                    }
                });
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            assertTrue(errors.isEmpty(), errors.toString());
        });
    }

    @Test
    void serverPathDoesNotUseConfigManagerNorWorkingDirectory() throws Exception {
        org.example.config.ConfigManager.reset();
        ApiConfig config = new ApiConfig("127.0.0.1", 0, ApiConfig.DEFAULT_ORIGINS, tempDir.resolve("data"), 2,
            Duration.ofMillis(10));
        try (RelationsApiServer server = RelationsApiServer.create(config)) {
            JavalinTest.test(server.app(), (s, client) -> {
                assertEquals(200, post(client, "/api/v1/relations/suggest", EXAMPLE, null).code());
                assertEquals(202, post(client, "/api/v1/relations/feedback",
                    "{ \"source\":\"Post\", \"target\":\"User\", \"type\":\"ManyToOne\", \"decision\":\"accepted\" }",
                    null).code());
            });
        }
        java.lang.reflect.Field instance = org.example.config.ConfigManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        assertNull(instance.get(null), "El camino de la API no debe crear ConfigManager");
        assertTrue(java.nio.file.Files.exists(config.memoryFile()), "La memoria se guarda en RELATIONS_DATA_DIR");
    }

    @Test
    void configFromEnvUsesDefaultsAndDataDir() {
        ApiConfig defaults = ApiConfig.fromEnv(Map.of());
        assertEquals(8095, defaults.port());
        assertEquals("127.0.0.1", defaults.host());
        assertEquals(ApiConfig.DEFAULT_ORIGINS, defaults.allowedOrigins());
        assertFalse(defaults.dataDir().equals(Path.of("").toAbsolutePath()), "Nunca el directorio de trabajo");

        ApiConfig custom = ApiConfig.fromEnv(Map.of("RELATIONS_PORT", "9999",
            "RELATIONS_ALLOWED_ORIGINS", "http://a:1, http://b:2",
            "RELATIONS_DATA_DIR", tempDir.toString()));
        assertEquals(9999, custom.port());
        assertEquals(Set.of("http://a:1", "http://b:2"), custom.allowedOrigins());
        assertEquals(tempDir.resolve("memoria_relaciones.json"), custom.memoryFile());
    }
}
