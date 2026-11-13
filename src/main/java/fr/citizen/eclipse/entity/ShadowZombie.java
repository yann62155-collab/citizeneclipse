package fr.citizen.eclipse.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** Zombie d'ombre : noir aux yeux rouges, résistant, ne brûle pas au soleil. */
public class ShadowZombie extends Zombie {
    public ShadowZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected void handleAttributes(float difficulty) {
        super.handleAttributes(difficulty);
        // Pas de renforts : ils seraient des zombies normaux qui ne disparaîtraient pas.
        AttributeInstance r = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (r != null) r.setBaseValue(0.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        EclipseCreature.tick(this);
    }
}
