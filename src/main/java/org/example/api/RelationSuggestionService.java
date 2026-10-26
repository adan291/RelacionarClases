package org.example.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.analysis.AnalysisOptions;
import org.example.analysis.IntelligentRelationAnalyzer;
import org.example.analysis.MemoryKeys;
import org.example.api.ApiModels.EntityDto;
import org.example.api.ApiModels.FieldDto;
import org.example.api.ApiModels.OptionsDto;
import org.example.api.ApiModels.RelationDto;
import org.example.api.ApiModels.SuggestRequest;
import org.example.api.ApiModels.SuggestResponse;
import org.example.api.ApiModels.Suggestion;
import org.example.ml.ClassInfo;
import org.example.ml.FieldInfo;
import org.example.ml.MLRelationPredictor;
import org.example.ml.NamingPatternAnalyzer;
import org.example.ml.PredictionSource;
import org.example.ml.TypeNames;
import org.example.model.RelacionDetectada;
import org.example.model.RelationKey;
import org.example.service.MemoryService;

/**
 * Fachada de sugerencias para la API (contrato 3.2). No guarda estado entre peticiones: cada
 * llamada construye su modelo, analiza y descarta. Las dependencias llegan por constructor.
 *
 * <p>Pasos:</p>
 * <ol>
 *   <li>Valida la petición y convierte las {@code relations} existentes en campos
 *       ({@code List<Post> posts} o {@code Post post}) para que el análisis estructural las vea.</li>
 *   <li>Ejecuta {@link IntelligentRelationAnalyzer#analizar(Map, AnalysisOptions)}.</li>
 *   <li>Añade dos reglas que el análisis de clases no cubre y que importan con modelos de
 *       frontend: claves ajenas por nombre ({@code userId → User}) y el lado inverso de una
 *       relación existente (si User tiene OneToMany a Post, se sugiere Post ManyToOne User).
 *       Con memoria activa, una decisión aceptada exacta se sugiere (origen MEMORY) y una
 *       rechazada exacta se descarta.</li>
 *   <li>Quita los pares que ya tienen relación en el mismo sentido, filtra por confianza,
 *       ordena de mayor a menor y corta en {@code maxResults}.</li>
 * </ol>
 */
public class RelationSuggestionService {

