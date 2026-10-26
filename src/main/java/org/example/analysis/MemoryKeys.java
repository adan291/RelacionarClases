package org.example.analysis;

import java.util.regex.Pattern;

import org.example.model.RelationKey;

/**
 * Formato de las claves del fichero de memoria: {@code "Origen -> Destino : Tipo"}.
 */
public final class MemoryKeys {

    private static final Pattern KEY_SEPARATOR = Pattern.compile(" -> | : ");

    private MemoryKeys() {
    }

    /** @return la clave de texto que se guarda en la memoria */
    public static String format(RelationKey key) {
        return key.origen() + " -> " + key.destino() + " : " + key.tipo();
    }

    /**
     * Separa por {@code " -> "} o {@code " : "} y necesita al menos tres partes (mismo criterio que
     * el análisis por memoria de siempre). Las claves con campo ({@code "A.campo -> B : Tipo"})
     * devuelven {@code "A.campo"} como origen.
     *
     * @return la clave, o null si el texto no tiene el formato esperado
     */
    public static RelationKey parse(String texto) {
        if (texto == null) {
            return null;
        }
        String[] partes = KEY_SEPARATOR.split(texto);
        if (partes.length < 3) {
            return null;
        }
        return new RelationKey(partes[0].trim(), partes[1].trim(), partes[2].trim());
    }
}
