package org.example.ml;

/**
 * Mejor tipo de relación para un par de clases: índice del tipo y confianza.
 * Versión Valhalla (value record, perfil {@code valhalla}).
 */
public value record PairScore(int typeIndex, double confidence) {
}
