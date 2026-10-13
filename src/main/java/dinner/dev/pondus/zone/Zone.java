package dinner.dev.pondus.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * Simple axis-aligned bounding box zone with a gravity direction and strength.
 */
public class Zone {
    private final String id;
    private final UUID modifierId;
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final net.minecraft.core.Direction direction;
    private final double strength; // amplifier for directional effect
    private boolean enabled = true;

    public Zone(String id, BlockPos pos1, BlockPos pos2, net.minecraft.core.Direction direction, double strength) {
        this.id = id;
        this.modifierId = UUID.nameUUIDFromBytes(("zone:" + id).getBytes());
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.direction = direction;
        this.strength = strength;
    }

    public String getId() {
        return id;
    }

    public UUID getModifierId() {
        return modifierId;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public net.minecraft.core.Direction getDirection() {
        return direction;
    }

    public double getStrength() {
        return strength;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns true if the given entity's position is inside this zone (inclusive). */
    public boolean contains(Entity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Returns an AABB covering the zone (useful for debugging). */
    public AABB getAabb() {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}