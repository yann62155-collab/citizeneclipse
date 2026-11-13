package fr.citizen.eclipse;

import com.mojang.logging.LogUtils;
import fr.citizen.eclipse.entity.ModEntities;
import fr.citizen.eclipse.network.EclipseStatePayload;
import fr.citizen.eclipse.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

/**
 * Citizen Éclipse — éclipses planifiées pour le serveur RP Citizen.
 */
@Mod(CitizenEclipse.MODID)
public class CitizenEclipse {
    public static final String MODID = "citizeneclipse";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenEclipse(IEventBus modBus, ModContainer container) {
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITY_TYPES.register(modBus);

        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::buildCreativeTabs);

        NeoForge.EVENT_BUS.register(new ServerEvents());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(EclipseStatePayload.TYPE, EclipseStatePayload.STREAM_CODEC, EclipseStatePayload::handle);
    }

    private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.ECLIPSE_HELMET.get());
        }
    }
}
