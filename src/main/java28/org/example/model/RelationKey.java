package org.example.model;

/**
 * Clave de una relación: origen, destino y tipo.
 * Versión Valhalla (value record, perfil {@code valhalla}).
 */
public value record RelationKey(String origen, String destino, String tipo) {

    public static RelationKey of(RelacionDetectada r) {
        return new RelationKey(r.getOrigen(), r.getDestino(), r.getTipo());
    }
}
