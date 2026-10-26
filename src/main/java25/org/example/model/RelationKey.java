package org.example.model;

/**
 * Clave de una relación: origen, destino y tipo. Sustituye a las claves de texto
 * ({@code "A -> B : Tipo"}) dentro del análisis y del índice de memoria; el fichero de memoria
 * sigue usando texto ({@code org.example.analysis.MemoryKeys}).
 * Versión Java 25 (record); la del perfil valhalla está en {@code src/main/java28}.
 */
public record RelationKey(String origen, String destino, String tipo) {

    public static RelationKey of(RelacionDetectada r) {
        return new RelationKey(r.getOrigen(), r.getDestino(), r.getTipo());
    }
}
