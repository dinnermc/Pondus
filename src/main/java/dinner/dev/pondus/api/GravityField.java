package dinner.dev.pondus.api;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The gravity contribution returned by a registered {@link GravitySource}.
 *
 * @param gravityVector normalized world-space direction of gravity
 * @param strengthMultiplier multiplier applied to the entity's gravity strength (or absolute field strength when {@code absoluteStrength} is {@code true})
 * @param priority priority used when choosing the effective gravity direction; equal-priority fields combine vectorially
 * @param rotationParameters optional rotation behavior; {@code null} uses Pondus defaults
 * @param absoluteStrength when {@code true}, {@code strengthMultiplier} represents an absolute local gravity strength (independent of ambient dimension gravity, so planets and ship gravity work in zero-G space)
 */
public record GravityField(
    @NotNull Vec3 gravityVector,
    double strengthMultiplier,
    int priority,
    @Nullable RotationParameters rotationParameters,
    boolean absoluteStrength
) {
    private static final RotationParameters PLANETARY_ROTATION = new RotationParameters(false, false, 320);

    public GravityField {
        gravityVector = normalizeGravityVector(gravityVector);
        if (!Double.isFinite(strengthMultiplier)) {
            throw new IllegalArgumentException("Gravity field strength must be finite.");
        }
    }

    /** Compatibility constructor for multiplicative vector gravity fields. */
    public GravityField(
        @NotNull Vec3 gravityVector,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters
    ) {
        this(gravityVector, strengthMultiplier, priority, rotationParameters, false);
    }

    /** Compatibility constructor for multiplicative cardinal gravity fields. */
    public GravityField(
        @NotNull Direction direction,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters
    ) {
        this(direction, strengthMultiplier, priority, rotationParameters, false);
    }

    /** Constructor for cardinal gravity fields with explicit strength mode. */
    public GravityField(
        @NotNull Direction direction,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters,
        boolean absoluteStrength
    ) {
        this(Vec3.atLowerCornerOf(Objects.requireNonNull(direction, "direction").getNormal()),
            strengthMultiplier, priority, rotationParameters, absoluteStrength);
    }

    /**
     * Creates a field that only scales gravity strength without overriding the entity's
     * gravity direction (for low-gravity armor, heavy trinkets, damping fields, etc.).
     */
    public static GravityField strengthOnly(double strengthMultiplier) {
        return new GravityField(Direction.DOWN, strengthMultiplier, GravityModifier.DEFAULT_PRIORITY, null, false);
    }

    /** Creates a cardinal gravity field with default rotation parameters. */
    public static GravityField ofDirection(@NotNull Direction direction, double strengthMultiplier, int priority) {
        return new GravityField(direction, strengthMultiplier, priority, null, false);
    }

    /** Creates a continuous-vector gravity field with default rotation parameters. */
    public static GravityField ofVector(@NotNull Vec3 gravityVector, double strengthMultiplier, int priority) {
        return new GravityField(gravityVector, strengthMultiplier, priority, null, false);
    }

    /**
     * Creates an absolute-strength local gravity field (e.g. artificial ship gravity or planetary gravity)
     * that works even in a zero-G space dimension where ambient dimension gravity is {@code 0.0}.
     */
    public static GravityField absolute(
        @NotNull Vec3 gravityVector,
        double gravityStrength,
        int priority,
        @Nullable RotationParameters rotationParameters
    ) {
        return new GravityField(gravityVector, gravityStrength, priority, rotationParameters, true);
    }

    /**
     * Convenience factory for planetary/celestial gravity bodies (planets, moons, asteroids).
     * Uses {@link GravityModifier#ZONE_PRIORITY}, absolute strength (works in zero-G space),
     * and smooth world-velocity-preserving rotation. Multiple overlapping planetary sources
     * at the same priority automatically combine vectorially (forming natural Lagrange points).
     */
    public static GravityField planetary(@NotNull Vec3 gravityVector, double gravityStrength) {
        return new GravityField(
            gravityVector, gravityStrength, GravityModifier.ZONE_PRIORITY, PLANETARY_ROTATION, true
        );
    }

    /** Returns the closest cardinal direction for legacy consumers. */
    public Direction direction() {
        return Direction.getNearest(gravityVector.x, gravityVector.y, gravityVector.z);
    }

    static Vec3 normalizeGravityVector(Vec3 vector) {
        Objects.requireNonNull(vector, "gravityVector");
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)
            || vector.lengthSqr() < 1.0E-12) {
            throw new IllegalArgumentException("Gravity vector must be finite and non-zero.");
        }
        return vector.scale(1.0 / Math.sqrt(vector.lengthSqr()));
    }
}
