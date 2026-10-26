package org.example.ml;

import java.util.regex.Pattern;

/**
 * Utilidades sobre nombres de tipo tal y como aparecen en el código ({@code List<Order>},
 * {@code Map<String, User>}, {@code User[]}).
 */
public final class TypeNames {

    /** Separa un tipo en identificadores: todo lo que no sea letra, dígito, '_' o '$'. */
    private static final Pattern NON_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_$]+");

    private TypeNames() {
    }

    /**
     * Indica si el tipo menciona la clase como identificador completo.
     * {@code references("List<User>", "User")} es true; {@code references("UserRole", "User")} es false
     * (antes se usaba {@code contains} y daba un falso positivo).
     *
     * @param type tipo del campo
     * @param className nombre simple de la clase
     * @return true si algún identificador del tipo es exactamente {@code className}
     */
    public static boolean references(String type, String className) {
        if (type == null || className == null || className.isEmpty()) {
            return false;
        }
        if (type.equals(className)) {
            return true;
        }
        if (!type.contains(className)) {
            return false; // atajo: sin subcadena no puede haber identificador igual
        }
        for (String token : NON_IDENTIFIER.split(type)) {
            if (token.equals(className)) {
                return true;
            }
        }
        return false;
    }
}
