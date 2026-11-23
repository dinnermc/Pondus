package dinner.dev.pondus.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Persistent gravity field geometry and its gravity contribution.
 */
public class Zone {
    public enum Shape {
        BOX,
        SPHERE,
        CYLINDER
    }

    private final String id;
    private final UUID modifierId;
    private final Shape shape;
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final Vec3 center;
    private final Direction.Axis axis;
    private final double radius;
    private final double halfHeight;
    private final double transitionDistance;
    private final Direction direction;
    private final boolean centerGravity;
    private final double strength;
    private boolean enabled = true;

    public Zone(
        String id,
        BlockPos pos1,
        BlockPos pos2,
        Direction direction,
        double strength
    ) {
        this(id, Shape.BOX, pos1, pos2, null, Direction.Axis.Y, 0, 0, 1.0, direction, false, strength);
    }

    private Zone(
        String id,
        Shape shape,
        BlockPos pos1,
        BlockPos pos2,
        Vec3 center,
        Direction.Axis axis,
        double radius,
        double halfHeight,
        double transitionDistance,
        Direction direction,
        boolean centerGravity,
        double strength
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Zone id cannot be blank.");
        }
        if (shape == null || direction == null || axis == null) {
            throw new IllegalArgumentException("Zone shape, axis, and direction are required.");
        }
        requireNonNegativeFinite(strength, "strength");
        requireNonNegativeFinite(transitionDistance, "transitionDistance");
        if (shape == Shape.BOX && (pos1 == null || pos2 == null)) {
            throw new IllegalArgumentException("Box zones require two positions.");
        }
        if (shape != Shape.BOX && center == null) {
            throw new IllegalArgumentException("Round zones require a center.");
        }
        if (shape != Shape.BOX) {
            requireNonNegativeFinite(radius, "radius");
        }
        if (shape == Shape.CYLINDER) {
            requireNonNegativeFinite(halfHeight, "halfHeight");
        }
        this.id = id;
        this.modifierId = UUID.nameUUIDFromBytes(("zone:" + id).getBytes());
        this.shape = shape;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.center = center;
        this.axis = axis;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.transitionDistance = transitionDistance;
        this.direction = direction;
        this.centerGravity = centerGravity;
        this.strength = strength;
    }

    public static Zone box(
        String id, BlockPos pos1, BlockPos pos2, Direction direction, double strength, double transitionDistance
    ) {
        return box(id, pos1, pos2, direction, false, strength, transitionDistance);
    }

    public static Zone box(
        String id,
        BlockPos pos1,
        BlockPos pos2,
        Direction direction,
        boolean centerGravity,
        double strength,
        double transitionDistance
    ) {
        return new Zone(
            id, Shape.BOX, pos1, pos2, null, Direction.Axis.Y, 0, 0,
            transitionDistance, direction, centerGravity, strength
        );
    }

    public static Zone sphere(
        String id, BlockPos center, double radius, Direction direction, double strength, double transitionDistance
    ) {
        return sphere(id, center, radius, direction, false, strength, transitionDistance);
    }

    public static Zone sphere(
        String id,
        BlockPos center,
        double radius,
        Direction direction,
        boolean centerGravity,
        double strength,
        double transitionDistance
    ) {
        return sphere(id, Vec3.atCenterOf(center), radius, direction, centerGravity, strength, transitionDistance);
    }

    public static Zone sphere(
        String id,
        Vec3 center,
        double radius,
        Direction direction,
        boolean centerGravity,
        double strength,
        double transitionDistance
    ) {
        return new Zone(
            id, Shape.SPHERE, null, null, center, Direction.Axis.Y, radius, 0,
            transitionDistance, direction, centerGravity, strength
        );
    }

    public static Zone cylinder(
        String id,
        BlockPos center,
        Direction.Axis axis,
        double radius,
        double height,
        Direction direction,
        double strength,
        double transitionDistance
    ) {
        return cylinder(id, center, axis, radius, height, direction, false, strength, transitionDistance);
    }

    public static Zone cylinder(
        String id,
        BlockPos center,
        Direction.Axis axis,
        double radius,
        double height,
        Direction direction,
        boolean centerGravity,
        double strength,
        double transitionDistance
    ) {
        return cylinder(id, Vec3.atCenterOf(center), axis, radius, height, direction, centerGravity, strength, transitionDistance);
    }

    public static Zone cylinder(
        String id,
        Vec3 center,
        Direction.Axis axis,
        double radius,
        double height,
        Direction direction,
        boolean centerGravity,
        double strength,
        double transitionDistance
    ) {
        requireNonNegativeFinite(height, "height");
        return new Zone(
            id, Shape.CYLINDER, null, null, center, axis, radius, height / 2.0,
            transitionDistance, direction, centerGravity, strength
        );
    }

    static Zone load(
        String id,
        Shape shape,
        BlockPos pos1,
        BlockPos pos2,
        Vec3 center,
        Direction.Axis axis,
        double radius,
        double halfHeight,
        double transitionDistance,
        Direction direction,
        boolean centerGravity,
        double strength
    ) {
        return new Zone(
            id, shape, pos1, pos2, center, axis, radius, halfHeight,
            transitionDistance, direction, centerGravity, strength
        );
    }

    public String getId() {
        return id;
    }

    public UUID getModifierId() {
        return modifierId;
    }

    public Shape getShape() {
        return shape;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public Vec3 getCenter() {
        return center;
    }

    public Vec3 getGravityCenter() {
        return shape == Shape.BOX ? getAabb().getCenter() : center;
    }

    public Direction.Axis getAxis() {
        return axis;
    }

    public double getRadius() {
        return radius;
    }

    public double getHalfHeight() {
        return halfHeight;
    }

    public double getTransitionDistance() {
        return transitionDistance;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean hasCenterGravity() {
        return centerGravity;
    }

    public Direction getDirectionAt(Vec3 point, Direction fallback) {
        Vec3 gravityVector = getGravityVectorAt(point);
        if (gravityVector.lengthSqr() < 1.0E-8) {
            return fallback;
        }
        return Direction.getNearest(gravityVector.x, gravityVector.y, gravityVector.z);
    }

    public Vec3 getGravityVectorAt(Vec3 point) {
        if (!centerGravity) {
            return Vec3.atLowerCornerOf(direction.getNormal());
        }

        Vec3 towardCenter = getGravityCenter().subtract(point);
        if (towardCenter.lengthSqr() < 1.0E-8) {
            return Vec3.ZERO;
        }
        return towardCenter.normalize();
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

    public boolean contains(Entity entity) {
        Vec3 point = entity.getBoundingBox().getCenter();
        return switch (shape) {
            case BOX -> {
                AABB bounds = getAabb();
                yield point.x >= bounds.minX && point.x <= bounds.maxX
                    && point.y >= bounds.minY && point.y <= bounds.maxY
                    && point.z >= bounds.minZ && point.z <= bounds.maxZ;
            }
            case SPHERE -> point.distanceTo(center) <= radius;
            case CYLINDER -> {
                double axialOffset = axis.choose(
                    point.x - center.x,
                    point.y - center.y,
                    point.z - center.z
                );
                double dx = point.x - center.x;
                double dy = point.y - center.y;
                double dz = point.z - center.z;
                double radialDistance = switch (axis) {
                    case X -> Math.hypot(dy, dz);
                    case Y -> Math.hypot(dx, dz);
                    case Z -> Math.hypot(dx, dy);
                };
                yield Math.abs(axialOffset) <= halfHeight && radialDistance <= radius;
            }
        };
    }

    /** Returns the core bounds of the field, excluding its transition falloff. */
    public AABB getAabb() {
        if (shape == Shape.BOX) {
            int minX = Math.min(pos1.getX(), pos2.getX());
            int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            int minY = Math.min(pos1.getY(), pos2.getY());
            int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            int minZ = Math.min(pos1.getZ(), pos2.getZ());
            int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        if (shape == Shape.SPHERE) {
            return new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius
            );
        }

        double xRadius = axis == Direction.Axis.X ? halfHeight : radius;
        double yRadius = axis == Direction.Axis.Y ? halfHeight : radius;
        double zRadius = axis == Direction.Axis.Z ? halfHeight : radius;
        return new AABB(
            center.x - xRadius, center.y - yRadius, center.z - zRadius,
            center.x + xRadius, center.y + yRadius, center.z + zRadius
        );
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
