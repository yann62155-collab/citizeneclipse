package fr.citizen.eclipse.entity;

import fr.citizen.eclipse.CitizenEclipse;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CitizenEclipse.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowZombie>> SHADOW_ZOMBIE = ENTITY_TYPES.register("shadow_zombie",
            () -> EntityType.Builder.<ShadowZombie>of(ShadowZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).build("shadow_zombie"));

    public static final DeferredHolder<EntityType<?>, EntityType<EclipseColossus>> ECLIPSE_COLOSSUS = ENTITY_TYPES.register("eclipse_colossus",
            () -> EntityType.Builder.<EclipseColossus>of(EclipseColossus::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(10).build("eclipse_colossus"));

    public static final DeferredHolder<EntityType<?>, EntityType<VoidSkeleton>> VOID_SKELETON = ENTITY_TYPES.register("void_skeleton",
            () -> EntityType.Builder.<VoidSkeleton>of(VoidSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).eyeHeight(1.74F).clientTrackingRange(8).build("void_skeleton"));

    public static final DeferredHolder<EntityType<?>, EntityType<DuskSpider>> DUSK_SPIDER = ENTITY_TYPES.register("dusk_spider",
            () -> EntityType.Builder.<DuskSpider>of(DuskSpider::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.9F).eyeHeight(0.65F).clientTrackingRange(8).build("dusk_spider"));

    public static final DeferredHolder<EntityType<?>, EntityType<EclipseWraith>> ECLIPSE_WRAITH = ENTITY_TYPES.register("eclipse_wraith",
            () -> EntityType.Builder.<EclipseWraith>of(EclipseWraith::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.5F).eyeHeight(0.175F).clientTrackingRange(8).build("eclipse_wraith"));

    public static final DeferredHolder<EntityType<?>, EntityType<DarkMonk>> DARK_MONK = ENTITY_TYPES.register("dark_monk",
            () -> EntityType.Builder.<DarkMonk>of(DarkMonk::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(12).build("dark_monk"));

    public static final DeferredHolder<EntityType<?>, EntityType<DiscipleMonk>> DISCIPLE_MONK = ENTITY_TYPES.register("disciple_monk",
            () -> EntityType.Builder.<DiscipleMonk>of(DiscipleMonk::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build("disciple_monk"));

    @EventBusSubscriber(modid = CitizenEclipse.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeEvents {
        @SubscribeEvent
        public static void onAttributes(EntityAttributeCreationEvent event) {
            event.put(SHADOW_ZOMBIE.get(), ShadowZombie.createAttributes().build());
            event.put(ECLIPSE_COLOSSUS.get(), EclipseColossus.createColossusAttributes().build());
            event.put(VOID_SKELETON.get(), VoidSkeleton.createAttributes().build());
            event.put(DUSK_SPIDER.get(), DuskSpider.createAttributes().build());
            event.put(ECLIPSE_WRAITH.get(), EclipseWraith.createAttributes().build());
            event.put(DARK_MONK.get(), DarkMonk.createAttributes().build());
            event.put(DISCIPLE_MONK.get(), DiscipleMonk.createDiscipleAttributes().build());
        }
    }
}
