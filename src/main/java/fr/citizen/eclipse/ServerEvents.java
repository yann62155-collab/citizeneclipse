package fr.citizen.eclipse;

import fr.citizen.eclipse.command.EclipseCommand;
import fr.citizen.eclipse.config.EclipseConfig;
import fr.citizen.eclipse.web.EclipseWebServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Événements côté serveur (bus du jeu). */
public class ServerEvents {

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        EclipseManager.get().onServerStarted(event.getServer());
        EclipseConfig.Web web = EclipseConfig.get().web;
        if (web.active && (!web.seulementServeurDedie || event.getServer().isDedicatedServer())) {
            EclipseWebServer.start();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        EclipseWebServer.stop();
        EclipseManager.get().onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        EclipseManager.get().onServerTick(event.getServer());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        EclipseCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) EclipseManager.get().onLogin(sp);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) EclipseManager.get().onLogout(sp);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (EclipseManager.get().onEntityJoin(event.getEntity(), event.loadedFromDisk())) {
            event.setCanceled(true);
        }
    }

    /** Pendant l'éclipse, les monstres peuvent apparaître naturellement même en plein jour. */
    @SubscribeEvent
    public void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getResult() == MobSpawnEvent.SpawnPlacementCheck.Result.FAIL) return;
        Level level = event.getLevel().getLevel();
        if (EclipseManager.get().allowNaturalSpawn(event.getEntityType(), level, event.getSpawnType(), event.getDefaultResult())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
        }
    }

    /** Deuxième contrôle vanilla (« assez sombre ? ») : levé aussi, mais on garde le contrôle d'obstruction. */
    @SubscribeEvent
    public void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() == MobSpawnEvent.PositionCheck.Result.FAIL) return;
        var mob = event.getEntity();
        Level level = event.getLevel().getLevel();
        if (EclipseManager.get().allowNaturalSpawn(mob.getType(), level, event.getSpawnType(), false)
                && mob.checkSpawnObstruction(event.getLevel())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
        }
    }
}
