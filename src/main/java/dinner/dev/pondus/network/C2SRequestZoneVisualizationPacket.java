package dinner.dev.pondus.network;

import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.zone.ZoneManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record C2SRequestZoneVisualizationPacket() implements C2SModPacket {
    public static final Type<C2SRequestZoneVisualizationPacket> TYPE =
        new Type<>(Pondus.id("request_zone_visualization"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestZoneVisualizationPacket> STREAM_CODEC =
        StreamCodec.unit(new C2SRequestZoneVisualizationPacket());

    @Override
    public void handleServer(ServerPlayer player) {
        Services.PLATFORM.sendToClient(
            S2CZoneVisualizationPacket.fromZones(
                player.serverLevel().dimension(),
                ZoneManager.getInstance().getZones(player.serverLevel()).values()
            ),
            player
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
