package fr.citizen.eclipse.network;

/**
 * État de l'éclipse côté joueur (simples nombres, aucune classe client).
 * Le rendu du ciel lit ces valeurs à chaque image.
 */
public final class ClientEclipseState {
    private ClientEclipseState() {}

    private static volatile long offset;          // heure serveur - heure locale
    private static volatile long approachStart;
    private static volatile long totalityStart;
    private static volatile long totalityEnd;
    private static volatile long recedeEnd;

    public static void update(EclipseStatePayload p) {
        offset = p.serverNow() - System.currentTimeMillis();
        approachStart = p.approachStart();
        totalityStart = p.totalityStart();
        totalityEnd = p.totalityEnd();
        recedeEnd = p.recedeEnd();
    }

    public static void clear() {
        approachStart = totalityStart = totalityEnd = recedeEnd = 0;
    }

    public static long now() {
        return System.currentTimeMillis() + offset;
    }

    public static boolean active() {
        long n = now();
        return approachStart > 0 && n >= approachStart && n < recedeEnd;
    }

    /**
     * Position de la lune par rapport au soleil : 1 = loin (début de l'approche),
     * 0 = centrée (totale), -1 = repartie de l'autre côté (fin).
     */
    public static float separation() {
        long n = now();
        if (approachStart <= 0 || n < approachStart || n >= recedeEnd) return 1f;
        if (n < totalityStart) return 1f - frac(n, approachStart, totalityStart);
        if (n < totalityEnd) return 0f;
        return -frac(n, totalityEnd, recedeEnd);
    }

    /** Assombrissement du ciel 0..1 (maximal pendant la totale). */
    public static float darkness() {
        if (!active()) return 0f;
        float s = Math.abs(separation());
        // Le ciel commence à s'assombrir quand la lune mord sur le soleil (dernier quart du trajet).
        float cover = 1f - Math.min(1f, s / 0.25f);
        return cover * cover * (3f - 2f * cover);
    }

    private static float frac(long n, long a, long b) {
        if (b <= a) return 1f;
        return Math.max(0f, Math.min(1f, (n - a) / (float) (b - a)));
    }
}
