package dinner.dev.pondus.network;

import dinner.dev.pondus.ClientPacketHandler;
import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.zone.Zone;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record S2CZoneVisualizationPacket(ResourceKey<Level> dimension, List<ZoneVisualData> zones) implements S2CModPacket {
    public static final Type<S2CZoneVisualizationPacket> TYPE =
        new Type<>(Pondus.id("zone_visualization"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CZoneVisualizationPacket> STREAM_CODEC =
        StreamCodec.of(S2CZoneVisualizationPacket::encode, S2CZoneVisualizationPacket::decode);

    public S2CZoneVisualizationPacket {
        zones = List.copyOf(zones);
    }

    public static S2CZoneVisualizationPacket fromZones(ResourceKey<Level> dimension, Iterable<Zone> zones) {
        List<ZoneVisualData> data = new ArrayList<>();
        for (Zone zone : zones) {
            AABB bounds = zone.getAabb();
            Vec3 center = zone.getGravityCenter();
            data.add(new ZoneVisualData(
                zone.getId(),
                zone.getShape().ordinal(),
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                center == null ? 0 : center.x,
                center == null ? 0 : center.y,
                center == null ? 0 : center.z,
                zone.getAxis().ordinal(),
                zone.getRadius(),
                zone.getHalfHeight(),
                zone.getTransitionDistance(),
                zone.getDirection().ordinal(),
                zone.hasCenterGravity(),
                zone.isEnabled()
            ));
        }
        return new S2CZoneVisualizationPacket(dimension, data);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, S2CZoneVisualizationPacket packet) {
        buffer.writeResourceKey(packet.dimension);
        buffer.writeVarInt(packet.zones.size());
        for (ZoneVisualData zone : packet.zones) {
            buffer.writeUtf(zone.id, 256);
            buffer.writeVarInt(zone.shape);
            buffer.writeDouble(zone.minX);
            buffer.writeDouble(zone.minY);
            buffer.writeDouble(zone.minZ);
            buffer.writeDouble(zone.maxX);
            buffer.writeDouble(zone.maxY);
            buffer.writeDouble(zone.maxZ);
            buffer.writeDouble(zone.centerX);
            buffer.writeDouble(zone.centerY);
            buffer.writeDouble(zone.centerZ);
            buffer.writeVarInt(zone.axis);
            buffer.writeDouble(zone.radius);
            buffer.writeDouble(zone.halfHeight);
            buffer.writeDouble(zone.transitionDistance);
            buffer.writeVarInt(zone.direction);
            buffer.writeBoolean(zone.centerGravity);
            buffer.writeBoolean(zone.enabled);
        }
    }

    private static S2CZoneVisualizationPacket decode(RegistryFriendlyByteBuf buffer) {
        ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
        int size = buffer.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid gravity zone count: " + size);
        }

        List<ZoneVisualData> zones = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zones.add(new ZoneVisualData(
                buffer.readUtf(256),
                buffer.readVarInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
            ));
        }
        return new S2CZoneVisualizationPacket(dimension, zones);
    }

    @Override
    public void handleClient() {
        ClientPacketHandler.handle(this);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ZoneVisualData(
        String id,
        int shape,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double centerX,
        double centerY,
        double centerZ,
        int axis,
        double radius,
        double halfHeight,
        double transitionDistance,
        int direction,
        boolean centerGravity,
        boolean enabled
    ) {
        public Zone.Shape getShape() {
            return Zone.Shape.values()[shape];
        }

        public Direction getDirection() {
            return Direction.values()[direction];
        }

        public Direction.Axis getAxis() {
            return Direction.Axis.values()[axis];
        }

        public AABB getBounds() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public Vec3 getCenter() {
            return new Vec3(centerX, centerY, centerZ);
        }
    }
}
