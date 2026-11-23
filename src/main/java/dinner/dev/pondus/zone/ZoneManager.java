package dinner.dev.pondus.zone;

import com.google.common.collect.Maps;
import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.api.GravityField;
import dinner.dev.pondus.api.GravityFieldShapes;
import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.GravitySource;
import dinner.dev.pondus.api.RotationParameters;
import dinner.dev.pondus.network.S2CZoneVisualizationPacket;
import dinner.dev.pondus.platform.Services;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Manages persistent gravity zones and exposes their combined influence as a gravity source.
 */
public class ZoneManager implements GravitySource {
    private static final ZoneManager INSTANCE = new ZoneManager();

    private static final int ZONE_ROTATION_TIME_MS = 500;
    private static final int CENTER_ZONE_ROTATION_TIME_MS = 320;
    private static final double OPPOSITE_DIRECTION_HYSTERESIS = 0.2;
    private static final double CORE_DEADZONE_RADIUS = 0.35;
    private static final double CORE_FADE_RADIUS = 1.0;

    public static ZoneManager getInstance() {
        return INSTANCE;
    }

    private ZoneManager() {}

    @Override
    public ResourceLocation id() {
        return Pondus.id("zones");
    }

    /** Add or replace a zone. */
    public void addZone(Zone zone, Level level) {
        getData(level).addZone(zone);
        syncVisualization(level);
    }

    /** Remove zone by id string. Its modifier is stripped on the next level tick. */
    public void removeZone(String id, Level level) {
        if (getData(level).removeZone(id)) {
            syncVisualization(level);
        }
    }

    /** Enable or disable a zone by id, persisting the change and updating clients. */
    public boolean setZoneEnabled(String id, boolean enabled, Level level) {
        if (!getData(level).setZoneEnabled(id, enabled)) {
            return false;
        }
        syncVisualization(level);
        return true;
    }

    /** Get zone by id string, or null if not found. */
    public Zone getZone(String id, Level level) {
        return getData(level).getZones().get(id);
    }

    /** Returns a copy of the zone map. */
    public Map<String, Zone> getZones(Level level) {
        return Maps.newHashMap(getData(level).getZones());
    }

    public double getZoneInfluence(Entity entity, Zone zone) {
        if (!zone.isEnabled()) {
            return 0;
        }

        Vec3 point = entity.getBoundingBox().getCenter();
        return switch (zone.getShape()) {
            case BOX -> GravityFieldShapes.boxInfluence(
                point, zone.getAabb(), zone.getTransitionDistance()
            );
            case SPHERE -> GravityFieldShapes.sphereInfluence(
                point, zone.getCenter(), zone.getRadius(), zone.getTransitionDistance()
            );
            case CYLINDER -> GravityFieldShapes.cylinderInfluence(
                point, zone.getCenter(), zone.getAxis(), zone.getRadius(),
                zone.getHalfHeight(), zone.getTransitionDistance()
            );
        };
    }

