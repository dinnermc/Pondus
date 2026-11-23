package dinner.dev.pondus.api;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Geometry helpers for calculating a smooth gravity-field influence.
 *
 * <p>Each shape has full influence inside its bounds, then smoothly fades to zero
 * over the requested distance outside the shape.</p>
 */
public final class GravityFieldShapes {
    private GravityFieldShapes() {}

    /**
     * Calculates influence relative to an axis-aligned box.
     *
     * @param point position to test, typically the entity bounding-box center
     * @param bounds field bounds
     * @param transitionDistance distance outside the box over which influence fades to zero
     * @return influence from {@code 0.0} (none) to {@code 1.0} (full)
     */
    public static double boxInfluence(Vec3 point, AABB bounds, double transitionDistance) {
        requirePoint(point);
        Objects.requireNonNull(bounds, "bounds");
        requireNonNegativeFinite(transitionDistance, "transitionDistance");

        double dx = Math.max(Math.max(bounds.minX - point.x, 0), point.x - bounds.maxX);
        double dy = Math.max(Math.max(bounds.minY - point.y, 0), point.y - bounds.maxY);
        double dz = Math.max(Math.max(bounds.minZ - point.z, 0), point.z - bounds.maxZ);
        return falloff(Math.sqrt(dx * dx + dy * dy + dz * dz), transitionDistance);
    }

    /**
     * Calculates influence relative to a sphere.
     *
     * @param point position to test, typically the entity bounding-box center
     * @param center sphere center
     * @param radius sphere radius
     * @param transitionDistance distance outside the sphere over which influence fades to zero
     * @return influence from {@code 0.0} (none) to {@code 1.0} (full)
     */
    public static double sphereInfluence(
        Vec3 point, Vec3 center, double radius, double transitionDistance
    ) {
        requirePoint(point);
        requirePoint(center);
        requireNonNegativeFinite(radius, "radius");
        requireNonNegativeFinite(transitionDistance, "transitionDistance");

        return falloff(Math.max(0, point.distanceTo(center) - radius), transitionDistance);
    }

    /**
     * Calculates influence relative to a finite, capped cylinder.
     *
     * @param point position to test, typically the entity bounding-box center
     * @param center cylinder center
     * @param axis cylinder's longitudinal axis
     * @param radius cylinder radius
     * @param halfHeight half of the cylinder's length along its axis
     * @param transitionDistance distance outside the cylinder over which influence fades to zero
     * @return influence from {@code 0.0} (none) to {@code 1.0} (full)
     */
    public static double cylinderInfluence(
        Vec3 point,
        Vec3 center,
        Direction.Axis axis,
        double radius,
        double halfHeight,
        double transitionDistance
    ) {
        requirePoint(point);
        requirePoint(center);
        Objects.requireNonNull(axis, "axis");
        requireNonNegativeFinite(radius, "radius");
        requireNonNegativeFinite(halfHeight, "halfHeight");
        requireNonNegativeFinite(transitionDistance, "transitionDistance");

        double axialOffset = switch (axis) {
            case X -> point.x - center.x;
            case Y -> point.y - center.y;
            case Z -> point.z - center.z;
        };
        double dx = point.x - center.x;
        double dy = point.y - center.y;
        double dz = point.z - center.z;
        double radialDistance = switch (axis) {
            case X -> Math.hypot(dy, dz);
            case Y -> Math.hypot(dx, dz);
            case Z -> Math.hypot(dx, dy);
        };
        double outsideRadial = Math.max(0, radialDistance - radius);
        double outsideAxial = Math.max(0, Math.abs(axialOffset) - halfHeight);
        return falloff(Math.hypot(outsideRadial, outsideAxial), transitionDistance);
    }