    static final Set<String> RELATION_TYPES = Set.of("OneToOne", "OneToMany", "ManyToOne", "ManyToMany");
    static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,99}$");
    static final Pattern TYPE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$.<>, ?\\[\\]]{0,199}$");
    /** {@code authorId}, {@code authorID}, {@code author_id}; no {@code uuid} ni {@code paid}. */
    private static final Pattern FOREIGN_KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9]*?)(?:Id|ID|_id|_ID)$");
    private static final Set<String> KEY_TYPES = Set.of(
        "Long", "long", "Integer", "int", "UUID", "java.util.UUID", "String");

    static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    static final int DEFAULT_MAX_RESULTS = 100;
    static final int MAX_RESULTS_LIMIT = 1000;

    static final double FOREIGN_KEY_CONFIDENCE = 0.85;
    static final double INVERSE_CONFIDENCE = 0.80;
    static final double INVERSE_WITH_KEY_BONUS = 0.05;
    static final double MEMORY_ACCEPTED_CONFIDENCE = 0.90;

    private final IntelligentRelationAnalyzer analyzer;
    private final MemoryService memoryService;
    private final MLRelationPredictor predictor;
    private final NamingPatternAnalyzer naming = new NamingPatternAnalyzer();

    public RelationSuggestionService(IntelligentRelationAnalyzer analyzer, MemoryService memoryService,
                                     MLRelationPredictor predictor) {
        this.analyzer = analyzer;
        this.memoryService = memoryService;
        this.predictor = predictor;
    }

    /** @return true si hay un modelo ML entrenado y datos suficientes */
    public boolean isMlAvailable() {
        return predictor != null && predictor.isModelReady();
    }

    public SuggestResponse suggest(SuggestRequest request) {
        long start = System.nanoTime();
        if (request == null) {
            throw ApiException.badRequest("El cuerpo de la petición es obligatorio");
        }
        Options options = Options.from(request.options());
        Model model = Model.from(request.entities(), naming);

        boolean useMl = options.useMl() && predictor != null && predictor.isMLAvailable();
        List<RelacionDetectada> core = analyzer.analizar(model.classes,
            new AnalysisOptions(options.useMemory(), useMl));

        Map<RelationKey, Candidate> candidates = new LinkedHashMap<>();
        for (RelacionDetectada r : core) {
            merge(candidates, new Candidate(RelationKey.of(r), r.getConfianza(), origin(r.getSource()),
                null, r.getMappedBy(), r.isBidirectional()));
        }
        addForeignKeys(model, candidates);
        addInverseOfExisting(model, candidates);
        if (options.useMemory()) {
            applyExactMemory(model, candidates);
        }

        List<Candidate> selected = new ArrayList<>();
        for (Candidate c : candidates.values()) {
            RelationKey k = c.key;
            if (model.existingPairs.contains(pair(k.origen(), k.destino()))) {
                continue; // las relaciones existentes no se devuelven como sugerencia
            }
            if (c.confidence <= 0.0 || c.confidence < options.minConfidence()) {
                continue;
            }
            selected.add(c);
        }
        Set<String> pairs = new HashSet<>(model.existingPairs);
        for (Candidate c : selected) {
            pairs.add(pair(c.key.origen(), c.key.destino()));
        }

        selected.sort(Comparator.comparingDouble((Candidate c) -> c.confidence).reversed()
            .thenComparing(c -> c.key.origen())
            .thenComparing(c -> c.key.destino())
            .thenComparing(c -> c.key.tipo()));

        List<Suggestion> suggestions = new ArrayList<>();
        for (Candidate c : selected) {
            if (suggestions.size() >= options.maxResults()) {
                break;
            }
            RelationKey k = c.key;
            boolean bidirectional = c.bidirectional || pairs.contains(pair(k.destino(), k.origen()));
            String fieldName = c.fieldName != null ? c.fieldName : model.fieldNameFor(k, naming);
            suggestions.add(new Suggestion(k.origen(), k.destino(), k.tipo(), round(c.confidence),
                c.origin, fieldName, c.mappedBy, bidirectional));
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        return new SuggestResponse(suggestions, isMlAvailable(), durationMs);
    }

    // ------------------------------------------------------------------ reglas adicionales

    /** {@code userId: Long} en Post y existe la entidad User → Post ManyToOne User. */
    private void addForeignKeys(Model model, Map<RelationKey, Candidate> candidates) {
        for (EntityModel entity : model.entities.values()) {
            for (String target : entity.foreignKeyTargets.keySet()) {
                if (model.existingPairs.contains(pair(entity.name, target))) {
                    continue;
                }
                RelationKey key = new RelationKey(entity.name, target, "ManyToOne");
                merge(candidates, new Candidate(key, FOREIGN_KEY_CONFIDENCE, "SEMANTIC",
                    entity.foreignKeyTargets.get(target), null, false));
            }
        }
    }

    /** Lado inverso de cada relación existente que no tenga ya su contraparte. */
    private void addInverseOfExisting(Model model, Map<RelationKey, Candidate> candidates) {
        for (ExistingRelation rel : model.existingRelations) {
            if (model.existingPairs.contains(pair(rel.target, rel.source))) {
                continue;
            }
            String inverseType = switch (rel.type) {
                case "OneToMany" -> "ManyToOne";
                case "ManyToOne" -> "OneToMany";
                default -> rel.type; // OneToOne y ManyToMany son simétricos
            };
            EntityModel targetEntity = model.entities.get(rel.target);
            double confidence = INVERSE_CONFIDENCE;
            String origin = "STRUCTURAL";
            String fieldName = null;
            // El lado ManyToOne es el dueño de la clave ajena: si la entidad tiene una única clave
            // sin entidad conocida (authorId sin entidad Author), ese es su nombre natural
            if ("ManyToOne".equals(inverseType) && targetEntity.unresolvedForeignKeys.size() == 1) {
                fieldName = targetEntity.unresolvedForeignKeys.get(0);
                confidence += INVERSE_WITH_KEY_BONUS;
                origin = "COMBINED";
            }
            // mappedBy va en el lado que no es dueño: aquí, cuando la sugerencia es la colección
            // o el lado secundario de un OneToOne/ManyToMany cuyo dueño ya existe
            String mappedBy = "ManyToOne".equals(inverseType) ? null : rel.fieldName;
            RelationKey key = new RelationKey(rel.target, rel.source, inverseType);
            merge(candidates, new Candidate(key, confidence, origin, fieldName, mappedBy, true));
        }
    }

    /** Decisiones exactas de la memoria entre entidades de la petición. */
    private void applyExactMemory(Model model, Map<RelationKey, Candidate> candidates) {
        if (memoryService == null) {
            return;
        }
        Set<RelationKey> rejected = new HashSet<>();
        for (Map.Entry<String, String> e : memoryService.getMemoria().entrySet()) {
            RelationKey key = MemoryKeys.parse(e.getKey());
            if (key == null || !model.entities.containsKey(key.origen())
                    || !model.entities.containsKey(key.destino()) || !RELATION_TYPES.contains(key.tipo())) {
                continue;
            }
            if ("aceptada".equals(e.getValue())) {
                merge(candidates, new Candidate(key, MEMORY_ACCEPTED_CONFIDENCE, "MEMORY", null, null, false));
            } else if ("rechazada".equals(e.getValue())) {
                rejected.add(key);
            }
        }
        rejected.forEach(candidates::remove);
    }

    private static void merge(Map<RelationKey, Candidate> candidates, Candidate incoming) {
        Candidate current = candidates.get(incoming.key);
        if (current == null) {
            candidates.put(incoming.key, incoming);
            return;
        }
        String origin = current.origin.equals(incoming.origin) ? current.origin : "COMBINED";
        candidates.put(incoming.key, new Candidate(incoming.key,
            Math.max(current.confidence, incoming.confidence), origin,
            current.fieldName != null ? current.fieldName : incoming.fieldName,
            current.mappedBy != null ? current.mappedBy : incoming.mappedBy,
            current.bidirectional || incoming.bidirectional));
    }

    private static String origin(PredictionSource source) {
        return source != null ? source.name() : "STRUCTURAL";
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    static String pair(String a, String b) {
        return a + "\u0000" + b;
    }

    // ------------------------------------------------------------------ modelo de la petición

    private record Candidate(RelationKey key, double confidence, String origin, String fieldName,
                             String mappedBy, boolean bidirectional) {
    }

    record Options(double minConfidence, int maxResults, boolean useMemory, boolean useMl) {

        static Options from(OptionsDto dto) {
            if (dto == null) {
                return new Options(DEFAULT_MIN_CONFIDENCE, DEFAULT_MAX_RESULTS, true, true);
            }
            double min = dto.minConfidence() != null ? dto.minConfidence() : DEFAULT_MIN_CONFIDENCE;
            if (Double.isNaN(min) || min < 0.0 || min > 1.0) {
                throw ApiException.badRequest("options.minConfidence debe estar entre 0 y 1");
            }
            int max = dto.maxResults() != null ? dto.maxResults() : DEFAULT_MAX_RESULTS;
            if (max < 1 || max > MAX_RESULTS_LIMIT) {
                throw ApiException.badRequest("options.maxResults debe estar entre 1 y " + MAX_RESULTS_LIMIT);
            }
            return new Options(min, max,
                dto.useMemory() == null || dto.useMemory(),
                dto.useMl() == null || dto.useMl());
        }
    }

    private record ExistingRelation(String source, String type, String target, String fieldName) {
    }

    private static final class EntityModel {
        final String name;
        /** destino → nombre del campo, para claves ajenas que nombran una entidad (userId → User). */
        final Map<String, String> foreignKeyTargets = new LinkedHashMap<>();
        /** Prefijos de claves ajenas sin entidad (authorId → "author"). */
        final List<String> unresolvedForeignKeys = new ArrayList<>();

        EntityModel(String name) {
            this.name = name;
        }
    }

    private static final class Model {
        final Map<String, ClassInfo> classes = new LinkedHashMap<>();
        final Map<String, EntityModel> entities = new LinkedHashMap<>();
        final List<ExistingRelation> existingRelations = new ArrayList<>();
        final Set<String> existingPairs = new HashSet<>();

        static Model from(List<EntityDto> dtos, NamingPatternAnalyzer naming) {
            if (dtos == null || dtos.isEmpty()) {
                throw ApiException.badRequest("entities es obligatorio y no puede estar vacío");
            }
            if (dtos.size() > ApiConfig.MAX_ENTITIES) {
                throw ApiException.tooLarge("Como máximo " + ApiConfig.MAX_ENTITIES + " entidades (llegaron "
                    + dtos.size() + ")");
            }
            Model model = new Model();
            Map<String, String> lowerToName = new HashMap<>();
            for (EntityDto dto : dtos) {
                if (dto == null || dto.name() == null || !IDENTIFIER.matcher(dto.name()).matches()) {
                    throw ApiException.badRequest("Nombre de entidad no válido: "
                        + (dto == null ? null : dto.name()));
                }
                if (model.entities.containsKey(dto.name())) {
                    throw ApiException.badRequest("Entidad repetida: " + dto.name());
                }
                int fields = dto.fields() != null ? dto.fields().size() : 0;
                int relations = dto.relations() != null ? dto.relations().size() : 0;
                if (fields > ApiConfig.MAX_FIELDS_PER_ENTITY || relations > ApiConfig.MAX_FIELDS_PER_ENTITY) {
                    throw ApiException.tooLarge("Como máximo " + ApiConfig.MAX_FIELDS_PER_ENTITY
                        + " campos y " + ApiConfig.MAX_FIELDS_PER_ENTITY + " relaciones por entidad ("
                        + dto.name() + ")");
                }
                model.entities.put(dto.name(), new EntityModel(dto.name()));
                lowerToName.put(dto.name().toLowerCase(Locale.ROOT), dto.name());
            }

            for (EntityDto dto : dtos) {
                ClassInfo info = new ClassInfo(dto.name());
                EntityModel entity = model.entities.get(dto.name());
                Set<String> fieldNames = new HashSet<>();
                if (dto.fields() != null) {
                    for (FieldDto field : dto.fields()) {
                        validateField(dto.name(), field);
                        if (!fieldNames.add(field.name())) {
                            throw ApiException.badRequest("Campo repetido " + dto.name() + "." + field.name());
                        }
                        addField(info, FieldInfo.simple(field.name(), field.type()));
                        classifyForeignKey(entity, field, lowerToName);
                    }
                }
                if (dto.relations() != null) {
                    for (RelationDto rel : dto.relations()) {
                        validateRelation(dto.name(), rel, model.entities.keySet());
                        String fieldName = rel.fieldName() != null && !rel.fieldName().isBlank()
                            ? rel.fieldName()
                            : defaultFieldName(rel.targetEntity(), rel.type(), naming);
                        if (!fieldNames.add(fieldName)) {
                            throw ApiException.badRequest("Campo repetido " + dto.name() + "." + fieldName);
                        }
                        // La relación existente se convierte en campo para el análisis estructural
                        String type = isToMany(rel.type()) ? "List<" + rel.targetEntity() + ">" : rel.targetEntity();
                        addField(info, FieldInfo.withAnnotations(fieldName, type, List.of(rel.type())));
                        model.existingRelations.add(new ExistingRelation(dto.name(), rel.type(),
                            rel.targetEntity(), fieldName));
                        model.existingPairs.add(pair(dto.name(), rel.targetEntity()));
                    }
                }
                model.classes.put(dto.name(), info);
            }
            return model;
        }

        private static void addField(ClassInfo info, FieldInfo field) {
            info.addField(field);
            info.addFieldType(field.type());
            if (field.isCollection() && field.genericType() != null) {
                info.addCollectionType(field.genericType());
            }
        }

        private static void classifyForeignKey(EntityModel entity, FieldDto field, Map<String, String> lowerToName) {
            if (!KEY_TYPES.contains(field.type())) {
                return;
            }
            Matcher m = FOREIGN_KEY.matcher(field.name());
            if (!m.matches() || m.group(1).isEmpty()) {
                return;
            }
            String prefix = m.group(1);
            String target = lowerToName.get(prefix.toLowerCase(Locale.ROOT));
            String fieldName = Character.toLowerCase(prefix.charAt(0)) + prefix.substring(1);
            if (target != null && !target.equals(entity.name)) {
                entity.foreignKeyTargets.putIfAbsent(target, fieldName);
            } else if (target == null) {
                entity.unresolvedForeignKeys.add(fieldName);
            }
        }

        /** Nombre del campo de una sugerencia: el campo que ya referencia al destino, o uno por defecto. */
        String fieldNameFor(RelationKey key, NamingPatternAnalyzer naming) {
            ClassInfo info = classes.get(key.origen());
            if (info != null) {
                for (FieldInfo field : info.getFields()) {
                    if (key.destino().equals(field.genericType()) || TypeNames.references(field.type(), key.destino())) {
                        return field.name();
                    }
                }
            }
            return defaultFieldName(key.destino(), key.tipo(), naming);
        }

        private static String defaultFieldName(String target, String type, NamingPatternAnalyzer naming) {
            String base = Character.toLowerCase(target.charAt(0)) + target.substring(1);
            return isToMany(type) ? naming.pluralize(base) : base;
        }

        private static boolean isToMany(String type) {
            return "OneToMany".equals(type) || "ManyToMany".equals(type);
        }

        private static void validateField(String entity, FieldDto field) {
            if (field == null || field.name() == null || !IDENTIFIER.matcher(field.name()).matches()) {
                throw ApiException.badRequest("Nombre de campo no válido en " + entity + ": "
                    + (field == null ? null : field.name()));
            }
            if (field.type() == null || !TYPE_NAME.matcher(field.type()).matches()) {
                throw ApiException.badRequest("Tipo no válido en " + entity + "." + field.name() + ": " + field.type());
            }
        }

        private static void validateRelation(String entity, RelationDto rel, Set<String> entityNames) {
            if (rel == null || rel.type() == null || !RELATION_TYPES.contains(rel.type())) {
                throw ApiException.badRequest("Tipo de relación no válido en " + entity
                    + " (OneToOne | OneToMany | ManyToOne | ManyToMany): " + (rel == null ? null : rel.type()));
            }
            if (rel.targetEntity() == null || !entityNames.contains(rel.targetEntity())) {
                throw ApiException.badRequest("targetEntity desconocida en " + entity + ": " + rel.targetEntity());
            }
            if (rel.fieldName() != null && !rel.fieldName().isBlank()
                    && !IDENTIFIER.matcher(rel.fieldName()).matches()) {
                throw ApiException.badRequest("fieldName no válido en " + entity + ": " + rel.fieldName());
            }
        }
    }
}
