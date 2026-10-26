package org.example.testutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.example.ml.ClassInfo;
import org.example.ml.FieldInfo;

/**
 * Modelo de clases sintético y determinista, construido en memoria (sin disco).
 * Lo usan el test de equivalencia y los benchmarks JMH.
 *
 * <p>Los nombres combinan sustantivos de dominio con sufijos ("Customer", "CustomerItem",
 * "OrderLine"...), así que hay pares con nombres parecidos que activan el análisis semántico.
 * Cada clase tiene id, nombre y entre 1 y 4 referencias a otras clases: campo simple,
 * {@code List<>}, {@code Set<>}, array o clave ajena {@code xxxId}.</p>
 */
public final class SyntheticModel {

    private static final String[] NOUNS = {
        "Customer", "Order", "Product", "Invoice", "Payment", "Shipment", "Address", "Account",
        "User", "Role", "Permission", "Category", "Supplier", "Warehouse", "Employee", "Department",
        "Company", "Project", "Task", "Comment", "Post", "Tag", "Ticket", "Contract",
        "Vehicle", "Driver", "Route", "Booking", "Room", "Hotel", "Course", "Student",
        "Teacher", "Lesson", "Exam", "Grade", "Patient", "Doctor", "Appointment", "Prescription"
    };
    private static final String[] SUFFIXES = {
        "", "Item", "Line", "Detail", "History", "Type", "Status", "Group", "Profile", "Log", "Note", "Report"
    };
    private static final String[] TYPES = {"OneToOne", "OneToMany", "ManyToOne", "ManyToMany"};

    private SyntheticModel() {
    }

    /** @return nombre de la clase i-ésima (único) */
    public static String className(int i) {
        int combos = NOUNS.length * SUFFIXES.length;
        String base = NOUNS[i % NOUNS.length] + SUFFIXES[(i / NOUNS.length) % SUFFIXES.length];
        int round = i / combos;
        return round == 0 ? base : base + round;
    }

    /**
     * @param n número de clases
     * @param seed semilla
     * @return modelo con orden de inserción estable
     */
    public static Map<String, ClassInfo> classes(int n, long seed) {
        Random random = new Random(seed);
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            names.add(className(i));
        }
        Map<String, ClassInfo> model = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            ClassInfo info = new ClassInfo(name);
            info.addField(FieldInfo.withAnnotations("id", "Long", List.of("Id")));
            info.addField(FieldInfo.simple("name", "String"));
            info.addFieldType("Long");
            info.addFieldType("String");

            int refs = n > 1 ? 1 + random.nextInt(4) : 0;
            for (int r = 0; r < refs; r++) {
                int j = random.nextInt(n);
                if (j == i) {
                    j = (j + 1) % n;
                }
                String target = names.get(j);
                String var = decapitalize(target);
                boolean annotated = random.nextInt(5) == 0;
                FieldInfo field = switch (random.nextInt(5)) {
                    case 0 -> annotated
                        ? FieldInfo.withAnnotations(var, target, List.of("ManyToOne"))
                        : FieldInfo.simple(var, target);
                    case 1 -> annotated
                        ? FieldInfo.withAnnotations(var + "s", "List<" + target + ">", List.of("OneToMany"))
                        : FieldInfo.simple(var + "s", "List<" + target + ">");
                    case 2 -> FieldInfo.simple(var + "Set", "Set<" + target + ">");
                    case 3 -> FieldInfo.simple(var + "Array", target + "[]");
                    default -> FieldInfo.simple(var + "Id", "Long");
                };
                info.addField(field);
                info.addFieldType(field.type());
                if (field.isCollection() && field.genericType() != null) {
                    info.addCollectionType(field.genericType());
                }
            }
            if (random.nextInt(10) == 0) {
                info.setSuperClass("BaseEntity");
            }
            if (random.nextInt(5) == 0) {
                info.addInterface(random.nextBoolean() ? "Serializable" : "Auditable");
            }
            model.put(name, info);
        }
        return model;
    }

    /**
     * Memoria de decisiones con el formato real ("A -> B : Tipo" y algunas "A.campo -> B : Tipo").
     *
     * @param model modelo del que se toman los nombres (el 10 % de las claves usa nombres que no están)
     * @param decisions número de decisiones distintas
     * @param seed semilla
     * @return clave → "aceptada" | "rechazada", en orden de inserción
     */
    public static Map<String, String> memory(Map<String, ClassInfo> model, int decisions, long seed) {
        Random random = new Random(seed);
        List<String> names = new ArrayList<>(model.keySet());
        Map<String, String> memoria = new LinkedHashMap<>();
        int guard = 0;
        while (memoria.size() < decisions && guard++ < decisions * 20) {
            String origen = random.nextInt(10) == 0 ? "Legacy" + random.nextInt(50) : names.get(random.nextInt(names.size()));
            String destino = names.get(random.nextInt(names.size()));
            if (origen.equals(destino)) {
                continue;
            }
            String tipo = TYPES[random.nextInt(TYPES.length)];
            String key = random.nextInt(10) == 0
                ? origen + "." + decapitalize(destino) + " -> " + destino + " : " + tipo
                : origen + " -> " + destino + " : " + tipo;
            memoria.put(key, random.nextInt(10) < 6 ? "aceptada" : "rechazada");
        }
        return memoria;
    }

    private static String decapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
