package fr.citizen.eclipse;

import fr.citizen.eclipse.config.EclipseConfig;
import fr.citizen.eclipse.entity.DarkMonk;
import fr.citizen.eclipse.entity.DuskSpider;
import fr.citizen.eclipse.entity.EclipseColossus;
import fr.citizen.eclipse.entity.EclipseCreature;
import fr.citizen.eclipse.entity.ModEntities;
import fr.citizen.eclipse.entity.ShadowZombie;
import fr.citizen.eclipse.entity.VoidSkeleton;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import fr.citizen.eclipse.network.EclipseStatePayload;
import fr.citizen.eclipse.schedule.Eclipse;
import fr.citizen.eclipse.schedule.EclipsePhase;
import fr.citizen.eclipse.schedule.EclipseSchedule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cœur du mod côté serveur : phases, annonces, barre de boss, jour forcé,
 * vagues de créatures et troubles de la vision.
 */
public final class EclipseManager {
    public static final String MOB_TAG = "citizeneclipse.mob";
    private static final EclipseManager INSTANCE = new EclipseManager();

    public static EclipseManager get() {
        return INSTANCE;
    }

    private int lastId = -1;
    private EclipsePhase lastPhase = EclipsePhase.NONE;
    private long lastNotifyCheck = 0;
    private long tick = 0;

    private ServerBossEvent bossBar;

    private final Set<UUID> eclipseMobs = new HashSet<>();
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    private final Map<UUID, Integer> observeTicks = new HashMap<>();

    /** Une éclipse est en cours (approche, totale ou éloignement). Lu par les créatures d'éclipse. */
    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    /** Mis à jour chaque tick : les créatures déferlent en ce moment. */
    private volatile boolean mobPhaseActive = false;

    private long daylightRemaining = 0;
    private long daylightStep = 0;

    private record Weighted(EntityType<?> type, int weight) {}
    private List<Weighted> mobTypes = new ArrayList<>();
    private int totalWeight = 0;

    // ================================================================== cycle de vie

