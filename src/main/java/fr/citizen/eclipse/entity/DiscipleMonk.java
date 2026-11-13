package fr.citizen.eclipse.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Moine disciple : suit le Grand Moine, 200 PV, taille humaine, robe pourpre et yeux rouges.
 * Se défend, attaque les joueurs qui s'approchent et lance une spirale d'ombre plus faible
 * (5 s minimum entre deux sorts). Jamais plus de 4 en même temps.
 */
public class DiscipleMonk extends DarkMonk {
    public DiscipleMonk(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createDiscipleAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.SCALE, 1.0);
    }

    @Override
    public boolean isGrand() {
        return false;
    }
}
