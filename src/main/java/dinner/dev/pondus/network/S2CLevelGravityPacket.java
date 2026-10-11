package dinner.dev.pondus.network;

import dinner.dev.pondus.ClientPacketHandler;
import dinner.dev.pondus.Pondus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CLevelGravityPacket(CompoundTag data) implements S2CModPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CLevelGravityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, S2CLevelGravityPacket::data,
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
