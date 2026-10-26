package org.example.ml;

import java.util.List;

/**
 * Represents detailed information about a field in a Java class.
 * Used for enhanced feature extraction in ML model. Inmutable (la lista de anotaciones se copia).
 *
 * <p>Hay dos versiones de este fichero: {@code src/main/java25} (record) y
 * {@code src/main/java28} (value record, perfil {@code valhalla}). La lógica está en
 * {@link FieldInfoSupport}; si cambias la declaración, cambia las dos.</p>
 * 
 * @param name the field name
 * @param type the field type (e.g., "String", "List<Order>")
 * @param isCollection whether the field is a collection type
 * @param genericType the generic type parameter if collection (e.g., "Order" from List<Order>)
 * @param annotations list of annotation names on the field
 * @param mappedBy valor de {@code mappedBy} de la primera anotación JPA de relación, o null
 */
public record FieldInfo(
    String name,
    String type,
    boolean isCollection,
    String genericType,
    List<String> annotations,
    String mappedBy
) {
    public FieldInfo {
        annotations = FieldInfoSupport.immutable(annotations);
    }

    /** Constructor sin {@code mappedBy} (el habitual). */
    public FieldInfo(String name, String type, boolean isCollection, String genericType, List<String> annotations) {
        this(name, type, isCollection, genericType, annotations, null);
    }

    /** Creates a simple FieldInfo without annotations. */
    public static FieldInfo simple(String name, String type) {
        return FieldInfoSupport.create(name, type, List.of());
    }

    /** Creates a FieldInfo with annotations. */
    public static FieldInfo withAnnotations(String name, String type, List<String> annotations) {
        return FieldInfoSupport.create(name, type, annotations);
    }

    /** Checks if the field has a specific annotation. */
    public boolean hasAnnotation(String annotationName) {
        return FieldInfoSupport.hasAnnotation(annotations, annotationName);
    }

    /** Checks if this is an ID field (has @Id annotation). */
    public boolean isIdField() {
        return FieldInfoSupport.isIdField(annotations);
    }

    /** Checks if this field should be excluded (has @Transient). */
    public boolean isTransient() {
        return FieldInfoSupport.isTransient(annotations);
    }
}
