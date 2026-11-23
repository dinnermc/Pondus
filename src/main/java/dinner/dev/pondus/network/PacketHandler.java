package dinner.dev.pondus.network;

import dinner.dev.pondus.platform.Services;

public class PacketHandler {

    public static void registerPackets() {
        Services.PLATFORM.registerClientPlayPacket(S2CEntityGravityPacket.TYPE, S2CEntityGravityPacket.STREAM_CODEC);
        Services.PLATFORM.registerClientPlayPacket(S2CLevelGravityPacket.TYPE, S2CLevelGravityPacket.STREAM_CODEC);
        Services.PLATFORM.registerClientPlayPacket(
            S2CZoneVisualizationPacket.TYPE, S2CZoneVisualizationPacket.STREAM_CODEC
        );
        Services.PLATFORM.registerServerPlayPacket(
            C2SRequestZoneVisualizationPacket.TYPE, C2SRequestZoneVisualizationPacket.STREAM_CODEC
        );
    }
}
