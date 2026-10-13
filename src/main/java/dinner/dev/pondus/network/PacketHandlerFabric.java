package dinner.dev.pondus.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandlerFabric {

    public static void register() {
        // Server-bound (C2S) receivers would be registered here. None currently.
    }

    public static void registerClientPackets() {
        // Client-bound (S2C) receivers.
        ClientPlayNetworking.registerGlobalReceiver(S2CEntityGravityPacket.TYPE, (packet, context) -> {
            packet.handleClient();
        });
    }

    public static <MSG extends S2CModPacket> void sendToClient(MSG packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }

    public static <MSG extends C2SModPacket> void sendToServer(MSG packet) {
        ClientPlayNetworking.send(packet);
    }
}