    public void onServerStarted(MinecraftServer server) {
        EclipseConfig.load();
        reloadMobTypes();
        EclipseSchedule s = EclipseSchedule.get();
        s.load();
        long now = System.currentTimeMillis();
        s.prune(now);
        s.ensureWeekly(EclipseConfig.get(), now);

        lastId = -1;
        lastPhase = EclipsePhase.NONE;
        lastNotifyCheck = now;
        tick = 0;
        eclipseMobs.clear();
        bossBar = new ServerBossEvent(Component.literal("Éclipse"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        bossBar.setVisible(false);
        bossBar.setDarkenScreen(false);

        running = s.current(now) != null;
        // Le serveur s'est arrêté pendant une éclipse : on remet le cycle jour/nuit.
        if (s.current(now) == null) restoreDaylight(server);
    }

    public void onServerStopping(MinecraftServer server) {
        if (bossBar != null) bossBar.removeAllPlayers();
        EclipseSchedule.get().save();
    }

    public void reload(MinecraftServer server) {
        EclipseConfig.load();
        reloadMobTypes();
        EclipseSchedule.get().load();
        EclipseSchedule.get().ensureWeekly(EclipseConfig.get(), System.currentTimeMillis());
        syncAll();
    }

    private void reloadMobTypes() {
        List<Weighted> list = new ArrayList<>();
        int total = 0;
        for (String raw : EclipseConfig.get().creatures.types) {
            try {
                String[] p = raw.split("=");
                ResourceLocation id = ResourceLocation.parse(p[0].trim());
                int w = p.length > 1 ? Integer.parseInt(p[1].trim()) : 10;
                if (w <= 0) continue;
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
                if (type.isEmpty()) {
                    CitizenEclipse.LOGGER.warn("[Eclipse] Créature inconnue dans la config : {}", raw);
                    continue;
                }
                list.add(new Weighted(type.get(), w));
                total += w;
            } catch (Exception e) {
                CitizenEclipse.LOGGER.warn("[Eclipse] Entrée de créature invalide : {}", raw);
            }
        }
        mobTypes = list;
        totalWeight = total;
    }

    // ================================================================== tick

    public void onServerTick(MinecraftServer server) {
        tick++;
        EclipseConfig cfg = EclipseConfig.get();
        EclipseSchedule schedule = EclipseSchedule.get();
        long now = System.currentTimeMillis();

        Eclipse cur = schedule.current(now);
        EclipsePhase phase = cur == null ? EclipsePhase.NONE : cur.phase(now);
        int curId = cur == null ? -1 : cur.id;
        running = cur != null;

        if (curId != lastId || phase != lastPhase) {
            onPhaseChange(server, cur, lastPhase, phase);
            lastId = curId;
            lastPhase = phase;
            syncAll();
        }

        // Transition rapide vers le jour.
        if (daylightRemaining > 0) {
            ServerLevel ow = server.overworld();
            long step = Math.min(daylightStep, daylightRemaining);
            ow.setDayTime(ow.getDayTime() + step);
            daylightRemaining -= step;
        }

        if (tick % 20 == 0) {
            notifications(server, schedule, now);
            updateBossBar(server, cur, phase, now);
        }

        if (cur != null && cfg.vision.active && tick % 10 == 0) {
            vision(server, cfg);
        }

        mobPhaseActive = cur != null && mobsAllowed(cur, phase, now, cfg);
        if (mobPhaseActive && cfg.creatures.active && tick % cfg.creatures.intervalleTicks == 0) {
            spawnWave(server, cfg);
        }

        if (tick % 20 == 0) monkTick(server, cur, cfg);

        if (tick % 600 == 0) syncAll();                          // 30 s : corrige les dérives d'horloge
        if (tick % 72_000 == 0) {                                 // 1 h
            schedule.prune(now);
            if (schedule.ensureWeekly(cfg, now) > 0) syncAll();
        }
    }

    private boolean mobsAllowed(Eclipse e, EclipsePhase phase, long now, EclipseConfig cfg) {
        if (phase == EclipsePhase.TOTALITY) return true;
        if (phase == EclipsePhase.APPROACH && cfg.creatures.debutPendantApprochePourcent < 100) {
            return e.progress(now) * 100f >= cfg.creatures.debutPendantApprochePourcent;
        }
        return false;
    }

    private void onPhaseChange(MinecraftServer server, Eclipse e, EclipsePhase from, EclipsePhase to) {
        EclipseConfig cfg = EclipseConfig.get();
        if (from == EclipsePhase.NONE && to != EclipsePhase.NONE) {
            if (cfg.forcerJour) startDaylight(server, cfg);
            if (to == EclipsePhase.APPROACH) {
                broadcast(server, Texts.msg("La lune commence à glisser vers le soleil… L'éclipse sera totale à ")
                        .append(Texts.gold(Texts.hour(e.totalityStart)))
                        .append(Component.literal(". Levez les yeux, mais protégez-les !").withStyle(ChatFormatting.YELLOW)));
            }
        }
        if (to == EclipsePhase.TOTALITY && from != EclipsePhase.TOTALITY) {
            broadcast(server, Texts.msg("L'ÉCLIPSE EST TOTALE ! Les créatures des ténèbres déferlent. Ne fixez pas le soleil sans casque d'éclipse.")
                    .withStyle(ChatFormatting.RED));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (cfg.annonces.titreEcran) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Éclipse totale").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Les ténèbres s'abattent sur le royaume").withStyle(ChatFormatting.GOLD)));
                }
                p.level().playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.AMBIENT, 0.5F, 0.6F);
            }
        }
        if (to == EclipsePhase.RECEDE && from == EclipsePhase.TOTALITY) {
            broadcast(server, Texts.msg("La lune s'éloigne du soleil… la lumière revient peu à peu."));
        }
        if (to == EclipsePhase.NONE && from != EclipsePhase.NONE) {
            broadcast(server, Texts.msg("L'éclipse est terminée. Le soleil brille à nouveau sur Citizen."));
            if (cfg.creatures.disparaissentALaFin) despawnMobs(server);
            restoreDaylight(server);
            observeTicks.clear();
            EclipseSchedule.get().ensureWeekly(cfg, System.currentTimeMillis());
        }
    }

    // ================================================================== jour forcé

    private void startDaylight(MinecraftServer server, EclipseConfig cfg) {
        ServerLevel ow = server.overworld();
        GameRules.BooleanValue rule = ow.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        EclipseSchedule s = EclipseSchedule.get();
        if (s.restoreDaylightCycle() == null) s.setRestoreDaylightCycle(rule.get());
        rule.set(false, server);

        long cur = Math.floorMod(ow.getDayTime(), 24000L);
        long target = Math.floorMod(cfg.heureMinecraftEclipse, 24000);
        long delta;
        if (cur >= 1000 && cur <= 9000) {
            delta = 0; // le soleil est déjà bien haut
        } else {
            delta = Math.floorMod(target - cur, 24000L);
        }
        daylightRemaining = delta;
        daylightStep = Math.max(1, (long) Math.ceil(delta / (double) cfg.transitionJourTicks));
    }

    private void restoreDaylight(MinecraftServer server) {
        daylightRemaining = 0;
        EclipseSchedule s = EclipseSchedule.get();
        Boolean prev = s.restoreDaylightCycle();
        if (prev != null) {
            server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(prev, server);
            s.setRestoreDaylightCycle(null);
        }
    }

    // ================================================================== annonces

    private void notifications(MinecraftServer server, EclipseSchedule schedule, long now) {
        long from = lastNotifyCheck;
        lastNotifyCheck = now;
        if (now <= from) return;
        List<Integer> marks = EclipseConfig.get().annonces.rappelsMinutes;
        for (Eclipse e : schedule.upcoming(now)) {
            if (e.totalityStart <= from) continue;
            for (int m : marks) {
                if (m <= 0) continue;
                long t = e.totalityStart - m * 60_000L;
                if (from < t && t <= now) {
                    boolean approaching = now >= e.approachStart;
                    var c = Texts.msg("Éclipse totale dans ")
                            .append(Texts.gold(m + (m > 1 ? " minutes" : " minute")))
                            .append(Component.literal(" (à " + Texts.hour(e.totalityStart) + "). ").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(approaching
                                    ? "Regardez le ciel : la lune s'approche du soleil !"
                                    : "Préparez votre casque d'éclipse.").withStyle(ChatFormatting.GRAY));
                    broadcast(server, c);
                    break; // un seul message par passage
                }
            }
        }
    }

    public void onLogin(ServerPlayer player) {
        long now = System.currentTimeMillis();
        EclipseSchedule s = EclipseSchedule.get();
        PacketDistributor.sendToPlayer(player, EclipseStatePayload.of(s.currentOrNext(now), now));
        Eclipse cur = s.current(now);
        if (cur != null) {
            player.sendSystemMessage(Texts.msg("Une éclipse est en cours ! Totale à " + Texts.hour(cur.totalityStart) + ". Protégez vos yeux."));
            return;
        }
        Eclipse next = s.next(now);
        if (next != null && next.totalityStart - now <= EclipseConfig.get().annonces.rappelConnexionHeures * 3_600_000L) {
            player.sendSystemMessage(Texts.msg("Prochaine éclipse : ")
                    .append(Texts.gold(Texts.date(next.totalityStart)))
                    .append(Component.literal(" (dans " + Texts.duration(next.totalityStart - now) + ") ").withStyle(ChatFormatting.GRAY))
                    .append(Texts.link()));
        }
    }

    public void onLogout(ServerPlayer player) {
        if (bossBar != null) bossBar.removePlayer(player);
        lastWarn.remove(player.getUUID());
        observeTicks.remove(player.getUUID());
    }

    private void updateBossBar(MinecraftServer server, Eclipse e, EclipsePhase phase, long now) {
        if (bossBar == null) return;
        if (e == null || phase == EclipsePhase.NONE || !EclipseConfig.get().barreBoss) {
            if (bossBar.isVisible()) {
                bossBar.setVisible(false);
                bossBar.removeAllPlayers();
            }
            return;
        }
        float p = e.progress(now);
        switch (phase) {
            case APPROACH -> {
                bossBar.setName(Component.literal("☾ La lune approche du soleil — totale à " + Texts.hour(e.totalityStart)).withStyle(ChatFormatting.GOLD));
                bossBar.setColor(BossEvent.BossBarColor.YELLOW);
                bossBar.setProgress(Mth.clamp(p, 0f, 1f));
            }
            case TOTALITY -> {
                bossBar.setName(Component.literal("☀ ÉCLIPSE TOTALE — fin à " + Texts.hour(e.totalityEnd)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                bossBar.setColor(BossEvent.BossBarColor.RED);
                bossBar.setProgress(Mth.clamp(1f - p, 0f, 1f));
            }
            case RECEDE -> {
                bossBar.setName(Component.literal("La lune s'éloigne…").withStyle(ChatFormatting.LIGHT_PURPLE));
                bossBar.setColor(BossEvent.BossBarColor.PURPLE);
                bossBar.setProgress(Mth.clamp(1f - p, 0f, 1f));
            }
            default -> {}
        }
        bossBar.setVisible(true);
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            if (!bossBar.getPlayers().contains(pl)) bossBar.addPlayer(pl);
        }
    }

    private static void broadcast(MinecraftServer server, Component c) {
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

    public void syncAll() {
        long now = System.currentTimeMillis();
        PacketDistributor.sendToAllPlayers(EclipseStatePayload.of(EclipseSchedule.get().currentOrNext(now), now));
    }

    // ================================================================== dimensions

    private static List<ServerLevel> eclipseLevels(MinecraftServer server) {
        List<String> dims = EclipseConfig.get().dimensions;
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (dims.contains(l.dimension().location().toString())) out.add(l);
        }
        return out;
    }

    // ================================================================== vision

    private void vision(MinecraftServer server, EclipseConfig cfg) {
        double cosCone = Math.cos(Math.toRadians(cfg.vision.angleRegardDegres));
        long now = System.currentTimeMillis();
        for (ServerLevel level : eclipseLevels(server)) {
            if (!level.dimensionType().hasSkyLight()) continue;
            float timeOfDay = level.getTimeOfDay(1.0F);
            double ang = timeOfDay * Math.PI * 2.0;
            Vec3 sun = new Vec3(-Math.sin(ang), Math.cos(ang), 0.0);
            if (sun.y < 0.05) continue; // soleil sous l'horizon

            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isCreative()) continue;
                Vec3 look = player.getViewVector(1.0F);
                if (look.dot(sun) < cosCone) continue;
                if (!level.canSeeSky(BlockPos.containing(player.getEyePosition()))) continue;

                ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
                String headId = head.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(head.getItem()).toString();
                if (!head.isEmpty() && cfg.vision.casquesProtecteurs.contains(headId)) {
                    if (cfg.vision.usureCasqueSecondes > 0 && head.isDamageableItem()) {
                        int t = observeTicks.merge(player.getUUID(), 10, Integer::sum);
                        if (t >= cfg.vision.usureCasqueSecondes * 20) {
                            observeTicks.put(player.getUUID(), 0);
                            head.hurtAndBreak(1, player, EquipmentSlot.HEAD);
                        }
                    }
                    continue;
                }

                MobEffectInstance nausea = player.getEffect(MobEffects.CONFUSION);
                if (nausea == null || nausea.getDuration() < 80) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, cfg.vision.nauseeSecondes * 20, 0, false, false, true));
                }
                if (cfg.vision.ceciteSecondes > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, cfg.vision.ceciteSecondes * 20, 0, false, false, true));
                }
                Long last = lastWarn.get(player.getUUID());
                if (last == null || now - last > 5000) {
                    lastWarn.put(player.getUUID(), now);
                    player.displayClientMessage(Component.literal("Vos yeux brûlent ! Il vous faut un casque d'éclipse.").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    // ================================================================== créatures

    /** Résultat d'une vague (utilisé par /eclipse vague pour le diagnostic). */
    public static final class WaveReport {
        public int spawned, players, skippedCreative, noPosition, peaceful, capReached;
    }

    private void spawnWave(MinecraftServer server, EclipseConfig cfg) {
        spawnWave(server, cfg, null);
    }

    /** Lance une vague. Si {@code only} est donné, seulement autour de ce joueur (même en créatif). */
    public WaveReport spawnWave(MinecraftServer server, EclipseConfig cfg, ServerPlayer only) {
        WaveReport rep = new WaveReport();
        if (totalWeight <= 0) return rep;
        EclipseConfig.Creatures c = cfg.creatures;
        cleanupTracked(server);

        for (ServerLevel level : eclipseLevels(server)) {
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                rep.peaceful++;
                continue;
            }
            RandomSource rnd = level.getRandom();
            for (ServerPlayer player : level.players()) {
                if (only != null && player != only) continue;
                rep.players++;
                if (only == null && c.ignorerCreatifSpectateur && (player.isCreative() || player.isSpectator())) {
                    rep.skippedCreative++;
                    continue;
                }
                if (eclipseMobs.size() >= c.maxTotal) {
                    rep.capReached++;
                    return rep;
                }
                // Seules les créatures d'éclipse comptent : les monstres déjà présents ne bloquent pas la vague.
                AABB box = player.getBoundingBox().inflate(c.distanceMax + 8, 32, c.distanceMax + 8);
                int nearby = level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(MOB_TAG)).size();
                int toSpawn = Math.min(c.apparitionsParVague, c.monstresMaxParJoueur - nearby);
                if (toSpawn <= 0) {
                    rep.capReached++;
                    continue;
                }
                int budget = c.monstresMaxParJoueur - nearby;
                for (int i = 0; i < toSpawn && budget > 0; i++) {
                    int n = trySpawnNear(level, player, rnd, c, budget);
                    if (n > 0) {
                        rep.spawned += n;
                        budget -= n;
                    } else rep.noPosition++;
                }
            }
        }
        return rep;
    }

    /** Fait apparaître un groupe près du joueur. Retourne le nombre de créatures apparues. */
    private int trySpawnNear(ServerLevel level, ServerPlayer player, RandomSource rnd, EclipseConfig.Creatures c, int budget) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = c.distanceMin + rnd.nextDouble() * (c.distanceMax - c.distanceMin);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos probe = new BlockPos(x, player.getBlockY(), z);
            if (!level.isPositionEntityTicking(probe)) continue;

            BlockPos pos = findGround(level, player, x, z);
            if (pos == null) continue;
            if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, c.distanceMin * 0.75, false) != null) continue;

            EntityType<?> type = pickType(rnd);
            if (type == null) return 0;
            int group = Math.min(budget, 1 + rnd.nextInt(Math.max(1, c.tailleGroupeMax)));
            if (type == ModEntities.ECLIPSE_COLOSSUS.get()) group = 1; // les colosses arrivent seuls
            int spawned = 0;
            for (int g = 0; g < group; g++) {
                BlockPos p = pos;
                if (g > 0) {
                    BlockPos q = findGround(level, player, pos.getX() + rnd.nextInt(5) - 2, pos.getZ() + rnd.nextInt(5) - 2);
                    if (q == null || Math.abs(q.getY() - pos.getY()) > 3) continue;
                    p = q;
                }
                if (spawnOne(level, type, p, rnd, c)) spawned++;
            }
            if (spawned > 0) return spawned;
        }
        return 0;
    }

    @org.jetbrains.annotations.Nullable
    private static BlockPos findGround(ServerLevel level, ServerPlayer player, int x, int z) {
        // 1) À la surface (sous le ciel)…
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos surface = new BlockPos(x, y, z);
        if (Math.abs(y - player.getBlockY()) <= 24 && isValidSpawn(level, surface)) return surface;
        // 2) …sinon à la hauteur du joueur (grotte, ville couverte, sous les arbres).
        for (int dy = 0; dy <= 10; dy++) {
            for (int sign : new int[]{-1, 1}) {
                BlockPos p = new BlockPos(x, player.getBlockY() + dy * sign, z);
                if (isValidSpawn(level, p)) return p;
            }
        }
        return null;
    }

    private boolean spawnOne(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource rnd, EclipseConfig.Creatures c) {
        Entity ent = type.create(level);
        if (!(ent instanceof Mob mob)) {
            if (ent != null) ent.discard();
            return false;
        }
        double y = pos.getY();
        if (mob instanceof Phantom) {
            BlockPos air = pos.above(10 + rnd.nextInt(6));
            if (!level.isEmptyBlock(air)) return false;
            y = air.getY();
        }
        mob.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        if (mob.isSpawnCancelled()) {
            mob.discard();
            return false;
        }
        // Mutants : zombies, squelettes et araignées d'éclipse peuvent être géants.
        if ((mob instanceof ShadowZombie && !(mob instanceof EclipseColossus) || mob instanceof VoidSkeleton || mob instanceof DuskSpider)
                && rnd.nextInt(100) < c.chanceMutantPourcent) {
            EclipseCreature.makeMutant(mob, c.tailleMutant);
        }
        if (!level.noCollision(mob)) {
            mob.discard();
            return false;
        }
        markEclipseMob(mob, c);
        level.addFreshEntityWithPassengers(mob);
        mob.getSelfAndPassengers().forEach(e -> eclipseMobs.add(e.getUUID()));
        return true;
    }

    private static void markEclipseMob(Mob mob, EclipseConfig.Creatures c) {
        mob.getSelfAndPassengers().forEach(e -> {
            e.addTag(MOB_TAG);
            if (c.resistantsAuSoleil && e instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
        });
    }

    private static boolean isValidSpawn(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
            if (!level.getFluidState(p).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ spawn naturel pendant l'éclipse

    private boolean isConfiguredType(EntityType<?> type) {
        for (Weighted w : mobTypes) if (w.type() == type) return true;
        return false;
    }

    private boolean naturalBoostActive(net.minecraft.world.level.Level level) {
        EclipseConfig cfg = EclipseConfig.get();
        if (!cfg.creatures.active || !cfg.creatures.spawnNaturelEnPleinJour) return false;
        if (!cfg.dimensions.contains(level.dimension().location().toString())) return false;
        return mobPhaseActive;
    }

    /**
     * Pendant l'éclipse, les monstres de la liste peuvent apparaître naturellement même en pleine lumière
     * (la règle « il faut qu'il fasse sombre » est levée). Retourne true pour forcer l'autorisation.
     */
    public boolean allowNaturalSpawn(EntityType<?> type, net.minecraft.world.level.Level level, MobSpawnType spawnType, boolean defaultResult) {
        if (defaultResult) return false;
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return false;
        if (!isConfiguredType(type)) return false;
        return naturalBoostActive(level);
    }

    private EntityType<?> pickType(RandomSource rnd) {
        if (totalWeight <= 0) return null;
        int r = rnd.nextInt(totalWeight);
        for (Weighted w : mobTypes) {
            r -= w.weight();
            if (r < 0) return w.type();
        }
        return mobTypes.get(0).type();
    }

    private void cleanupTracked(MinecraftServer server) {
        Iterator<UUID> it = eclipseMobs.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            boolean alive = false;
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null && e.isAlive()) {
                    alive = true;
                    break;
                }
            }
            if (!alive) it.remove();
        }
    }

    private void despawnMobs(MinecraftServer server) {
        for (UUID id : eclipseMobs) {
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null) {
                    EclipseCreature.vanish(e);
                    break;
                }
            }
        }
        eclipseMobs.clear();
    }

    /** Appelé quand une entité rejoint un monde. Retourne true s'il faut l'empêcher d'apparaître. */
    public boolean onEntityJoin(Entity entity, boolean loadedFromDisk) {
        if (!entity.getTags().contains(MOB_TAG)) {
            // Monstre apparu naturellement pendant l'éclipse : il devient une créature d'éclipse.
            if (!loadedFromDisk && entity instanceof Mob mob && mob.getSpawnType() == MobSpawnType.NATURAL
                    && isConfiguredType(mob.getType()) && naturalBoostActive(mob.level())) {
                markEclipseMob(mob, EclipseConfig.get().creatures);
                eclipseMobs.add(mob.getUUID());
            }
            return false;
        }
        long now = System.currentTimeMillis();
        Eclipse cur = EclipseSchedule.get().current(now);
        if (cur != null) {
            eclipseMobs.add(entity.getUUID());
            return false;
        }
        // Créature d'une éclipse passée rechargée avec son chunk : on la retire.
        return loadedFromDisk && EclipseConfig.get().creatures.disparaissentALaFin;
    }


    // ================================================================== moine ténébreux

    private long lastMonkAnnounce = 0;
    private BlockPos monkLastPos = null;

    private void monkTick(MinecraftServer server, Eclipse cur, EclipseConfig cfg) {
        if (cur == null) {
            monkLastPos = null;
            return;
        }
        if (!cfg.moine.active) return;
        // Un seul moine par éclipse, dès que les créatures déferlent.
        if (mobPhaseActive && !cur.moineApparu) {
            spawnMonk(server, cfg, null);
        }
        if (cur.moineApparu && !cur.moineVaincu && cur.moineUuid != null
                && tick - lastMonkAnnounce >= cfg.moine.annonceSecondes * 20L) {
            lastMonkAnnounce = tick;
            announceMonk(server, cur);
        }
    }

    /** Fait apparaître le moine près d'un joueur (ou de {@code near}). Retourne null si impossible. */
    public DarkMonk spawnMonk(MinecraftServer server, EclipseConfig cfg, ServerPlayer near) {
        long now = System.currentTimeMillis();
        Eclipse cur = EclipseSchedule.get().current(now);
        if (cur == null) return null;

        List<ServerPlayer> candidates = new ArrayList<>();
        if (near != null) candidates.add(near);
        else for (ServerLevel l : eclipseLevels(server)) {
            if (l.getDifficulty() == Difficulty.PEACEFUL) continue;
            for (ServerPlayer p : l.players()) if (!p.isCreative() && !p.isSpectator()) candidates.add(p);
        }
        if (candidates.isEmpty()) return null;

        ServerPlayer target = candidates.get(server.overworld().getRandom().nextInt(candidates.size()));
        ServerLevel level = target.serverLevel();
        RandomSource rnd = level.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            double dist = cfg.moine.distanceMin + rnd.nextDouble() * (cfg.moine.distanceMax - cfg.moine.distanceMin);
            int x = Mth.floor(target.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(target.getZ() + Math.sin(angle) * dist);
            if (!level.isPositionEntityTicking(new BlockPos(x, target.getBlockY(), z))) continue;
            BlockPos pos = findGround(level, target, x, z);
            if (pos == null) continue;

            DarkMonk monk = ModEntities.DARK_MONK.get().create(level);
            if (monk == null) return null;
            monk.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
            if (!level.noCollision(monk)) {
                monk.discard();
                continue;
            }
            EventHooks.finalizeMobSpawn(monk, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            monk.setHealth(monk.getMaxHealth());
            markEclipseMob(monk, cfg.creatures);
            level.addFreshEntity(monk);
            eclipseMobs.add(monk.getUUID());

            cur.moineApparu = true;
            cur.moineVaincu = false;
            cur.moineUuid = monk.getUUID().toString();
            EclipseSchedule.get().save();
            monkLastPos = pos;
            lastMonkAnnounce = tick;

            broadcast(server, Texts.msg("Un ").append(Component.literal("Moine ténébreux").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                    .append(Component.literal(" est apparu en " + coords(pos) + ", près de " + target.getGameProfile().getName()
                            + ". Ne l'approchez pas… ou terrassez-le !").withStyle(ChatFormatting.YELLOW)));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 0.8F, 0.5F);
            }
            return monk;
        }
        return null;
    }

    private void announceMonk(MinecraftServer server, Eclipse cur) {
        UUID id;
        try {
            id = UUID.fromString(cur.moineUuid);
        } catch (Exception e) {
            return;
        }
        Entity monk = null;
        for (ServerLevel l : server.getAllLevels()) {
            monk = l.getEntity(id);
            if (monk != null) break;
        }
        if (monk != null && monk.isAlive()) {
            monkLastPos = monk.blockPosition();
            Player nearest = monk.level().getNearestPlayer(monk, 256);
            String near = nearest == null ? "" : " — près de " + nearest.getGameProfile().getName()
                    + " (" + Mth.floor(nearest.distanceTo(monk)) + " blocs)";
            int hp = Mth.ceil(((LivingEntity) monk).getHealth());
            broadcast(server, Texts.msg("Le ").append(Component.literal("Moine ténébreux").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(" rôde en " + coords(monkLastPos) + near + " · " + hp + " PV").withStyle(ChatFormatting.GRAY)));
        } else if (monkLastPos != null) {
            broadcast(server, Texts.msg("Le ").append(Component.literal("Moine ténébreux").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(" a été vu pour la dernière fois en " + coords(monkLastPos) + ".").withStyle(ChatFormatting.GRAY)));
        }
    }

    public void onMonkDeath(DarkMonk monk, DamageSource source) {
        MinecraftServer server = monk.getServer();
        if (server == null) return;
        Eclipse cur = EclipseSchedule.get().current(System.currentTimeMillis());
        if (cur != null && monk.getUUID().toString().equals(cur.moineUuid)) {
            cur.moineVaincu = true;
            EclipseSchedule.get().save();
        }
        String killer = source.getEntity() instanceof Player p ? p.getGameProfile().getName() : null;
        broadcast(server, Texts.msg("Le ").append(Component.literal("Moine ténébreux").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal(killer != null ? " a été terrassé par " + killer + " !" : " a été terrassé !").withStyle(ChatFormatting.GOLD)));
    }

    /** Pour /eclipse moine : un moine est-il déjà en vie ? */
    public boolean monkAlive(MinecraftServer server) {
        Eclipse cur = EclipseSchedule.get().current(System.currentTimeMillis());
        if (cur == null || cur.moineUuid == null || cur.moineVaincu) return false;
        try {
            UUID id = UUID.fromString(cur.moineUuid);
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e != null && e.isAlive()) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String coords(BlockPos p) {
        return p.getX() + " / " + p.getY() + " / " + p.getZ();
    }

    // ================================================================== commandes admin

    public int trackedMobCount() {
        return eclipseMobs.size();
    }
}
