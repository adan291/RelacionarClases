package org.example.model;

/**
 * Clave de una relación: origen, destino y tipo. Sustituye a las claves de texto
 * ({@code "A -> B : Tipo"}) dentro del análisis; el fichero de memoria sigue usando texto.
 */
public record RelationKey(String origen, String destino, String tipo) {

    public static RelationKey of(RelacionDetectada r) {
        return new RelationKey(r.getOrigen(), r.getDestino(), r.getTipo());
    }
}