    @Override
    public GravityField getGravityField(Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        var zones = getData(serverLevel).getZones();
        if (zones.isEmpty()) {
            return null;
        }

        double ambientStrength = PondusAPI.getBaseGravityStrength(entity)
            * PondusAPI.getDimensionGravityStrength(serverLevel);
        Direction fallbackDirection = PondusAPI.getBaseGravityDirection(entity);
        String fieldSourceId = "field:" + id();
        Direction currentZoneDirection = PondusAPI.getModifierBySource(entity, fieldSourceId)
            .map(GravityModifier::direction)
            .orElse(fallbackDirection);
        Vec3 baseGravity = Vec3.atLowerCornerOf(fallbackDirection.getNormal()).scale(ambientStrength);
        Vec3 zoneGravity = Vec3.ZERO;
        double totalInfluence = 0.0;
        double totalZoneForceMagnitude = 0.0;
        boolean hasZoneInfluence = false;
        boolean hasCenterZone = false;
        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        for (Zone zone : zones.values()) {
            double influence = getZoneInfluence(entity, zone);
            if (influence <= 0) {
                continue;
            }
            hasZoneInfluence = true;
            totalInfluence += influence;
            Vec3 zoneDirection;
            double effectiveStrength = zone.getStrength();
            if (zone.hasCenterGravity()) {
                hasCenterZone = true;
                Vec3 center = zone.getGravityCenter();
                zoneDirection = GravityFieldShapes.centerVector(entityCenter, center, CORE_DEADZONE_RADIUS);
                if (zoneDirection.lengthSqr() < 1.0E-8) {
                    zoneDirection = Vec3.atLowerCornerOf(currentZoneDirection.getNormal());
                }
                effectiveStrength *= GravityFieldShapes.coreScale(entityCenter, center, CORE_FADE_RADIUS);
            } else {
                zoneDirection = zone.getGravityVectorAt(entityCenter);
                if (zoneDirection.lengthSqr() < 1.0E-8) {
                    zoneDirection = Vec3.atLowerCornerOf(fallbackDirection.getNormal());
                }
            }
            Vec3 zoneForce = zoneDirection.scale(effectiveStrength * influence);
            zoneGravity = zoneGravity.add(zoneForce);
            totalZoneForceMagnitude += zoneForce.length();
        }

        if (!hasZoneInfluence) {
            return null;
        }

        // Normalize overlapping zone weights so base + zone influences always form a partition of unity.
        double baseWeight = Math.max(0.0, 1.0 - totalInfluence);
        double zoneScale = totalInfluence > 1.0 ? (1.0 / totalInfluence) : 1.0;
        Vec3 scaledZoneGravity = zoneGravity.scale(zoneScale);
        Vec3 combinedGravity = baseGravity.scale(baseWeight).add(scaledZoneGravity);
        double totalForceMagnitude = baseGravity.length() * baseWeight + totalZoneForceMagnitude * zoneScale;
        double combinedStrength = combinedGravity.length();
        Vec3 gravityVector = combinedStrength > 1.0E-6
            ? combinedGravity.scale(1.0 / combinedStrength)
            : Vec3.atLowerCornerOf(currentZoneDirection.getNormal());
        if (!Double.isFinite(gravityVector.x) || !Double.isFinite(gravityVector.y)
            || !Double.isFinite(gravityVector.z) || gravityVector.lengthSqr() < 1.0E-12) {
            gravityVector = Vec3.atLowerCornerOf(fallbackDirection.getNormal());
        }
        Direction combinedDirection = Direction.getNearest(gravityVector.x, gravityVector.y, gravityVector.z);
        if (combinedDirection == currentZoneDirection.getOpposite()
            && combinedStrength < totalForceMagnitude * OPPOSITE_DIRECTION_HYSTERESIS) {
            combinedDirection = currentZoneDirection;
            gravityVector = Vec3.atLowerCornerOf(combinedDirection.getNormal());
        }
        RotationParameters defaultRotation = RotationParameters.getDefault();
        int rotationTimeMs = hasCenterZone
            ? Math.min(defaultRotation.rotationTimeMS(), CENTER_ZONE_ROTATION_TIME_MS)
            : Math.max(defaultRotation.rotationTimeMS(), ZONE_ROTATION_TIME_MS);
        RotationParameters rotation = new RotationParameters(
            false,
            false,
            rotationTimeMs
        );
        return GravityField.absolute(
            gravityVector,
            combinedStrength,
            GravityModifier.ZONE_PRIORITY,
            rotation
        );
    }

    private static ZoneSavedData getData(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Gravity zones can only be accessed on the server.");
        }
        return serverLevel.getDataStorage()
            .computeIfAbsent(ZoneSavedData.FACTORY, ZoneSavedData.DATA_NAME);
    }

    public void syncVisualization(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        S2CZoneVisualizationPacket packet =
            S2CZoneVisualizationPacket.fromZones(serverLevel.dimension(), getZones(serverLevel).values());
        Services.PLATFORM.sendToLevel(packet, serverLevel);
    }
}
