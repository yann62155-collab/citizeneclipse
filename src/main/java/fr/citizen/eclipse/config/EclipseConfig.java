package fr.citizen.eclipse.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.citizen.eclipse.CitizenEclipse;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Configuration JSON : config/citizeneclipse/config.json (rechargeable avec /eclipse recharger).
 */
public final class EclipseConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static EclipseConfig instance = new EclipseConfig();

    public static EclipseConfig get() {
        return instance;
    }

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve(CitizenEclipse.MODID);
    }

    public static final int CONFIG_VERSION = 3;
    /** Ne pas modifier : sert à mettre à jour les anciennes configs. */
    public int versionConfig = 0;

    // ---------------------------------------------------------------- général
    public String fuseauHoraire = "Europe/Paris";

    /** Horaires d'ouverture du serveur : les éclipses automatiques tombent dedans. */
    public Map<String, String> horairesOuverture = defaultHours();

    public int eclipsesAutoParSemaine = 1;
    public int semainesPlanifieesAvance = 2;
    /** Écart minimum entre deux éclipses (heures). */
    public int ecartMinimumHeures = 12;

    public int approcheMinutes = 45;
    public int dureeTotaleMinutes = 15;
    public int eloignementMinutes = 10;

    public List<String> dimensions = new ArrayList<>(List.of("minecraft:overworld"));

    /** Fait se lever le soleil (rapidement) au début de l'approche et le fige jusqu'à la fin. */
    public boolean forcerJour = true;
    /** Heure Minecraft où le soleil est figé (4000 ≈ soleil haut à l'est). */
    public int heureMinecraftEclipse = 4000;
    /** Durée de la transition vers le jour, en ticks (200 = 10 s). */
    public int transitionJourTicks = 200;

    public boolean barreBoss = true;

    // ---------------------------------------------------------------- web
    public Web web = new Web();

    public static final class Web {
        public boolean active = true;
        public String adresseEcoute = "0.0.0.0";
        public int port = 30015;
        /** Adresse publique affichée en jeu (commande /eclipse calendrier et message de connexion). */
        public String urlPublique = "http://mc312.boxtoplay.com:30015";
        public boolean seulementServeurDedie = true;
        public String nomServeur = "CITIZEN | Saison 2";
    }

    // ---------------------------------------------------------------- annonces
    public Annonces annonces = new Annonces();

    public static final class Annonces {
        /** Rappels dans le chat, en minutes avant le début de l'éclipse totale. Un seul message par palier. */
        public List<Integer> rappelsMinutes = new ArrayList<>(List.of(60, 30, 10, 1));
        /** À la connexion, une seule ligne si une éclipse est prévue dans ce délai (heures). */
        public int rappelConnexionHeures = 168;
        public boolean titreEcran = true;
    }

    // ---------------------------------------------------------------- créatures
    public Creatures creatures = new Creatures();

    public static final class Creatures {
        public boolean active = true;
        /** Pendant l'approche, les vagues commencent quand la lune couvre déjà ce % du trajet (0-100). 100 = seulement l'éclipse totale. */
        public int debutPendantApprochePourcent = 90;
        public int intervalleTicks = 60;
        /** Nombre max de créatures D'ÉCLIPSE autour de chaque joueur (les monstres normaux ne comptent pas). */
        public int monstresMaxParJoueur = 35;
        public int apparitionsParVague = 6;
        /** Chaque apparition peut être un groupe de 1 à N créatures de la même espèce. */
        public int tailleGroupeMax = 3;
        /** % de chance qu'un zombie / squelette / araignée d'éclipse soit MUTANT (géant). */
        public int chanceMutantPourcent = 15;
        /** Taille des mutants (1.0 = normale). */
        public double tailleMutant = 1.9;
        public int distanceMin = 16;
        public int distanceMax = 40;
        public int maxTotal = 250;
        /** Autorise aussi le spawn naturel des monstres en plein jour pendant l'éclipse (lumière ignorée). */
        public boolean spawnNaturelEnPleinJour = true;
        public boolean resistantsAuSoleil = true;
        public boolean disparaissentALaFin = true;
        public boolean ignorerCreatifSpectateur = true;
        /** "id=poids" */
        public List<String> types = new ArrayList<>(List.of(
                "citizeneclipse:shadow_zombie=35",
                "citizeneclipse:void_skeleton=22",
                "citizeneclipse:dusk_spider=18",
                "citizeneclipse:eclipse_wraith=8",
                "citizeneclipse:eclipse_colossus=4",
                "minecraft:zombie=8",
                "minecraft:creeper=7",
                "minecraft:witch=2"));
    }

    // ---------------------------------------------------------------- moine ténébreux
    public Moine moine = new Moine();

    public static final class Moine {
        public boolean active = true;
        /** Annonce de sa position dans le chat (secondes). */
        public int annonceSecondes = 60;
        public int distanceMin = 24;
        public int distanceMax = 40;
    }

    // ---------------------------------------------------------------- vision
    public Vision vision = new Vision();

    public static final class Vision {
        public boolean active = true;
        /** Demi-angle (degrés) du cône dans lequel on « regarde » le soleil. */
        public double angleRegardDegres = 12.0;
        public int nauseeSecondes = 10;
        public int ceciteSecondes = 3;
        /** Casques qui protègent (identifiants d'items). */
        public List<String> casquesProtecteurs = new ArrayList<>(List.of("citizeneclipse:eclipse_helmet"));
        /** Le casque perd 1 de durabilité toutes les N secondes d'observation (0 = jamais). */
        public int usureCasqueSecondes = 5;
    }

    // ================================================================ chargement

    private static Map<String, String> defaultHours() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("lundi", "19:00-00:30");
        m.put("mardi", "19:00-00:30");
        m.put("mercredi", "19:00-00:30");
        m.put("jeudi", "19:00-00:30");
        m.put("vendredi", "19:00-00:30");
        m.put("samedi", "18:00-03:00");
        m.put("dimanche", "18:00-03:00");
        return m;
    }

    public static synchronized void load() {
        Path file = dir().resolve("config.json");
        try {
            Files.createDirectories(dir());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    EclipseConfig loaded = GSON.fromJson(r, EclipseConfig.class);
                    if (loaded != null) instance = loaded;
                }
            } else {
                instance = new EclipseConfig();
                instance.versionConfig = CONFIG_VERSION;
            }
            instance.migrate();
            instance.sanitize();
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            CitizenEclipse.LOGGER.error("[Eclipse] Impossible de lire {} : {} (valeurs par défaut utilisées)", file, e.toString());
            instance = new EclipseConfig();
        }
    }

    /** Met à jour une config créée par une ancienne version du mod. */
    private void migrate() {
        if (versionConfig < 2 && creatures != null) {
            // v2 : vagues plus fortes, seules les créatures d'éclipse comptent dans la limite.
            Creatures d = new Creatures();
            creatures.monstresMaxParJoueur = Math.max(creatures.monstresMaxParJoueur, d.monstresMaxParJoueur);
            creatures.apparitionsParVague = Math.max(creatures.apparitionsParVague, d.apparitionsParVague);
            creatures.intervalleTicks = Math.min(creatures.intervalleTicks, d.intervalleTicks);
            creatures.maxTotal = Math.max(creatures.maxTotal, d.maxTotal);
            creatures.debutPendantApprochePourcent = Math.min(creatures.debutPendantApprochePourcent, d.debutPendantApprochePourcent);
        }
        if (versionConfig < 3 && creatures != null) {
            // v3 : espèces d'éclipse + mutants.
            Creatures d = new Creatures();
            boolean hasCustom = creatures.types != null && creatures.types.stream().anyMatch(t -> t.startsWith("citizeneclipse:"));
            if (!hasCustom) creatures.types = d.types;
            creatures.monstresMaxParJoueur = Math.max(creatures.monstresMaxParJoueur, d.monstresMaxParJoueur);
            creatures.apparitionsParVague = Math.max(creatures.apparitionsParVague, d.apparitionsParVague);
            creatures.maxTotal = Math.max(creatures.maxTotal, d.maxTotal);
            if (creatures.tailleGroupeMax <= 0) creatures.tailleGroupeMax = d.tailleGroupeMax;
            if (creatures.tailleMutant <= 0) creatures.tailleMutant = d.tailleMutant;
            if (creatures.chanceMutantPourcent <= 0) creatures.chanceMutantPourcent = d.chanceMutantPourcent;
        }
        versionConfig = CONFIG_VERSION;
    }

    private void sanitize() {
        if (horairesOuverture == null || horairesOuverture.isEmpty()) horairesOuverture = defaultHours();
        if (web == null) web = new Web();
        if (annonces == null) annonces = new Annonces();
        if (creatures == null) creatures = new Creatures();
        if (vision == null) vision = new Vision();
        if (moine == null) moine = new Moine();
        moine.annonceSecondes = clamp(moine.annonceSecondes, 10, 3600);
        moine.distanceMin = clamp(moine.distanceMin, 8, 96);
        moine.distanceMax = clamp(moine.distanceMax, moine.distanceMin + 1, 128);
        if (dimensions == null) dimensions = new ArrayList<>(List.of("minecraft:overworld"));
        if (annonces.rappelsMinutes == null) annonces.rappelsMinutes = new ArrayList<>();
        if (creatures.types == null || creatures.types.isEmpty()) creatures.types = new Creatures().types;
        if (vision.casquesProtecteurs == null) vision.casquesProtecteurs = new ArrayList<>();
        approcheMinutes = clamp(approcheMinutes, 0, 600);
        dureeTotaleMinutes = clamp(dureeTotaleMinutes, 1, 600);
        eloignementMinutes = clamp(eloignementMinutes, 0, 600);
        eclipsesAutoParSemaine = clamp(eclipsesAutoParSemaine, 0, 14);
        semainesPlanifieesAvance = clamp(semainesPlanifieesAvance, 1, 8);
        transitionJourTicks = clamp(transitionJourTicks, 1, 6000);
        creatures.intervalleTicks = clamp(creatures.intervalleTicks, 10, 6000);
        creatures.distanceMin = clamp(creatures.distanceMin, 4, 128);
        creatures.distanceMax = clamp(creatures.distanceMax, creatures.distanceMin + 1, 160);
        creatures.debutPendantApprochePourcent = clamp(creatures.debutPendantApprochePourcent, 0, 100);
        creatures.tailleGroupeMax = clamp(creatures.tailleGroupeMax, 1, 8);
        creatures.chanceMutantPourcent = clamp(creatures.chanceMutantPourcent, 0, 100);
        creatures.tailleMutant = Math.max(1.0, Math.min(4.0, creatures.tailleMutant));
        try {
            ZoneId.of(fuseauHoraire);
        } catch (Exception e) {
            fuseauHoraire = "Europe/Paris";
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(fuseauHoraire);
        } catch (Exception e) {
            return ZoneId.of("Europe/Paris");
        }
    }

    /** Créneau d'ouverture d'un jour (fin le lendemain si fin ≤ début). */
    public record Window(LocalTime start, LocalTime end) {
        public boolean crossesMidnight() {
            return !end.isAfter(start);
        }
    }

    public Window window(DayOfWeek day) {
        String raw = null;
        for (Map.Entry<String, String> e : horairesOuverture.entrySet()) {
            DayOfWeek d = parseDay(e.getKey());
            if (d == day) {
                raw = e.getValue();
                break;
            }
        }
        if (raw == null || raw.isBlank()) return null;
        try {
            String[] parts = raw.trim().replace('h', ':').split("-");
            return new Window(parseTime(parts[0]), parseTime(parts[1]));
        } catch (Exception e) {
            CitizenEclipse.LOGGER.warn("[Eclipse] Horaire invalide pour {} : '{}'", day, raw);
            return null;
        }
    }

    private static LocalTime parseTime(String s) {
        s = s.trim();
        if (s.endsWith(":")) s = s + "00";
        String[] hm = s.split(":");
        int h = Integer.parseInt(hm[0].trim());
        int m = hm.length > 1 && !hm[1].isBlank() ? Integer.parseInt(hm[1].trim()) : 0;
        if (h == 24) h = 0;
        return LocalTime.of(h, m);
    }

    public static DayOfWeek parseDay(String s) {
        String k = s.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "lundi", "monday" -> DayOfWeek.MONDAY;
            case "mardi", "tuesday" -> DayOfWeek.TUESDAY;
            case "mercredi", "wednesday" -> DayOfWeek.WEDNESDAY;
            case "jeudi", "thursday" -> DayOfWeek.THURSDAY;
            case "vendredi", "friday" -> DayOfWeek.FRIDAY;
            case "samedi", "saturday" -> DayOfWeek.SATURDAY;
            case "dimanche", "sunday" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }
}
