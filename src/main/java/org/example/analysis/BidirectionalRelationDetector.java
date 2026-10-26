package org.example.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.model.RelacionDetectada;

/**
 * 🔄 BidirectionalRelationDetector
 * Detecta relaciones bidireccionales entre entidades.
 * 
 * Ejemplo: Si Cliente tiene List<Pedido> y Pedido tiene Cliente,
 * detecta que es una relación bidireccional OneToMany/ManyToOne.
 */
public class BidirectionalRelationDetector {

    /**
     * Analiza las relaciones y detecta cuáles son bidireccionales.
     * Marca el lado "owner" (el que tiene la FK) y el lado "inverse" (mappedBy).
     * 
     * @param relaciones lista de relaciones detectadas
     * @return lista con relaciones marcadas como bidireccionales
     */
    public List<RelacionDetectada> detectBidirectional(List<RelacionDetectada> relaciones) {
        // Mapa para buscar relaciones inversas: "A->B" -> relación
        Map<String, RelacionDetectada> relacionMap = new HashMap<>();
        for (RelacionDetectada r : relaciones) {
            String key = r.getOrigen() + "->" + r.getDestino();
            relacionMap.put(key, r);
        }

        List<RelacionDetectada> resultado = new ArrayList<>();
        
        for (RelacionDetectada r : relaciones) {
            // Buscar relación inversa
            String inverseKey = r.getDestino() + "->" + r.getOrigen();
            RelacionDetectada inverse = relacionMap.get(inverseKey);
            
            if (inverse != null && !r.equals(inverse)) {
                // Hay relación bidireccional
                BidirectionalInfo info = determineBidirectionalType(r, inverse);
                
                // Marcar la relación actual
                r.setBidirectional(true);
                r.setInverseRelation(inverse);
                r.setOwnerSide(info.isOwner);
                r.setMappedBy(info.mappedBy);
                
                // Ajustar tipo si es necesario
                if (info.correctedType != null) {
                    r = new RelacionDetectada(
                        r.getOrigen(), r.getDestino(), info.correctedType,
                        r.getConfianza(), r.getSource()
                    );
                    r.setBidirectional(true);
                    r.setOwnerSide(info.isOwner);
                    r.setMappedBy(info.mappedBy);
                }
            }
            
            resultado.add(r);
        }
        
        return resultado;
    }

    /**
     * Determina el tipo de relación bidireccional y quién es el owner.
     */
    private BidirectionalInfo determineBidirectionalType(RelacionDetectada r1, RelacionDetectada r2) {
        BidirectionalInfo info = new BidirectionalInfo();
        
        String tipo1 = r1.getTipo();
        String tipo2 = r2.getTipo();
        
        // OneToMany <-> ManyToOne: El ManyToOne es el owner (tiene la FK)
        if ("OneToMany".equals(tipo1) && "ManyToOne".equals(tipo2)) {
            info.isOwner = false; // OneToMany no es owner
            info.mappedBy = r1.getDestino().toLowerCase();
            info.correctedType = "OneToMany";
        } 
        else if ("ManyToOne".equals(tipo1) && "OneToMany".equals(tipo2)) {
            info.isOwner = true; // ManyToOne es owner
            info.mappedBy = null;
            info.correctedType = "ManyToOne";
        }
        // OneToOne <-> OneToOne: El primero alfabéticamente es owner
        else if ("OneToOne".equals(tipo1) && "OneToOne".equals(tipo2)) {
            info.isOwner = r1.getOrigen().compareTo(r1.getDestino()) < 0;
            info.mappedBy = info.isOwner ? null : r1.getDestino().toLowerCase();
            info.correctedType = "OneToOne";
        }
        // ManyToMany <-> ManyToMany: El primero alfabéticamente es owner
        else if ("ManyToMany".equals(tipo1) && "ManyToMany".equals(tipo2)) {
            info.isOwner = r1.getOrigen().compareTo(r1.getDestino()) < 0;
            info.mappedBy = info.isOwner ? null : r1.getDestino().toLowerCase() + "s";
            info.correctedType = "ManyToMany";
        }
        // Otros casos: mantener como está
        else {
            info.isOwner = true;
            info.mappedBy = null;
            info.correctedType = null;
        }
        
        return info;
    }

