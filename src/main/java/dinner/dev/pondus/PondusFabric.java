package dinner.dev.pondus;

import dinner.dev.pondus.api.GravitySourceManager;
import dinner.dev.pondus.api.ItemGravityManager;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.command.GravityCommand;
import dinner.dev.pondus.network.PacketHandlerFabric;
import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.network.S2CLevelGravityPacket;
import dinner.dev.pondus.network.S2CZoneVisualizationPacket;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.util.EntityGravityData;
import dinner.dev.pondus.zone.ZoneManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PondusFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Pondus.init();
        Pondus.register();

        // Register built-in gravity sources (zones + declarative item gravity rules)
        GravitySourceManager.register(ZoneManager.getInstance());
        GravitySourceManager.register(ItemGravityManager.getInstance());

        // Evaluate gravity sources BEFORE entities tick so hitbox/sync update on the same tick
        ServerTickEvents.START_WORLD_TICK.register(GravitySourceManager::tick);

        // Clear dimension-scoped sources on server stop so they never leak across worlds
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GravitySourceManager.clearLevelSources());

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            GravityCommand.register(dispatcher)
        );

        // Register network payloads and server receivers
        PacketHandlerFabric.register();

        // Sync gravity data & dimension strength when a player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            syncLevelGravity(player, player.serverLevel());
            syncPlayerGravity(player);
        });

        // Respect resetGravityOnRespawn when copying player data across death/dimension transitions
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !Pondus.config.resetGravityOnRespawn) {
                CompoundTag data = new CompoundTag();
                PondusAPI.getGravityData(oldPlayer).toNbt(data);
                PondusAPI.getGravityData(newPlayer).fromNbt(data);
            }
        });

        // Re-sync gravity data after respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            syncLevelGravity(newPlayer, newPlayer.serverLevel());
            syncPlayerGravity(newPlayer);
        });

        // Sync dimension gravity, zone visualization, and player gravity on dimension change
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            syncLevelGravity(player, destination);
            Services.PLATFORM.sendToClient(
                S2CZoneVisualizationPacket.fromZones(
                    destination.dimension(),
                    ZoneManager.getInstance().getZones(destination).values()
                ),
                player
            );
            syncPlayerGravity(player);
        });

        // Sync tracked entities when a player starts tracking them
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            EntityGravityData targetData = PondusAPI.getGravityData(trackedEntity);
            CompoundTag data = new CompoundTag();
            targetData.toNbt(data);
            Services.PLATFORM.sendToClient(new S2CEntityGravityPacket(trackedEntity, data), player);
        });
    }

    private static void syncLevelGravity(ServerPlayer player, ServerLevel level) {
        Services.PLATFORM.sendToClient(
            new S2CLevelGravityPacket(level.dimension(), PondusAPI.getDimensionGravityStrength(level)),
            player
        );
    }

    private static void syncPlayerGravity(ServerPlayer player) {
        EntityGravityData entityGravityData = PondusAPI.getGravityData(player);
        CompoundTag data = new CompoundTag();
        entityGravityData.toNbt(data);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(player, data), player, true);
    }
}
