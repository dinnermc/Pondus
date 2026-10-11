package dinner.dev.pondus;

import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.command.GravityCommand;
import dinner.dev.pondus.network.PacketHandlerFabric;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.util.EntityGravityData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class PondusFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        Pondus.init();
        Pondus.register();
        CommonEvents.initFabric();

        // Register commands
        CommandRegistrationCallback.EVENT.register(GravityCommand::register);

        // Register network packets
        PacketHandlerFabric.register();

        // Player login event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerLevel level = player.serverLevel();

            EntityGravityData entityGravityData = PondusAPI.getGravityData(player);
            CompoundTag data = new CompoundTag();
            entityGravityData.toNbt(data);
            Services.PLATFORM.sendToTracking(new dinner.dev.pondus.network.S2CEntityGravityPacket(player, data), player, true);
        });

        // Player respawn event
        net.fabricmc.fabric.api.event.player.PlayerRespawnCallback.EVENT.register((player, alive) -> {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ServerLevel level = serverPlayer.serverLevel();

            EntityGravityData entityGravityData = PondusAPI.getGravityData(serverPlayer);
            CompoundTag data = new CompoundTag();
            entityGravityData.toNbt(data);
            Services.PLATFORM.sendToTracking(new dinner.dev.pondus.network.S2CEntityGravityPacket(serverPlayer, data), serverPlayer, true);
        });

        // Player dimension change
        net.fabricmc.fabric.api.event.player.PlayerChangeDimensionCallback.EVENT.register((player, fromDim, toDim) -> {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ServerLevel level = serverPlayer.server.getLevel(toDim);

            // Level gravity sync would go here if needed
        });

        // Player tracking event
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            // Handle attack if needed
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}