package org.example.model;

import java.util.Objects;

import org.example.ml.PredictionSource;

/**
 * Representa una relación detectada entre dos clases (con nivel de confianza).
 * equals/hashCode se basan en origen+destino+tipo (la confianza no forma parte de la clave).
 * toString() devuelve la clave usada por la memoria: "Origen -> Destino : Tipo"
 */
public class RelacionDetectada {

    private final String origen;
    private final String destino;
    private final String tipo;
    private double confianza; // 🔧 Ahora mutable
    private PredictionSource source; // Source of the prediction (ML, STRUCTURAL, SEMANTIC, COMBINED)
    
    // 🔄 Campos para relaciones bidireccionales
    private boolean bidirectional = false;
    private boolean ownerSide = true;
    private String mappedBy = null;
    private RelacionDetectada inverseRelation = null;
    
    // 📖 Campo para anotaciones existentes
    private boolean existingAnnotation = false;

    public RelacionDetectada(String origen, String destino, String tipo, double confianza) {
        this.origen = origen;
        this.destino = destino;
        this.tipo = tipo;
        this.confianza = confianza;
        this.source = null; // Default to null, will be set when combining scores
    }
    
    public RelacionDetectada(String origen, String destino, String tipo, double confianza, PredictionSource source) {
        this.origen = origen;
        this.destino = destino;
        this.tipo = tipo;
        this.confianza = confianza;
        this.source = source;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public String getTipo() {
        return tipo;
    }

    public double getConfianza() {
        return confianza;
    }

    /**
     * 🔧 Nuevo setter: permite ajustar la confianza dinámicamente.
     */
    public void setConfianza(double confianza) {
        this.confianza = confianza;
    }

    /**
     * Gets the prediction source (ML, STRUCTURAL, SEMANTIC, or COMBINED).
     * @return the prediction source, or null if not set
     */
    public PredictionSource getSource() {
        return source;
    }

    /**
     * Sets the prediction source.
     * @param source the prediction source
     */
    public void setSource(PredictionSource source) {
        this.source = source;
    }

    // 🔄 Getters y setters para bidireccionalidad
    
    public boolean isBidirectional() {
        return bidirectional;
    }

    public void setBidirectional(boolean bidirectional) {
        this.bidirectional = bidirectional;
    }

    public boolean isOwnerSide() {
        return ownerSide;
    }

    public void setOwnerSide(boolean ownerSide) {
        this.ownerSide = ownerSide;
    }

    public String getMappedBy() {
        return mappedBy;
    }

    public void setMappedBy(String mappedBy) {
        this.mappedBy = mappedBy;
    }

    public RelacionDetectada getInverseRelation() {
        return inverseRelation;
    }

    public void setInverseRelation(RelacionDetectada inverseRelation) {
        this.inverseRelation = inverseRelation;
    }

    // 📖 Getters y setters para anotaciones existentes
    
    public boolean isExistingAnnotation() {
        return existingAnnotation;
    }

    public void setExistingAnnotation(boolean existingAnnotation) {
        this.existingAnnotation = existingAnnotation;
    }

    /**
     * Clave textual que se guardará en memoria (sin confianza).
     */
    public String getClaveMemoria() {
        return String.format("%s -> %s : %s", origen, destino, tipo);
    }

    @Override
    public String toString() {
        // Mostrar confianza y fuente para el usuario
        if (source != null) {
            return String.format("%s -> %s : %s (confianza %.2f, fuente: %s)", 
                origen, destino, tipo, confianza, source.getDisplayName());
        }
        return String.format("%s -> %s : %s (confianza %.2f)", origen, destino, tipo, confianza);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelacionDetectada)) return false;
        RelacionDetectada that = (RelacionDetectada) o;
        return Objects.equals(origen, that.origen) &&
                Objects.equals(destino, that.destino) &&
                Objects.equals(tipo, that.tipo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origen, destino, tipo);
    }
}
