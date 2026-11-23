package dinner.dev.pondus.platform;

import dinner.dev.pondus.api.GravityUpdateEvent;
import dinner.dev.pondus.attachments.CommonDataAttachment;
import dinner.dev.pondus.network.C2SModPacket;
import dinner.dev.pondus.network.PacketHandlerFabric;
import dinner.dev.pondus.network.S2CModPacket;
import dinner.dev.pondus.platform.services.IPlatformHelper;
import dinner.dev.pondus.util.EntityGravityData;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClient() {
        return EnvType.CLIENT == FabricLoader.getInstance().getEnvironmentType();
    }

    @Override
    public <MSG extends S2CModPacket> void registerClientPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        // Registered centrally in PacketHandlerFabric.register() / registerClientPackets().
    }

    @Override
    public <MSG extends C2SModPacket> void registerServerPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        // Registered centrally in PacketHandlerFabric.register().
    }

    @Override
    public void sendToClient(S2CModPacket msg, ServerPlayer player) {
        PacketHandlerFabric.sendToClient(msg, player);
    }

    @Override
    public void sendToServer(C2SModPacket msg) {
        PacketHandlerFabric.sendToServer(msg);
    }

    @Override
    public void sendToTracking(S2CModPacket msg, Entity entity, boolean includeSelf) {
        Set<ServerPlayer> players = new LinkedHashSet<>(PlayerLookup.tracking(entity));
        if (includeSelf && entity instanceof ServerPlayer self) {
            players.add(self);
        }
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, msg);
        }
    }

    @Override
    public void sendToLevel(S2CModPacket msg, ServerLevel level) {
        for (ServerPlayer player : PlayerLookup.world(level)) {
            ServerPlayNetworking.send(player, msg);
        }
    }

    @Override
    public <T> void registerDataAttachment(CommonDataAttachment<T> attachment) {
        AttachmentRegistry.Builder<T> builder = AttachmentRegistry.builder();
        if (attachment.getCodec() != null) {
            builder.persistent(attachment.getCodec());
            if (attachment.isCopyOnDeath()) {
                builder.copyOnDeath();
            }
        }

        AttachmentType<T> type = builder.buildAndRegister(attachment.getName());
        attachment.setAttachment(type);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    @Nullable
    public <T> T getAttachedValue(Object object, CommonDataAttachment<T> attachment) {
        AttachmentType<T> type = (AttachmentType<T>) attachment.getAttachment();
        if (object instanceof AttachmentTarget attachmentTarget) {
            return attachmentTarget.getAttached(type);
        } else {
            throw new IllegalStateException("Cannot attach data to " + object);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> void setAttachedValue(Object object, CommonDataAttachment<T> attachment, @Nullable T value) {
        AttachmentType<T> type = (AttachmentType<T>) attachment.getAttachment();
        if (object instanceof AttachmentTarget attachmentTarget) {
            attachmentTarget.setAttached(type, value);
        } else {
            throw new IllegalStateException("Cannot attach data to " + object);
        }
    }

    @Override
    public void postEvent(Entity entity, EntityGravityData entityGravityData) {
        GravityUpdateEvent.EVENT.invoker().onGravityUpdate(entity, entityGravityData);
    }
}
