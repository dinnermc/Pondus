package dinner.dev.pondus.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandlerFabric {

    public static void register() {
        // Register payload types on both logical sides before registering receivers or sending packets.
        PayloadTypeRegistry.playS2C().register(S2CEntityGravityPacket.TYPE, S2CEntityGravityPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2CLevelGravityPacket.TYPE, S2CLevelGravityPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(S2CZoneVisualizationPacket.TYPE, S2CZoneVisualizationPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(C2SRequestZoneVisualizationPacket.TYPE, C2SRequestZoneVisualizationPacket.STREAM_CODEC);

        // Register server-bound (C2S) receivers.
        ServerPlayNetworking.registerGlobalReceiver(C2SRequestZoneVisualizationPacket.TYPE, (packet, context) ->
            context.player().server.execute(() -> packet.handleServer(context.player()))
        );
    }

    public static void registerClientPackets() {
        // Register client-bound (S2C) receivers on the client entrypoint only.
        ClientPlayNetworking.registerGlobalReceiver(S2CEntityGravityPacket.TYPE, (packet, context) ->
            context.client().execute(packet::handleClient)
        );
        ClientPlayNetworking.registerGlobalReceiver(S2CLevelGravityPacket.TYPE, (packet, context) ->
            context.client().execute(packet::handleClient)
        );
        ClientPlayNetworking.registerGlobalReceiver(S2CZoneVisualizationPacket.TYPE, (packet, context) ->
            context.client().execute(packet::handleClient)
        );
    }

    public static <MSG extends S2CModPacket> void sendToClient(MSG packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }

    public static <MSG extends C2SModPacket> void sendToServer(MSG packet) {
        ClientPlayNetworking.send(packet);
    }
}
