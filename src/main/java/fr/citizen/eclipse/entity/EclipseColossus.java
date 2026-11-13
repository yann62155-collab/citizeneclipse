package fr.citizen.eclipse.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** Colosse d'éclipse : zombie mutant géant (~4,5 blocs) rouge sombre, lent mais qui frappe très fort. */
public class EclipseColossus extends ShadowZombie {
    public EclipseColossus(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 50;
    }

    public static AttributeSupplier.Builder createColossusAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 140.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SCALE, 2.4);
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }
}
