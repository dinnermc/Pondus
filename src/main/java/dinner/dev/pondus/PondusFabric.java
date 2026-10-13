package dinner.dev.pondus;

import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.command.GravityCommand;
import dinner.dev.pondus.network.PacketHandlerFabric;
import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.util.EntityGravityData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PondusFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Pondus.init();
        Pondus.register();
        CommonEvents.initFabric();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> GravityCommand.register(dispatcher));

        // Register network packets
        PacketHandlerFabric.register();

        // Sync gravity data when a player joins
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncPlayerGravity(handler.player));

        // Re-sync gravity data after a player respawns
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncPlayerGravity(newPlayer));
    }

    private static void syncPlayerGravity(ServerPlayer player) {
        EntityGravityData entityGravityData = PondusAPI.getGravityData(player);
        CompoundTag data = new CompoundTag();
        entityGravityData.toNbt(data);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(player, data), player, true);
    }
}
