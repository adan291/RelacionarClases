package org.example.model;

import java.util.Objects;

/**
 * Represents a point-in-time capture of a detected relation.
 * Contains all relation fields plus the decision state for snapshot persistence.
 */
public class RelationSnapshot {
    
    private String origen;
    private String destino;
    private String tipo;
    private double confianza;
    private String source;
    private boolean bidirectional;
    private boolean ownerSide;
    private String mappedBy;
    private DecisionState decisionState;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public RelationSnapshot() {
        this.decisionState = DecisionState.PENDING;
    }
    
    /**
     * Creates a RelationSnapshot from a RelacionDetectada with a specified decision state.
     * 
     * @param relacion the detected relation to snapshot
     * @param decisionState the decision state for this relation
     */
    public RelationSnapshot(RelacionDetectada relacion, DecisionState decisionState) {
        this.origen = relacion.getOrigen();
        this.destino = relacion.getDestino();
        this.tipo = relacion.getTipo();
        this.confianza = relacion.getConfianza();
        this.source = relacion.getSource() != null ? relacion.getSource().name() : null;
        this.bidirectional = relacion.isBidirectional();
        this.ownerSide = relacion.isOwnerSide();
        this.mappedBy = relacion.getMappedBy();
        this.decisionState = decisionState;
    }

    /**
     * Creates a RelationSnapshot from a RelacionDetectada with PENDING decision state.
     * 
     * @param relacion the detected relation to snapshot
     */
    public RelationSnapshot(RelacionDetectada relacion) {
        this(relacion, DecisionState.PENDING);
    }
    
    /**
     * Generates a unique key for this relation based on origen, destino, and tipo.
     * 
     * @return the unique key string
     */
    public String getKey() {
        return String.format("%s -> %s : %s", origen, destino, tipo);
    }
    
    // Getters and Setters
    
    public String getOrigen() {
        return origen;
    }
    
    public void setOrigen(String origen) {
        this.origen = origen;
    }
    
    public String getDestino() {
        return destino;
    }
    
    public void setDestino(String destino) {
        this.destino = destino;
    }
    
    public String getTipo() {
        return tipo;
    }
    
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
    
    public double getConfianza() {
        return confianza;
    }
    
    public void setConfianza(double confianza) {
        this.confianza = confianza;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
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
    
    public DecisionState getDecisionState() {
        return decisionState;
    }
    
    public void setDecisionState(DecisionState decisionState) {
        this.decisionState = decisionState;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationSnapshot)) return false;
        RelationSnapshot that = (RelationSnapshot) o;
        return Objects.equals(origen, that.origen) &&
               Objects.equals(destino, that.destino) &&
               Objects.equals(tipo, that.tipo);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(origen, destino, tipo);
    }
    
    @Override
    public String toString() {
        return String.format("RelationSnapshot{%s -> %s : %s, confianza=%.2f, decision=%s}",
                origen, destino, tipo, confianza, decisionState);
    }
}