    /**
     * Computes a unit vector pointing from {@code point} toward {@code center},
     * returning {@link Vec3#ZERO} inside the inner {@code coreDeadzone} radius
     * so entities passing through the exact center do not violently spin.
     */
    public static Vec3 centerVector(Vec3 point, Vec3 center, double coreDeadzone) {
        requirePoint(point);
        requirePoint(center);
        requireNonNegativeFinite(coreDeadzone, "coreDeadzone");

        Vec3 towardCenter = center.subtract(point);
        double distSq = towardCenter.lengthSqr();
        double minDist = Math.max(1.0E-4, coreDeadzone);
        if (distSq <= minDist * minDist) {
            return Vec3.ZERO;
        }
        return towardCenter.scale(1.0 / Math.sqrt(distSq));
    }

    /**
     * Smoothly scales strength from {@code 0.0} at the center to {@code 1.0} at
     * {@code coreFadeRadius} so entities near the geometric center feel gentle,
     * stable gravity instead of abrupt 180-degree flips.
     */
    public static double coreScale(Vec3 point, Vec3 center, double coreFadeRadius) {
        requirePoint(point);
        requirePoint(center);
        requireNonNegativeFinite(coreFadeRadius, "coreFadeRadius");
        if (coreFadeRadius == 0.0) {
            return 1.0;
        }
        double dist = point.distanceTo(center);
        if (dist >= coreFadeRadius) {
            return 1.0;
        }
        double t = dist / coreFadeRadius;
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Computes Newtonian inverse-square planetary gravity strength ({@code 1 / r^2})
     * with a smooth outer cutoff at {@code maxRange} and a safe surface/core profile
     * (constant {@code surfaceGravity} at the surface, smoothly fading to {@code 0} inside
     * the inner core so there is no singularity at {@code r = 0}).
     *
     * @param distance distance from the planet's center to the entity
     * @param surfaceRadius radius of the planet's surface (where gravity equals {@code surfaceGravity})
     * @param surfaceGravity gravity strength at the planet surface (e.g. {@code 1.0} for Earth, {@code 0.4} for a moon)
     * @param maxRange outer distance beyond which the planet's influence smoothly reaches {@code 0.0}
     * @return effective local gravity strength, or {@code 0.0} beyond {@code maxRange}
     */
    public static double inverseSquareStrength(
        double distance,
        double surfaceRadius,
        double surfaceGravity,
        double maxRange
    ) {
        requireNonNegativeFinite(distance, "distance");
        requireNonNegativeFinite(surfaceRadius, "surfaceRadius");
        requireNonNegativeFinite(surfaceGravity, "surfaceGravity");
        requireNonNegativeFinite(maxRange, "maxRange");
        if (surfaceRadius <= 0.0 || maxRange <= surfaceRadius || distance >= maxRange) {
            return 0.0;
        }
        if (distance <= surfaceRadius) {
            double coreRadius = Math.min(1.5, surfaceRadius * 0.25);
            if (coreRadius > 0.0 && distance < coreRadius) {
                double t = distance / coreRadius;
                return surfaceGravity * (t * t * (3.0 - 2.0 * t));
            }
            return surfaceGravity;
        }
        double ratio = surfaceRadius / distance;
        double invSq = surfaceGravity * ratio * ratio;
        // Smooth window near maxRange so gravity reaches 0 cleanly without a hard edge
        double edgeStart = surfaceRadius + (maxRange - surfaceRadius) * 0.75;
        if (distance > edgeStart) {
            double edgeProgress = (distance - edgeStart) / (maxRange - edgeStart);
            double edgeFade = 1.0 - edgeProgress * edgeProgress * (3.0 - 2.0 * edgeProgress);
            invSq *= edgeFade;
        }
        return invSq;
    }

    private static double falloff(double distance, double transitionDistance) {
        if (distance == 0) {
            return 1.0;
        }
        if (transitionDistance == 0 || distance >= transitionDistance) {
            return 0.0;
        }

        double progress = distance / transitionDistance;
        return 1.0 - progress * progress * (3.0 - 2.0 * progress);
    }

    private static void requirePoint(Vec3 point) {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            throw new IllegalArgumentException("Point coordinates must be finite.");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
