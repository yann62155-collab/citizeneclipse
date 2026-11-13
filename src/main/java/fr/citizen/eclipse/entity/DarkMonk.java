package fr.citizen.eclipse.entity;

import fr.citizen.eclipse.EclipseManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Moine ténébreux : unique pendant chaque éclipse. Grand, 200 PV, il garde les bras croisés
 * tant qu'on le laisse tranquille, mais attaque quiconque s'approche ou le frappe.
 * Modèle sur mesure : capuche pointue, visage dans l'ombre, yeux violets, robe et cape en lambeaux.
 */
public class DarkMonk extends Monster {
    /** Distance à partir de laquelle il attaque les joueurs (blocs). */
    public static final double AGGRO_RANGE = 8.0;

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.literal("Moine ténébreux").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    public DarkMonk(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.SCALE, 1.6);
    }

    /** Grand Moine (true) ou simple disciple (false). */
    public boolean isGrand() {
        return true;
    }

    @Override
    protected void registerGoals() {
        if (!isGrand()) {
            // Les disciples suivent le Grand Moine.
            this.goalSelector.addGoal(5, new FollowGrandMonkGoal(this));
        }
        // On remplace toute l'IA d'illageois (pas de raid, pas d'attaque des villageois).
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // Se défend quand on le frappe…
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // …et attaque les joueurs qui s'approchent trop.
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p.distanceToSqr(this) < AGGRO_RANGE * AGGRO_RANGE));
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Mains nues : bras croisés au repos, comme un moine.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ================================================================== pouvoirs magiques
    // Aucun sort ne touche aux blocs : uniquement des particules, des dégâts magiques et des effets.

    private static final EntityDataAccessor<Boolean> CASTING =
            SynchedEntityData.defineId(DarkMonk.class, EntityDataSerializers.BOOLEAN);

    private enum Cast { NONE, ZONE, SPIRAL }

    private Cast cast = Cast.NONE;
    private int castTicks = 0;
    private int zoneCooldown = 60;
    private int spiralCooldown = 40;
    /** Délai minimum entre deux sorts, quels qu'ils soient : 5 secondes. */
    public static final int GLOBAL_SPELL_COOLDOWN = 100;
    private int globalCooldown = 40;
    private int ringTicks = -1;
    private final List<Spiral> spirals = new ArrayList<>();

    /** Projectile en spirale simulé (aucune entité, aucun bloc touché). */
    private static final class Spiral {
        Vec3 pos;
        Vec3 dir;
        int age;

        Spiral(Vec3 pos, Vec3 dir) {
            this.pos = pos;
            this.dir = dir;
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
    }

    /** Bras levés pendant une incantation (utilisé par le modèle). */
    public boolean isCasting() {
        return this.entityData.get(CASTING);
    }

    private void setCasting(boolean value) {
        this.entityData.set(CASTING, value);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isGrand()) bossBar.setProgress(this.getHealth() / this.getMaxHealth());
        if (!(this.level() instanceof ServerLevel sl)) return;

        if (zoneCooldown > 0) zoneCooldown--;
        if (spiralCooldown > 0) spiralCooldown--;
        if (globalCooldown > 0) globalCooldown--;
        tickSpirals(sl);
        tickRing(sl);

        LivingEntity target = this.getTarget();
        boolean enraged = this.getHealth() < this.getMaxHealth() * 0.5F;

        if (cast != Cast.NONE) {
            this.getNavigation().stop();
            if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            castTicks--;
            if (cast == Cast.ZONE) {
                chargeZone(sl);
                if (castTicks <= 0) releaseZone(sl, enraged);
            } else if (cast == Cast.SPIRAL) {
                handGlow(sl);
                if (castTicks <= 0) releaseSpiral(sl, target, enraged);
            }
            if (castTicks <= 0) {
                cast = Cast.NONE;
                setCasting(false);
                globalCooldown = GLOBAL_SPELL_COOLDOWN; // 5 s avant le sort suivant
            }
            return;
        }

        if (target != null && target.isAlive() && globalCooldown == 0) {
            double d2 = this.distanceToSqr(target);
            if (isGrand() && zoneCooldown == 0 && d2 < 7.5 * 7.5) {
                startCast(Cast.ZONE, 30);
                zoneCooldown = enraged ? 140 : 220;
                this.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2.0F, 0.5F);
            } else if (spiralCooldown == 0 && d2 > 4.0 * 4.0 && d2 < 28.0 * 28.0 && this.hasLineOfSight(target)) {
                startCast(Cast.SPIRAL, 14);
                spiralCooldown = enraged ? 60 : 100;
                this.playSound(SoundEvents.EVOKER_PREPARE_WOLOLO, 1.6F, 0.4F);
            }
        }

        // Régénération lente quand personne ne le combat.
        if (target == null && this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(2.0F);
        }
    }

    private void startCast(Cast type, int ticks) {
        cast = type;
        castTicks = ticks;
        setCasting(true);
    }

    // ------------------------------------------------------------------ Cercle des ténèbres (zone)

    private static final double ZONE_RADIUS = 8.0;

    /** Avertissement : un cercle de flammes d'âme se dessine au sol autour du moine. */
    private void chargeZone(ServerLevel sl) {
        double y = this.getY() + 0.15;
        int points = 28;
        double spin = this.tickCount * 0.25;
        for (int i = 0; i < points; i++) {
            double a = spin + i * Math.PI * 2 / points;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX() + Math.cos(a) * ZONE_RADIUS, y,
                    this.getZ() + Math.sin(a) * ZONE_RADIUS, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.DRAGON_BREATH, this.getX(), this.getY() + 0.3, this.getZ(), 12, 1.2, 0.1, 1.2, 0.01);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 1.5 * this.getScale(), this.getZ(), 8, 0.6, 1.0, 0.6, 0.05);
    }

    private void releaseZone(ServerLevel sl, boolean enraged) {
        this.playSound(SoundEvents.EVOKER_CAST_SPELL, 2.5F, 0.4F);
        this.playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.6F);
        ringTicks = 0;
        float damage = enraged ? 11.0F : 8.0F;
        for (Player p : sl.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(ZONE_RADIUS, 4.0, ZONE_RADIUS))) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.distanceToSqr(this) > ZONE_RADIUS * ZONE_RADIUS) continue;
            p.hurt(this.damageSources().indirectMagic(this, this), damage);
            p.knockback(1.6, this.getX() - p.getX(), this.getZ() - p.getZ());
            p.push(0.0, 0.45, 0.0);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
        }
    }

    /** Onde visuelle qui s'élargit (purement décorative). */
    private void tickRing(ServerLevel sl) {
        if (ringTicks < 0) return;
        double r = 1.0 + ringTicks * 0.9;
        int points = (int) (r * 8);
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            double x = this.getX() + Math.cos(a) * r;
            double z = this.getZ() + Math.sin(a) * r;
            sl.sendParticles(ParticleTypes.SQUID_INK, x, this.getY() + 0.3, z, 1, 0, 0.05, 0, 0.0);
            if (i % 2 == 0) sl.sendParticles(ParticleTypes.WITCH, x, this.getY() + 0.8, z, 1, 0, 0.2, 0, 0.0);
        }
        if (++ringTicks > 8) ringTicks = -1;
    }

    // ------------------------------------------------------------------ Spirale d'ombre (attaque directe)

    private void handGlow(ServerLevel sl) {
        double h = this.getY() + 2.2 * this.getScale();
        sl.sendParticles(ParticleTypes.WITCH, this.getX(), h, this.getZ(), 4, 0.4, 0.2, 0.4, 0.0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), h, this.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
    }

    private void releaseSpiral(ServerLevel sl, LivingEntity target, boolean enraged) {
        if (target == null || !target.isAlive()) return;
        Vec3 from = this.getEyePosition();
        int count = enraged && isGrand() ? 2 : 1;
        for (int i = 0; i < count; i++) {
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
            if (i == 1) aim = aim.yRot(0.25F).normalize();
            spirals.add(new Spiral(from.add(aim.scale(1.2)), aim));
        }
        this.playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 0.6F);
    }

    private void tickSpirals(ServerLevel sl) {
        if (spirals.isEmpty()) return;
        LivingEntity target = this.getTarget();
        Iterator<Spiral> it = spirals.iterator();
        while (it.hasNext()) {
            Spiral s = it.next();
            s.age++;
            // Légère poursuite de la cible.
            if (target != null && target.isAlive()) {
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(s.pos).normalize();
                s.dir = s.dir.scale(0.88).add(to.scale(0.12)).normalize();
            }
            boolean done = false;
            for (int step = 0; step < 2 && !done; step++) {
                s.pos = s.pos.add(s.dir.scale(0.4));
                drawHelix(sl, s, step);
                BlockPos bp = BlockPos.containing(s.pos);
                // Un bloc l'arrête, sans jamais l'abîmer.
                if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) {
                    impact(sl, s.pos, null);
                    done = true;
                    break;
                }
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(s.pos, s.pos).inflate(0.9))) {
                    if (p.isCreative() || p.isSpectator()) continue;
                    impact(sl, s.pos, p);
                    done = true;
                    break;
                }
            }
            if (done || s.age > 45) {
                if (!done) impact(sl, s.pos, null);
                it.remove();
            }
        }
    }

    private void drawHelix(ServerLevel sl, Spiral s, int step) {
        Vec3 d = s.dir;
        Vec3 up = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(up).normalize();
        Vec3 v = d.cross(u).normalize();
        double angle = (s.age * 2 + step) * 0.9;
        double r = 0.45;
        Vec3 o1 = u.scale(Math.cos(angle) * r).add(v.scale(Math.sin(angle) * r));
        Vec3 p1 = s.pos.add(o1);
        Vec3 p2 = s.pos.subtract(o1);
        sl.sendParticles(ParticleTypes.WITCH, p1.x, p1.y, p1.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p2.x, p2.y, p2.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s.pos.x, s.pos.y, s.pos.z, 1, 0, 0, 0, 0);
    }

    private void impact(ServerLevel sl, Vec3 at, Player hit) {
        sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 20, 0.4, 0.4, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 15, 0.5, 0.5, 0.5, 0.1);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.4F);
        if (hit != null) {
            boolean enraged = this.getHealth() < this.getMaxHealth() * 0.5F;
            float dmg = enraged ? 9.0F : 7.0F;
            if (!isGrand()) dmg = 4.0F;
            hit.hurt(this.damageSources().indirectMagic(this, this), dmg);
            hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
            hit.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 0), this);
            hit.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0), this);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isGrand()) bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.level().isClientSide()) {
            bossBar.removeAllPlayers();
            EclipseManager.get().onMonkDeath(this, source);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            // Volutes sombres et étincelles violettes autour du moine.
            float s = this.getScale();
            if (this.random.nextInt(2) == 0) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = 0.6 * s + this.random.nextDouble() * 0.5 * s;
                this.level().addParticle(ParticleTypes.REVERSE_PORTAL,
                        this.getX() + Math.cos(a) * r, this.getY() + this.random.nextDouble() * 1.8 * s, this.getZ() + Math.sin(a) * r,
                        0.0, 0.02, 0.0);
            }
            if (this.random.nextInt(3) == 0) {
                this.level().addParticle(ParticleTypes.SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.9 * s, this.getY() + 0.1,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.9 * s, 0.0, 0.01, 0.0);
            }
            if (this.isCasting()) {
                this.level().addParticle(ParticleTypes.WITCH,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.8 * s, this.getY() + 2.1 * s,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.8 * s, 0.0, 0.05, 0.0);
            }
            if (this.random.nextInt(6) == 0) {
                this.level().addParticle(ParticleTypes.WITCH,
                        this.getX() + (this.random.nextDouble() - 0.5) * 1.6 * s, this.getY() + 0.4 * s + this.random.nextDouble() * 1.4 * s,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 1.6 * s, 0.0, 0.0, 0.0);
            }
        }
        EclipseCreature.tick(this);
    }

    /** Les disciples restent groupés autour du Grand Moine quand ils ne se battent pas. */
    static final class FollowGrandMonkGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final DarkMonk disciple;
        private DarkMonk leader;
        private int recalc;

        FollowGrandMonkGoal(DarkMonk disciple) {
            this.disciple = disciple;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        private DarkMonk findLeader() {
            List<DarkMonk> list = disciple.level().getEntitiesOfClass(DarkMonk.class,
                    disciple.getBoundingBox().inflate(48.0), m -> m.isGrand() && m.isAlive());
            return list.isEmpty() ? null : list.get(0);
        }

        @Override
        public boolean canUse() {
            if (disciple.getTarget() != null) return false;
            leader = findLeader();
            return leader != null && disciple.distanceToSqr(leader) > 6.0 * 6.0;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && disciple.getTarget() == null
                    && disciple.distanceToSqr(leader) > 3.0 * 3.0;
        }

        @Override
        public void tick() {
            if (--recalc <= 0) {
                recalc = 10;
                disciple.getNavigation().moveTo(leader, 1.1);
            }
        }

        @Override
        public void stop() {
            leader = null;
            disciple.getNavigation().stop();
        }
    }
}
