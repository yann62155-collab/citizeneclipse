package fr.citizen.eclipse.schedule;

import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.config.EclipseConfig;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Calendrier des éclipses, sauvegardé dans config/citizeneclipse/eclipses.json.
 * Accès thread-safe (le serveur web le lit depuis un autre thread).
 */
public final class EclipseSchedule {
    private static final EclipseSchedule INSTANCE = new EclipseSchedule();
    private static final long KEEP_PAST_MS = 14L * 24 * 3600 * 1000;
    private static final long STEP_MS = 5 * 60_000L;

    public static EclipseSchedule get() {
        return INSTANCE;
    }

    /** Données persistées. */
    private static final class Data {
        int nextId = 1;
        List<Eclipse> eclipses = new ArrayList<>();
        /** Valeur de la règle doDaylightCycle à restaurer après une éclipse (null = rien à restaurer). */
        Boolean restoreDaylightCycle = null;
        /** Le Grand Moine a été vaincu : il ne revient plus, seuls ses disciples apparaissent. */
        boolean grandMoineVaincu = false;
    }

    private Data data = new Data();
    private final Random random = new Random();

    private static Path file() {
        return EclipseConfig.dir().resolve("eclipses.json");
    }

    public synchronized void load() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Data d = EclipseConfig.GSON.fromJson(r, Data.class);
                    if (d != null) {
                        if (d.eclipses == null) d.eclipses = new ArrayList<>();
                        data = d;
                    }
                }
            }
        } catch (Exception e) {
            CitizenEclipse.LOGGER.error("[Eclipse] Lecture de {} impossible : {}", f, e.toString());
        }
        sort();
    }

    public synchronized void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("eclipses.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                EclipseConfig.GSON.toJson(data, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    EclipseConfig.GSON.toJson(data, w);
                }
            } catch (Exception e2) {
                CitizenEclipse.LOGGER.error("[Eclipse] Sauvegarde de {} impossible : {}", f, e2.toString());
            }
        }
    }

    private void sort() {
        data.eclipses.sort(Comparator.comparingLong(e -> e.totalityStart));
    }

    // ------------------------------------------------------------------ lecture

    public synchronized List<Eclipse> snapshot() {
        List<Eclipse> copy = new ArrayList<>();
        for (Eclipse e : data.eclipses) {
            Eclipse c = new Eclipse();
            c.id = e.id;
            c.approachStart = e.approachStart;
            c.totalityStart = e.totalityStart;
            c.totalityEnd = e.totalityEnd;
            c.recedeEnd = e.recedeEnd;
            c.source = e.source;
            c.creePar = e.creePar;
            copy.add(c);
        }
        return copy;
    }

    /** Éclipse en cours (approche, totale ou éloignement). */
    public synchronized Eclipse current(long now) {
        for (Eclipse e : data.eclipses) {
            if (e.phase(now) != EclipsePhase.NONE) return e;
        }
        return null;
    }

    /** Prochaine éclipse dont l'approche n'a pas commencé. */
    public synchronized Eclipse next(long now) {
        for (Eclipse e : data.eclipses) {
            if (e.approachStart > now) return e;
        }
        return null;
    }

    /** Éclipse en cours, sinon la prochaine. */
    public synchronized Eclipse currentOrNext(long now) {
        Eclipse c = current(now);
        return c != null ? c : next(now);
    }

    public synchronized Eclipse byId(int id) {
        for (Eclipse e : data.eclipses) if (e.id == id) return e;
        return null;
    }

    public synchronized List<Eclipse> upcoming(long now) {
        List<Eclipse> out = new ArrayList<>();
        for (Eclipse e : data.eclipses) if (e.recedeEnd > now) out.add(e);
        return out;
    }

    public synchronized Boolean restoreDaylightCycle() {
        return data.restoreDaylightCycle;
    }

    public synchronized void setRestoreDaylightCycle(Boolean value) {
        data.restoreDaylightCycle = value;
        save();
    }

    public synchronized boolean grandMonkDefeated() {
        return data.grandMoineVaincu;
    }

    public synchronized void setGrandMonkDefeated(boolean value) {
        data.grandMoineVaincu = value;
        save();
    }

    // ------------------------------------------------------------------ écriture

    public synchronized boolean overlapsAny(long start, long end, Integer ignoreId) {
        for (Eclipse e : data.eclipses) {
            if (ignoreId != null && e.id == ignoreId) continue;
            if (e.overlaps(start, end, 60_000L)) return true;
        }
        return false;
    }

    public synchronized Eclipse add(long totalityStart, int approachMin, int durationMin, int recedeMin, String source, String by) {
        Eclipse e = new Eclipse(data.nextId++, totalityStart, approachMin, durationMin, recedeMin, source, by);
        data.eclipses.add(e);
        sort();
        save();
        return e;
    }

    public synchronized boolean remove(int id) {
        boolean removed = data.eclipses.removeIf(e -> e.id == id);
        if (removed) save();
        return removed;
    }

    public synchronized boolean move(int id, long newTotalityStart) {
        Eclipse e = byId(id);
        if (e == null) return false;
        e.shiftTo(newTotalityStart);
        e.source = "ADMIN";
        sort();
        save();
        return true;
    }

    /** Termine l'éclipse en cours : fin de la totalité maintenant, éloignement rapide. */
    public synchronized boolean stopCurrent(long now, int recedeMinutes) {
        Eclipse e = current(now);
        if (e == null) return false;
        if (e.phase(now) == EclipsePhase.APPROACH) {
            // Annulée avant même d'être totale : on retire simplement l'éclipse.
            data.eclipses.remove(e);
        } else {
            e.totalityEnd = Math.min(e.totalityEnd, now);
            e.recedeEnd = Math.min(e.recedeEnd, now + recedeMinutes * 60_000L);
        }
        save();
        return true;
    }

    public synchronized void prune(long now) {
        if (data.eclipses.removeIf(e -> e.recedeEnd < now - KEEP_PAST_MS)) save();
    }

    // ------------------------------------------------------------------ génération automatique

    /**
     * Garantit au moins N éclipses par semaine (semaine du lundi au dimanche, heure locale)
     * pour la semaine courante et les suivantes. Retourne le nombre d'éclipses créées.
     */
    public synchronized int ensureWeekly(EclipseConfig cfg, long now) {
        if (cfg.eclipsesAutoParSemaine <= 0) return 0;
        ZoneId zone = cfg.zone();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int created = 0;
        for (int w = 0; w < cfg.semainesPlanifieesAvance; w++) {
            LocalDate weekStart = monday.plusWeeks(w);
            long ws = weekStart.atStartOfDay(zone).toInstant().toEpochMilli();
            long we = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
            int count = 0;
            for (Eclipse e : data.eclipses) {
                if (e.totalityStart >= ws && e.totalityStart < we) count++;
            }
            int guard = 0;
            while (count < cfg.eclipsesAutoParSemaine && guard++ < 10) {
                Long start = pickSlot(cfg, weekStart, now);
                if (start == null) break;
                Eclipse e = new Eclipse(data.nextId++, start, cfg.approcheMinutes, cfg.dureeTotaleMinutes, cfg.eloignementMinutes, "AUTO", "");
                data.eclipses.add(e);
                count++;
                created++;
                CitizenEclipse.LOGGER.info("[Eclipse] Éclipse automatique #{} planifiée le {}", e.id,
                        Instant.ofEpochMilli(start).atZone(zone));
            }
        }
        if (created > 0) {
            sort();
            save();
        }
        return created;
    }

    /** Choisit un horaire aléatoire dans les heures d'ouverture de la semaine. */
    private Long pickSlot(EclipseConfig cfg, LocalDate weekStart, long now) {
        ZoneId zone = cfg.zone();
        long approach = cfg.approcheMinutes * 60_000L;
        long duration = cfg.dureeTotaleMinutes * 60_000L;
        long recede = cfg.eloignementMinutes * 60_000L;
        long gap = Math.max(0, cfg.ecartMinimumHeures) * 3_600_000L;

        record Range(long from, long to) {}
        List<Range> ranges = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            LocalDate day = weekStart.plusDays(d);
            EclipseConfig.Window win = cfg.window(day.getDayOfWeek());
            if (win == null) continue;
            ZonedDateTime open = day.atTime(win.start()).atZone(zone);
            ZonedDateTime close = (win.crossesMidnight() ? day.plusDays(1) : day).atTime(win.end()).atZone(zone);
            // Toute l'éclipse (approche comprise) doit tenir dans les heures d'ouverture.
            long earliest = open.toInstant().toEpochMilli() + approach;
            long latest = close.toInstant().toEpochMilli() - duration - recede;
            // Jamais dans le passé, et laisser le temps aux annonces.
            earliest = Math.max(earliest, now + approach + 15 * 60_000L);
            earliest = ceilStep(earliest);
            if (latest >= earliest) ranges.add(new Range(earliest, latest));
        }
        if (ranges.isEmpty()) return null;

        for (int attempt = 0; attempt < 40; attempt++) {
            Range r = ranges.get(random.nextInt(ranges.size()));
            long steps = (r.to() - r.from()) / STEP_MS;
            long start = r.from() + (steps > 0 ? (long) (random.nextDouble() * (steps + 1)) : 0) * STEP_MS;
            if (start > r.to()) start = r.to();
            long s = start - approach;
            long e = start + duration + recede;
            boolean clash = false;
            for (Eclipse ex : data.eclipses) {
                if (ex.overlaps(s, e, gap)) {
                    clash = true;
                    break;
                }
            }
            if (!clash) return start;
        }
        return null;
    }

    private static long ceilStep(long t) {
        long r = t % STEP_MS;
        return r == 0 ? t : t + (STEP_MS - r);
    }
}
