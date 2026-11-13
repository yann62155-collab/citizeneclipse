package fr.citizen.eclipse.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/** Araignée des ténèbres : grande, rapide, venimeuse et toujours agressive (même en plein jour). */
public class DuskSpider extends Spider {
    public DuskSpider(EntityType<? extends Spider> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes()
                .add(Attributes.MAX_HEALTH, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SCALE, 1.4);
    }

    /** Les araignées vanilla deviennent neutres à la lumière : celle-ci reste toujours hostile. */
    @Override
    public float getLightLevelDependentMagicValue() {
        return 0.0F;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 7 * 20, 0), this);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        EclipseCreature.tick(this);
    }
}
