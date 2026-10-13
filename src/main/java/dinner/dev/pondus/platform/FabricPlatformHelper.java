package dinner.dev.pondus.platform;

import dinner.dev.pondus.CommonEvents;
import dinner.dev.pondus.attachments.CommonDataAttachment;
import dinner.dev.pondus.network.C2SModPacket;
import dinner.dev.pondus.network.S2CModPacket;
import dinner.dev.pondus.platform.services.IPlatformHelper;
import dinner.dev.pondus.util.EntityGravityData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isClient() {
        return net.fabricmc.api.EnvType.CLIENT == net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType();
    }

    @Override
    public <MSG extends S2CModPacket> void registerClientPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(type, (packet, context) -> {
            packet.handleClient();
        });
    }

    @Override
    public <MSG extends C2SModPacket> void registerServerPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        // For client-to-server packets, we register on the client side
        // This is typically done in the client entry point
    }

    @Override
    public void sendToClient(S2CModPacket msg, ServerPlayer player) {
        ServerPlayNetworking.send(player, msg);
    }

    @Override
    public void sendToServer(C2SModPacket msg) {
        // Client side only - send to server
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(msg);
    }

    @Override
    public void sendToTracking(S2CModPacket msg, Entity entity, boolean includeSelf) {
        java.util.Collection<ServerPlayer> players = new java.util.LinkedHashSet<>(PlayerLookup.tracking(entity));
        if (includeSelf && entity instanceof ServerPlayer self) {
            players.add(self);
        }
        for (ServerPlayer player : players) {
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
        CommonEvents.handleGravity(entity, entityGravityData);
    }
}
