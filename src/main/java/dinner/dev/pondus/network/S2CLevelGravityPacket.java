package dinner.dev.pondus.network;

import dinner.dev.pondus.ClientPacketHandler;
import dinner.dev.pondus.Pondus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record S2CLevelGravityPacket(ResourceKey<Level> dimension, double strength) implements S2CModPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CLevelGravityPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), S2CLevelGravityPacket::dimension,
                    ByteBufCodecs.DOUBLE, S2CLevelGravityPacket::strength,
                    S2CLevelGravityPacket::new
            );

    public static final Type<S2CLevelGravityPacket> TYPE = new Type<>(Pondus.id("level_gravity"));

    @Override
    public void handleClient() {
        ClientPacketHandler.handle(this);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
