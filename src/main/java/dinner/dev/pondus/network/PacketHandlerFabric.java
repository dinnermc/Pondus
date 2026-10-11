package dinner.dev.pondus.network;

import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.platform.Services;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandlerFabric {

    public static void register() {
        // Register client-bound packets
        Services.PLATFORM.registerClientPlayPacket(S2CEntityGravityPacket.TYPE, S2CEntityGravityPacket.STREAM_CODEC);
        
        // Register server-bound packets if any
        // Services.PLATFORM.registerServerPlayPacket(C2SPacket.TYPE, C2SPacket.STREAM_CODEC);
    }

    public static void registerClientPackets() {
        // Register client-bound packets on the client side
        ClientPlayNetworking.registerGlobalReceiver(S2CEntityGravityPacket.TYPE, (packet, context) -> {
            packet.handleClient();
        });
        
        ClientPlayNetworking.registerGlobalReceiver(S2CLevelGravityPacket.TYPE, (packet, context) -> {
            packet.handleClient();
        });
    }

    public static <MSG extends S2CModPacket> void sendToClient(MSG packet, ServerPlayer player) {
        ServerPlayNetworking.send(player, packet);
    }

    public static <MSG extends C2SModPacket> void sendToServer(MSG packet) {
        // Client side only - send to server
        ClientPlayNetworking.send(packet);
    }
}