    /**
     * Genera sugerencia de código JPA para una relación bidireccional.
     */
    public String generateBidirectionalJPA(RelacionDetectada rel) {
        if (!rel.isBidirectional()) {
            return generateUnidirectionalJPA(rel);
        }

        StringBuilder sb = new StringBuilder();
        String origen = rel.getOrigen();
        String destino = rel.getDestino();
        String tipo = rel.getTipo();
        
        sb.append("// ").append(origen).append(" <-> ").append(destino);
        sb.append(" (Bidireccional)\n");
        
        if (rel.isOwnerSide()) {
            // Lado owner - tiene la FK
            sb.append("// En clase ").append(origen).append(" (OWNER - tiene FK):\n");
            sb.append(generateOwnerAnnotation(tipo, destino));
        } else {
            // Lado inverse - usa mappedBy
            sb.append("// En clase ").append(origen).append(" (INVERSE - mappedBy):\n");
            sb.append(generateInverseAnnotation(tipo, destino, rel.getMappedBy()));
        }
        
        return sb.toString();
    }

    private String generateOwnerAnnotation(String tipo, String destino) {
        return switch (tipo) {
            case "ManyToOne" -> String.format(
                "@ManyToOne(fetch = FetchType.LAZY)\n" +
                "@JoinColumn(name = \"%s_id\", nullable = false)\n" +
                "private %s %s;\n",
                destino.toLowerCase(), destino, destino.toLowerCase()
            );
            case "OneToOne" -> String.format(
                "@OneToOne(fetch = FetchType.LAZY)\n" +
                "@JoinColumn(name = \"%s_id\", unique = true)\n" +
                "private %s %s;\n",
                destino.toLowerCase(), destino, destino.toLowerCase()
            );
            case "ManyToMany" -> String.format(
                "@ManyToMany(fetch = FetchType.LAZY)\n" +
                "@JoinTable(\n" +
                "    name = \"%s_%s\",\n" +
                "    joinColumns = @JoinColumn(name = \"%s_id\"),\n" +
                "    inverseJoinColumns = @JoinColumn(name = \"%s_id\")\n" +
                ")\n" +
                "private Set<%s> %ss = new HashSet<>();\n",
                destino.toLowerCase(), destino.toLowerCase(),
                destino.toLowerCase(), destino.toLowerCase(),
                destino, destino.toLowerCase()
            );
            default -> "// Tipo no soportado: " + tipo + "\n";
        };
    }

    private String generateInverseAnnotation(String tipo, String destino, String mappedBy) {
        String mappedByField = mappedBy != null ? mappedBy : destino.toLowerCase();
        
        return switch (tipo) {
            case "OneToMany" -> String.format(
                "@OneToMany(mappedBy = \"%s\", cascade = CascadeType.ALL, orphanRemoval = true)\n" +
                "private List<%s> %ss = new ArrayList<>();\n",
                mappedByField, destino, destino.toLowerCase()
            );
            case "OneToOne" -> String.format(
                "@OneToOne(mappedBy = \"%s\")\n" +
                "private %s %s;\n",
                mappedByField, destino, destino.toLowerCase()
            );
            case "ManyToMany" -> String.format(
                "@ManyToMany(mappedBy = \"%s\")\n" +
                "private Set<%s> %ss = new HashSet<>();\n",
                mappedByField, destino, destino.toLowerCase()
            );
            default -> "// Tipo no soportado: " + tipo + "\n";
        };
    }

    private String generateUnidirectionalJPA(RelacionDetectada rel) {
        String destino = rel.getDestino();
        return switch (rel.getTipo()) {
            case "ManyToOne" -> String.format(
                "@ManyToOne(fetch = FetchType.LAZY)\n" +
                "@JoinColumn(name = \"%s_id\")\n" +
                "private %s %s;\n",
                destino.toLowerCase(), destino, destino.toLowerCase()
            );
            case "OneToMany" -> String.format(
                "@OneToMany(fetch = FetchType.LAZY)\n" +
                "private List<%s> %ss = new ArrayList<>();\n",
                destino, destino.toLowerCase()
            );
            case "OneToOne" -> String.format(
                "@OneToOne(fetch = FetchType.LAZY)\n" +
                "@JoinColumn(name = \"%s_id\")\n" +
                "private %s %s;\n",
                destino.toLowerCase(), destino, destino.toLowerCase()
            );
            case "ManyToMany" -> String.format(
                "@ManyToMany(fetch = FetchType.LAZY)\n" +
                "private Set<%s> %ss = new HashSet<>();\n",
                destino, destino.toLowerCase()
            );
            default -> "// Tipo desconocido: " + rel.getTipo() + "\n";
        };
    }

    private static class BidirectionalInfo {
        boolean isOwner;
        String mappedBy;
        String correctedType;
    }
}
