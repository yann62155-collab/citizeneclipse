package fr.citizen.eclipse.schedule;

public enum EclipsePhase {
    NONE("aucune"),
    APPROACH("approche"),
    TOTALITY("totale"),
    RECEDE("eloignement");

    public final String key;

    EclipsePhase(String key) {
        this.key = key;
    }
}
