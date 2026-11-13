package fr.citizen.eclipse.schedule;

/**
 * Une éclipse planifiée. Toutes les dates sont en millisecondes epoch (UTC).
 *
 * approche : la lune glisse vers le soleil (visible dans le ciel)
 * totale   : la lune couvre le soleil, les créatures déferlent
 * éloignement : la lune repart, la lumière revient
 */
public class Eclipse {
    public int id;
    public long approachStart;
    public long totalityStart;
    public long totalityEnd;
    public long recedeEnd;
    /** AUTO ou ADMIN */
    public String source = "AUTO";
    public String creePar = "";
    /** Moine ténébreux de cette éclipse (un seul). */
    public boolean moineApparu = false;
    public boolean moineVaincu = false;
    public String moineUuid = null;

    public Eclipse() {}

    public Eclipse(int id, long totalityStart, int approachMin, int durationMin, int recedeMin, String source, String by) {
        this.id = id;
        this.totalityStart = totalityStart;
        this.approachStart = totalityStart - approachMin * 60_000L;
        this.totalityEnd = totalityStart + durationMin * 60_000L;
        this.recedeEnd = totalityEnd + recedeMin * 60_000L;
        this.source = source;
        this.creePar = by == null ? "" : by;
    }

    public EclipsePhase phase(long now) {
        if (now < approachStart || now >= recedeEnd) return EclipsePhase.NONE;
        if (now < totalityStart) return EclipsePhase.APPROACH;
        if (now < totalityEnd) return EclipsePhase.TOTALITY;
        return EclipsePhase.RECEDE;
    }

    /** Progression 0..1 de la phase en cours. */
    public float progress(long now) {
        return switch (phase(now)) {
            case APPROACH -> frac(now, approachStart, totalityStart);
            case TOTALITY -> frac(now, totalityStart, totalityEnd);
            case RECEDE -> frac(now, totalityEnd, recedeEnd);
            default -> 0f;
        };
    }

    private static float frac(long now, long a, long b) {
        if (b <= a) return 1f;
        return Math.max(0f, Math.min(1f, (now - a) / (float) (b - a)));
    }

    public void shiftTo(long newTotalityStart) {
        long d = newTotalityStart - totalityStart;
        approachStart += d;
        totalityStart += d;
        totalityEnd += d;
        recedeEnd += d;
    }

    public boolean overlaps(long start, long end, long marginMs) {
        return start < recedeEnd + marginMs && end + marginMs > approachStart;
    }
}
