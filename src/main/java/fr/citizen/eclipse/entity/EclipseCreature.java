package fr.citizen.eclipse.entity;

import fr.citizen.eclipse.EclipseManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import fr.citizen.eclipse.CitizenEclipse;

/** Comportement commun des créatures d'éclipse : elles se dissipent quand l'éclipse est finie. */
public final class EclipseCreature {
    private EclipseCreature() {}

    /** À appeler dans aiStep() : hors éclipse, la créature part en fumée. */
    public static void tick(Mob mob) {
        if (mob.level().isClientSide() || mob.tickCount % 20 != 0) return;
        if (EclipseManager.isRunning()) return;
        vanish(mob);
    }

    public static void vanish(Entity e) {
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                    18, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.02);
            sl.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                    6, e.getBbWidth() / 2, e.getBbHeight() / 2, e.getBbWidth() / 2, 0.03);
        }
        e.discard();
    }

    public static final String MUTANT_TAG = "citizeneclipse.mutant";

    /** Version mutante : bien plus grande, plus de vie, frappe plus fort. */
    public static void makeMutant(Mob mob, double scale) {
        if (mob.getTags().contains(MUTANT_TAG)) return;
        mob.addTag(MUTANT_TAG);
        double extra = Math.max(0.0, scale - 1.0);
        modify(mob, Attributes.SCALE, "mutant_scale", extra);
        modify(mob, Attributes.MAX_HEALTH, "mutant_health", extra * 1.4);
        modify(mob, Attributes.ATTACK_DAMAGE, "mutant_damage", extra * 0.7);
        modify(mob, Attributes.KNOCKBACK_RESISTANCE, "mutant_kb", 0.0);
        AttributeInstance kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.8));
        mob.setHealth(mob.getMaxHealth());
    }

    private static void modify(Mob mob, Holder<Attribute> attr, String id, double amount) {
        if (amount == 0.0) return;
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        inst.addPermanentModifier(new AttributeModifier(CitizenEclipse.id(id), amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
