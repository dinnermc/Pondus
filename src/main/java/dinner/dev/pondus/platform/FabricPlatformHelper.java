package dinner.dev.pondus.platform;

import dinner.dev.pondus.attachments.CommonDataAttachment;
import dinner.dev.pondus.network.C2SModPacket;
import dinner.dev.pondus.network.PacketHandlerFabric;
import dinner.dev.pondus.network.S2CModPacket;
import dinner.dev.pondus.util.EntityGravityData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

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
        ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) -> {
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
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            PlayerLookup.tracking(entity, includeSelf).forEach(player -> {
                ServerPlayNetworking.send(player, msg);
            });
        }
    }

    @Override
    public <T> void registerDataAttachment(CommonDataAttachment<T> attachment) {
        AttachmentType.Builder<T> builder = AttachmentType.builder(attachment.getDefaultValueSupplier());
        if (attachment.getCodec() != null) {
            builder.copyOnDeath(attachment.isCopyOnDeath());
            builder.save(attachment.getCodec());
        }
        if (attachment.canSync()) {
            builder.syncWith(attachment.getStreamCodec());
        }
        
        AttachmentType<T> type = builder.build();
        Registry.register(BuiltInRegistries.ATTACHMENT_TYPE, attachment.getName(), type);
        attachment.setAttachment(type);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    @Nullable
    public <T> T getAttachedValue(Object object, CommonDataAttachment<T> attachment) {
        AttachmentType<T> type = (AttachmentType<T>) attachment.getAttachment();
        if (object instanceof AttachmentTarget attachmentTarget) {
            return attachmentTarget.getData(type);
        } else {
            throw new IllegalStateException("Cannot attach data to " + object);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> void setAttachedValue(Object object, CommonDataAttachment<T> attachment, @Nullable T value) {
        AttachmentType<T> type = (AttachmentType<T>) attachment.getAttachment();
        if (object instanceof AttachmentTarget attachmentTarget) {
            if (value == null) {
                attachmentTarget.removeData(type);
            } else {
                attachmentTarget.setData(type, value);
            }
        } else {
            throw new IllegalStateException("Cannot attach data to " + object);
        }
    }

    @Override
    public void postEvent(Entity entity, EntityGravityData entityGravityData) {
        CommonEvents.handleGravity(entity, entityGravityData);
    }
}