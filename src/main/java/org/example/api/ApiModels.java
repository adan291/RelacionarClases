package org.example.api;

import java.util.List;

/**
 * Cuerpos de petición y respuesta de la API de relaciones (contrato 3.2 de docs/FABRICA.md).
 * Son records normales: Gson los crea por reflexión, así que no deben ser value classes.
 */
public final class ApiModels {

    private ApiModels() {
    }

    /** {@code POST /relations/suggest} */
    public record SuggestRequest(List<EntityDto> entities, OptionsDto options) {
    }

    public record EntityDto(String name, List<FieldDto> fields, List<RelationDto> relations) {
    }

    public record FieldDto(String name, String type) {
    }

    /** Relación que ya existe en el modelo del cliente. */
    public record RelationDto(String type, String targetEntity, String fieldName) {
    }

    /** Todos opcionales: null significa "valor por defecto". */
    public record OptionsDto(Double minConfidence, Integer maxResults, Boolean useMemory, Boolean useMl) {
    }

    public record Suggestion(String source, String target, String type, double confidence,
                             String origin, String fieldName, String mappedBy, boolean bidirectional) {
    }

    public record SuggestResponse(List<Suggestion> suggestions, boolean mlAvailable, long durationMs) {
    }

    /** {@code POST /relations/feedback}: un objeto o una lista. */
    public record FeedbackItem(String source, String target, String type, String decision) {
    }

    public record FeedbackResponse(int recorded, boolean retrainScheduled) {
    }

    public record HealthResponse(String status, boolean mlAvailable) {
    }

    /** RFC 7807. */
    public record Problem(String type, String title, int status, String detail) {
    }
}
