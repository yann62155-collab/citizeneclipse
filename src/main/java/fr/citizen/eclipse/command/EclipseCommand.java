package fr.citizen.eclipse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.citizen.eclipse.EclipseManager;
import fr.citizen.eclipse.Texts;
import fr.citizen.eclipse.config.EclipseConfig;
import fr.citizen.eclipse.schedule.Eclipse;
import fr.citizen.eclipse.schedule.EclipsePhase;
import fr.citizen.eclipse.schedule.EclipseSchedule;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * /eclipse                      → prochaine éclipse (tout le monde)
 * /eclipse prochaine | calendrier
 * Admin (niveau OP 2) :
 * /eclipse liste
 * /eclipse lancer [approcheMinutes] [dureeMinutes]
 * /eclipse planifier <date> <heure> [dureeMinutes]   ex. 2026-11-20 21h30
 * /eclipse deplacer <id> <date> <heure>
 * /eclipse annuler <id>
 * /eclipse arreter
 * /eclipse generer
 * /eclipse recharger
 * /eclipse statut
 */
public final class EclipseCommand {
    private EclipseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("eclipse")
                .executes(EclipseCommand::next)
                .then(Commands.literal("prochaine").executes(EclipseCommand::next))
                .then(Commands.literal("calendrier").executes(EclipseCommand::calendar))
                .then(Commands.literal("liste").requires(s -> s.hasPermission(2)).executes(EclipseCommand::list))
                .then(Commands.literal("statut").requires(s -> s.hasPermission(2)).executes(EclipseCommand::status))
                .then(Commands.literal("lancer").requires(s -> s.hasPermission(2))
                        .executes(c -> launch(c, 1, EclipseConfig.get().dureeTotaleMinutes))
                        .then(Commands.argument("approcheMinutes", IntegerArgumentType.integer(0, 600))
                                .executes(c -> launch(c, IntegerArgumentType.getInteger(c, "approcheMinutes"), EclipseConfig.get().dureeTotaleMinutes))
                                .then(Commands.argument("dureeMinutes", IntegerArgumentType.integer(1, 600))
                                        .executes(c -> launch(c, IntegerArgumentType.getInteger(c, "approcheMinutes"), IntegerArgumentType.getInteger(c, "dureeMinutes"))))))
                .then(Commands.literal("planifier").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("date", StringArgumentType.word())
                                .then(Commands.argument("heure", StringArgumentType.word())
                                        .executes(c -> plan(c, EclipseConfig.get().dureeTotaleMinutes))
                                        .then(Commands.argument("dureeMinutes", IntegerArgumentType.integer(1, 600))
                                                .executes(c -> plan(c, IntegerArgumentType.getInteger(c, "dureeMinutes")))))))
                .then(Commands.literal("deplacer").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("date", StringArgumentType.word())
                                        .then(Commands.argument("heure", StringArgumentType.word())
                                                .executes(EclipseCommand::move)))))
                .then(Commands.literal("annuler").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(EclipseCommand::cancel)))
                .then(Commands.literal("arreter").requires(s -> s.hasPermission(2)).executes(EclipseCommand::stop))
                .then(Commands.literal("generer").requires(s -> s.hasPermission(2)).executes(EclipseCommand::generate))
                .then(Commands.literal("moine").requires(s -> s.hasPermission(2)).executes(EclipseCommand::monk))
                .then(Commands.literal("vague").requires(s -> s.hasPermission(2)).executes(EclipseCommand::wave))
                .then(Commands.literal("recharger").requires(s -> s.hasPermission(2)).executes(EclipseCommand::reload)));
    }

    // ------------------------------------------------------------------ joueurs

    private static int next(CommandContext<CommandSourceStack> c) {
        long now = System.currentTimeMillis();
        EclipseSchedule s = EclipseSchedule.get();
        Eclipse cur = s.current(now);
        if (cur != null) {
            EclipsePhase p = cur.phase(now);
            String txt = switch (p) {
                case APPROACH -> "La lune approche du soleil ! Éclipse totale à " + Texts.hour(cur.totalityStart)
                        + " (dans " + Texts.duration(cur.totalityStart - now) + ").";
                case TOTALITY -> "L'éclipse est totale ! Fin à " + Texts.hour(cur.totalityEnd) + ".";
                default -> "La lune s'éloigne du soleil, l'éclipse se termine.";
            };
            c.getSource().sendSuccess(() -> Texts.msg(txt), false);
            return 1;
        }
        Eclipse n = s.next(now);
        if (n == null) {
            c.getSource().sendSuccess(() -> Texts.msg("Aucune éclipse n'est prévue pour le moment."), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Texts.msg("Prochaine éclipse : ")
                .append(Texts.gold(Texts.date(n.totalityStart)))
                .append(Component.literal(" (dans " + Texts.duration(n.totalityStart - now) + ") ").withStyle(ChatFormatting.GRAY))
                .append(Texts.link()), false);
        return 1;
    }

    private static int calendar(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Texts.msg("Calendrier des éclipses : ").append(Texts.link()), false);
        return 1;
    }

    // ------------------------------------------------------------------ admin

    private static int list(CommandContext<CommandSourceStack> c) {
        long now = System.currentTimeMillis();
        List<Eclipse> list = EclipseSchedule.get().upcoming(now);
        if (list.isEmpty()) {
            c.getSource().sendSuccess(() -> Texts.msg("Aucune éclipse planifiée."), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Texts.msg("Éclipses planifiées :"), false);
        for (Eclipse e : list) {
            EclipsePhase p = e.phase(now);
            MutableComponent line = Component.literal(" #" + e.id + " ").withStyle(ChatFormatting.DARK_RED)
                    .append(Texts.gold(Texts.date(e.totalityStart)))
                    .append(Component.literal(" — " + ((e.totalityEnd - e.totalityStart) / 60_000) + " min, "
                            + (e.source.equals("AUTO") ? "auto" : "admin" + (e.creePar.isEmpty() ? "" : " (" + e.creePar + ")"))
                            + (p != EclipsePhase.NONE ? " [EN COURS : " + p.key + "]" : "")).withStyle(ChatFormatting.GRAY));
            c.getSource().sendSuccess(() -> line, false);
        }
        return list.size();
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        long now = System.currentTimeMillis();
        Eclipse cur = EclipseSchedule.get().current(now);
        String phase = cur == null ? "aucune éclipse en cours" : "éclipse #" + cur.id + " en phase " + cur.phase(now).key;
        int mobs = EclipseManager.get().trackedMobCount();
        EclipseConfig cfg = EclipseConfig.get();
        c.getSource().sendSuccess(() -> Texts.msg("Statut : " + phase + ", " + mobs + " créature(s) d'éclipse, site web "
                + (cfg.web.active ? "port " + cfg.web.port : "désactivé") + "."), false);
        return 1;
    }

    private static int launch(CommandContext<CommandSourceStack> c, int approach, int duration) {
        long now = System.currentTimeMillis();
        EclipseSchedule s = EclipseSchedule.get();
        if (s.current(now) != null) {
            c.getSource().sendFailure(Component.literal("Une éclipse est déjà en cours (/eclipse arreter pour l'interrompre)."));
            return 0;
        }
        EclipseConfig cfg = EclipseConfig.get();
        long start = now + approach * 60_000L;
        long end = start + (duration + cfg.eloignementMinutes) * 60_000L;
        // Une éclipse planifiée qui chevauche est décalée automatiquement plus tard par la génération : on la retire.
        for (Eclipse e : s.upcoming(now)) {
            if (e.overlaps(start - approach * 60_000L, end, 0)) s.remove(e.id);
        }
        Eclipse e = s.add(start, approach, duration, cfg.eloignementMinutes, "ADMIN", c.getSource().getTextName());
        c.getSource().sendSuccess(() -> Texts.msg("Éclipse #" + e.id + " lancée : totale à " + Texts.hour(e.totalityStart)
                + " pendant " + duration + " min."), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    private static int plan(CommandContext<CommandSourceStack> c, int duration) {
        Long start = parse(c);
        if (start == null) return 0;
        long now = System.currentTimeMillis();
        EclipseConfig cfg = EclipseConfig.get();
        if (start - cfg.approcheMinutes * 60_000L <= now) {
            c.getSource().sendFailure(Component.literal("Cette date est trop proche ou passée (approche de " + cfg.approcheMinutes
                    + " min). Utilisez /eclipse lancer pour une éclipse immédiate."));
            return 0;
        }
        long s0 = start - cfg.approcheMinutes * 60_000L;
        long e0 = start + (duration + cfg.eloignementMinutes) * 60_000L;
        if (EclipseSchedule.get().overlapsAny(s0, e0, null)) {
            c.getSource().sendFailure(Component.literal("Une autre éclipse est déjà prévue à ce moment-là (/eclipse liste)."));
            return 0;
        }
        Eclipse e = EclipseSchedule.get().add(start, cfg.approcheMinutes, duration, cfg.eloignementMinutes, "ADMIN", c.getSource().getTextName());
        c.getSource().sendSuccess(() -> Texts.msg("Éclipse #" + e.id + " planifiée le " + Texts.date(e.totalityStart) + "."), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    private static int move(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        Long start = parse(c);
        if (start == null) return 0;
        EclipseSchedule s = EclipseSchedule.get();
        Eclipse e = s.byId(id);
        if (e == null) {
            c.getSource().sendFailure(Component.literal("Éclipse #" + id + " introuvable."));
            return 0;
        }
        long now = System.currentTimeMillis();
        if (e.phase(now) != EclipsePhase.NONE) {
            c.getSource().sendFailure(Component.literal("Impossible de déplacer une éclipse en cours."));
            return 0;
        }
        long shift = start - e.totalityStart;
        if (e.approachStart + shift <= now) {
            c.getSource().sendFailure(Component.literal("La nouvelle date est trop proche ou passée."));
            return 0;
        }
        if (s.overlapsAny(e.approachStart + shift, e.recedeEnd + shift, id)) {
            c.getSource().sendFailure(Component.literal("Une autre éclipse est déjà prévue à ce moment-là."));
            return 0;
        }
        s.move(id, start);
        c.getSource().sendSuccess(() -> Texts.msg("Éclipse #" + id + " déplacée au " + Texts.date(start) + "."), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> c) {
        int id = IntegerArgumentType.getInteger(c, "id");
        EclipseSchedule s = EclipseSchedule.get();
        Eclipse e = s.byId(id);
        if (e == null) {
            c.getSource().sendFailure(Component.literal("Éclipse #" + id + " introuvable."));
            return 0;
        }
        if (e.phase(System.currentTimeMillis()) != EclipsePhase.NONE) {
            c.getSource().sendFailure(Component.literal("Elle est en cours : utilisez /eclipse arreter."));
            return 0;
        }
        s.remove(id);
        int regen = s.ensureWeekly(EclipseConfig.get(), System.currentTimeMillis());
        c.getSource().sendSuccess(() -> Texts.msg("Éclipse #" + id + " annulée."
                + (regen > 0 ? " Minimum hebdomadaire : une nouvelle date a été tirée (/eclipse liste)." : "")), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c) {
        if (!EclipseSchedule.get().stopCurrent(System.currentTimeMillis(), 2)) {
            c.getSource().sendFailure(Component.literal("Aucune éclipse en cours."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Texts.msg("L'éclipse est interrompue, la lune s'éloigne."), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    private static int generate(CommandContext<CommandSourceStack> c) {
        int n = EclipseSchedule.get().ensureWeekly(EclipseConfig.get(), System.currentTimeMillis());
        c.getSource().sendSuccess(() -> Texts.msg(n == 0 ? "Le calendrier respecte déjà le minimum hebdomadaire."
                : n + " éclipse(s) ajoutée(s) au calendrier."), true);
        EclipseManager.get().syncAll();
        return 1;
    }

    /** Force une vague autour de soi (même en créatif) et explique ce qui bloque. */
    private static int wave(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = c.getSource().getPlayerOrException();
        EclipseConfig cfg = EclipseConfig.get();
        if (!cfg.dimensions.contains(player.level().dimension().location().toString())) {
            c.getSource().sendFailure(Component.literal("Les éclipses ne touchent pas cette dimension (" + player.level().dimension().location() + ")."));
            return 0;
        }
        var rep = EclipseManager.get().spawnWave(c.getSource().getServer(), cfg, player);
        String why = rep.peaceful > 0 ? " Difficulté PAISIBLE : aucun monstre possible."
                : rep.capReached > 0 ? " Limite atteinte (" + cfg.creatures.monstresMaxParJoueur + " par joueur / " + cfg.creatures.maxTotal + " au total)."
                : rep.noPosition > 0 ? " " + rep.noPosition + " sans emplacement valide (eau, blocs, chunks non chargés)." : "";
        c.getSource().sendSuccess(() -> Texts.msg(rep.spawned + " créature(s) d'éclipse apparue(s)." + why), false);
        return rep.spawned;
    }

    /** Fait apparaître le Moine ténébreux près de soi (une éclipse doit être en cours). */
    private static int monk(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = c.getSource().getPlayerOrException();
        var server = c.getSource().getServer();
        if (EclipseSchedule.get().current(System.currentTimeMillis()) == null) {
            c.getSource().sendFailure(Component.literal("Le moine n'apparaît que pendant une éclipse (/eclipse lancer)."));
            return 0;
        }
        if (EclipseManager.get().monkAlive(server)) {
            c.getSource().sendFailure(Component.literal("Le Moine ténébreux est déjà en vie : il n'y en a qu'un par éclipse."));
            return 0;
        }
        var monk = EclipseManager.get().spawnMonk(server, EclipseConfig.get(), player);
        if (monk == null) {
            c.getSource().sendFailure(Component.literal("Aucun emplacement valide trouvé autour de vous (24-40 blocs)."));
            return 0;
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        EclipseManager.get().reload(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Texts.msg("Configuration et calendrier rechargés (le port web s'applique au redémarrage)."), true);
        return 1;
    }

    // ------------------------------------------------------------------ dates

    /** Date : 2026-11-20 ou 20-11-2026 ; heure : 21h30, 21h, 2130. */
    private static Long parse(CommandContext<CommandSourceStack> c) {
        String ds = StringArgumentType.getString(c, "date");
        String hs = StringArgumentType.getString(c, "heure");
        LocalDate date;
        try {
            date = ds.matches("\\d{4}-\\d{1,2}-\\d{1,2}")
                    ? LocalDate.parse(ds, DateTimeFormatter.ofPattern("yyyy-M-d"))
                    : LocalDate.parse(ds, DateTimeFormatter.ofPattern("d-M-yyyy"));
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal("Date invalide : " + ds + " (ex. 2026-11-20 ou 20-11-2026)."));
            return null;
        }
        LocalTime time;
        try {
            String h = hs.toLowerCase().replace("h", ":");
            if (h.endsWith(":")) h += "00";
            if (!h.contains(":") && h.length() >= 3) h = h.substring(0, h.length() - 2) + ":" + h.substring(h.length() - 2);
            String[] p = h.split(":");
            time = LocalTime.of(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal("Heure invalide : " + hs + " (ex. 21h30)."));
            return null;
        }
        return date.atTime(time).atZone(EclipseConfig.get().zone()).toInstant().toEpochMilli();
    }
}
