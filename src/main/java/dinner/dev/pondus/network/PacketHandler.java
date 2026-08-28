package dinner.dev.pondus.network;

import dinner.dev.pondus.platform.Services;

public class PacketHandler {

    public static void registerPackets() {
        Services.PLATFORM.registerClientPlayPacket(S2CEntityGravityPacket.TYPE,S2CEntityGravityPacket.STREAM_CODEC);
    }
}
