package dinner.dev.pondus.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

final class ZoneSavedData extends SavedData {
    static final String DATA_NAME = "pondus_gravity_zones";
    static final Factory<ZoneSavedData> FACTORY = new Factory<>(ZoneSavedData::new, ZoneSavedData::load);

    private static final String ZONES_TAG = "zones";
    private static final String ID_TAG = "id";
    private static final String POS1_TAG = "pos1";
    private static final String POS2_TAG = "pos2";
    private static final String SHAPE_TAG = "shape";
    private static final String CENTER_X_TAG = "center_x";
    private static final String CENTER_Y_TAG = "center_y";
    private static final String CENTER_Z_TAG = "center_z";
    private static final String AXIS_TAG = "axis";
    private static final String RADIUS_TAG = "radius";
    private static final String HALF_HEIGHT_TAG = "half_height";
    private static final String TRANSITION_TAG = "transition";
    private static final String DIRECTION_TAG = "direction";
    private static final String CENTER_GRAVITY_TAG = "center_gravity";
    private static final String STRENGTH_TAG = "strength";
    private static final String ENABLED_TAG = "enabled";

    private final Map<String, Zone> zones = new HashMap<>();

    private ZoneSavedData() {}

    static ZoneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ZoneSavedData data = new ZoneSavedData();
        ListTag zonesTag = tag.getList(ZONES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesTag.size(); i++) {
            CompoundTag zoneTag = zonesTag.getCompound(i);
            Direction direction = Direction.byName(zoneTag.getString(DIRECTION_TAG));
            if (direction == null) {
                continue;
            }

            Zone.Shape shape = Zone.Shape.BOX;
            if (zoneTag.contains(SHAPE_TAG)) {
                try {
                    shape = Zone.Shape.valueOf(zoneTag.getString(SHAPE_TAG));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
            }
            Direction.Axis axis = switch (zoneTag.getString(AXIS_TAG)) {
                case "x" -> Direction.Axis.X;
                case "z" -> Direction.Axis.Z;
                default -> Direction.Axis.Y;
            };
            Vec3 center = new Vec3(
                zoneTag.getDouble(CENTER_X_TAG),
                zoneTag.getDouble(CENTER_Y_TAG),
                zoneTag.getDouble(CENTER_Z_TAG)
            );
            Zone zone;
            try {
                if (shape == Zone.Shape.BOX && zoneTag.contains(POS1_TAG) && zoneTag.contains(POS2_TAG)) {
                    zone = Zone.load(
                        zoneTag.getString(ID_TAG), shape,
                        BlockPos.of(zoneTag.getLong(POS1_TAG)), BlockPos.of(zoneTag.getLong(POS2_TAG)),
                        null, axis, 0, 0,
                        zoneTag.contains(TRANSITION_TAG) ? zoneTag.getDouble(TRANSITION_TAG) : 1.0,
                        direction, zoneTag.getBoolean(CENTER_GRAVITY_TAG), zoneTag.getDouble(STRENGTH_TAG)
                    );
                } else if (shape == Zone.Shape.SPHERE || shape == Zone.Shape.CYLINDER) {
                    zone = Zone.load(
                        zoneTag.getString(ID_TAG), shape, null, null, center, axis,
                        zoneTag.getDouble(RADIUS_TAG), zoneTag.getDouble(HALF_HEIGHT_TAG),
                        zoneTag.getDouble(TRANSITION_TAG), direction,
                        zoneTag.getBoolean(CENTER_GRAVITY_TAG), zoneTag.getDouble(STRENGTH_TAG)
                    );
                } else {
                    continue;
                }
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            zone.setEnabled(!zoneTag.contains(ENABLED_TAG) || zoneTag.getBoolean(ENABLED_TAG));
            data.zones.put(zone.getId(), zone);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag zonesTag = new ListTag();
        for (Zone zone : zones.values()) {
            CompoundTag zoneTag = new CompoundTag();
            zoneTag.putString(ID_TAG, zone.getId());
            zoneTag.putString(SHAPE_TAG, zone.getShape().name());
            if (zone.getShape() == Zone.Shape.BOX) {
                zoneTag.putLong(POS1_TAG, zone.getPos1().asLong());
                zoneTag.putLong(POS2_TAG, zone.getPos2().asLong());
            } else {
                zoneTag.putDouble(CENTER_X_TAG, zone.getCenter().x);
                zoneTag.putDouble(CENTER_Y_TAG, zone.getCenter().y);
                zoneTag.putDouble(CENTER_Z_TAG, zone.getCenter().z);
                zoneTag.putDouble(RADIUS_TAG, zone.getRadius());
                zoneTag.putDouble(HALF_HEIGHT_TAG, zone.getHalfHeight());
                zoneTag.putString(AXIS_TAG, zone.getAxis().getName());
            }
            zoneTag.putDouble(TRANSITION_TAG, zone.getTransitionDistance());
            zoneTag.putString(DIRECTION_TAG, zone.getDirection().getName());
            zoneTag.putBoolean(CENTER_GRAVITY_TAG, zone.hasCenterGravity());
            zoneTag.putDouble(STRENGTH_TAG, zone.getStrength());
            zoneTag.putBoolean(ENABLED_TAG, zone.isEnabled());
            zonesTag.add(zoneTag);
        }
        tag.put(ZONES_TAG, zonesTag);
        return tag;
    }

    Map<String, Zone> getZones() {
        return zones;
    }

    void addZone(Zone zone) {
        zones.put(zone.getId(), zone);
        setDirty();
    }

    boolean removeZone(String id) {
        if (zones.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    boolean setZoneEnabled(String id, boolean enabled) {
        Zone zone = zones.get(id);
        if (zone == null) {
            return false;
        }
        if (zone.isEnabled() != enabled) {
            zone.setEnabled(enabled);
            setDirty();
        }
        return true;
    }
}
