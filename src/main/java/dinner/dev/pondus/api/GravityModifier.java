package dinner.dev.pondus.api;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A gravity modifier from a specific source.
 * Each modifier has an ID, gravity vector, strength multiplier, priority, source identifier,
 * optional rotation parameters, and an optional duration in ticks ({@link #PERMANENT} for no expiry).
 */
public record GravityModifier(
    UUID id,
    @NotNull String sourceId,        // e.g. "command", "zone:spawn", "item:moon_boots", "effect:gravity_increase"
    @NotNull Vec3 gravityVector,
    double strengthMultiplier,        // multiplicative, or absolute local strength when absoluteStrength == true
    int priority,                     // higher = wins for gravity vector; equal top priority blends vectorially
    @Nullable RotationParameters rotationParameters,
    int durationTicks,                // -1 = permanent, >0 = remaining ticks on server
    boolean absoluteStrength          // true = sets local base gravity independent of dimension/base gravity
) {
    public static final int PERMANENT = -1;
    public static final int DEFAULT_PRIORITY = 0;
    public static final int COMMAND_PRIORITY = 100;
    public static final int ITEM_PRIORITY = 75;
    public static final int ZONE_PRIORITY = 50;
    public static final int EFFECT_PRIORITY = 25;
    public static final int VEHICLE_PRIORITY = 200;
    public static final int DIMENSION_PRIORITY = -100;

    public GravityModifier(
        UUID id,
        @NotNull String sourceId,
        @NotNull Vec3 gravityVector,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters
    ) {
        this(id, sourceId, gravityVector, strengthMultiplier, priority, rotationParameters, PERMANENT, false);
    }

    public GravityModifier(
        UUID id,
        @NotNull String sourceId,
        @NotNull Vec3 gravityVector,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters,
        int durationTicks
    ) {
        this(id, sourceId, gravityVector, strengthMultiplier, priority, rotationParameters, durationTicks, false);
    }

    public GravityModifier(
        UUID id,
        @NotNull String sourceId,
        @NotNull Direction direction,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters
    ) {
        this(id, sourceId, Vec3.atLowerCornerOf(direction.getNormal()), strengthMultiplier, priority, rotationParameters, PERMANENT, false);
    }

    public GravityModifier(
        UUID id,
        @NotNull String sourceId,
        @NotNull Direction direction,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters,
        int durationTicks
    ) {
        this(id, sourceId, Vec3.atLowerCornerOf(direction.getNormal()), strengthMultiplier, priority, rotationParameters, durationTicks, false);
    }

    public GravityModifier {
        gravityVector = GravityField.normalizeGravityVector(gravityVector);
        if (!Double.isFinite(strengthMultiplier)) {
            throw new IllegalArgumentException("Gravity modifier strength must be finite.");
        }
        if (durationTicks < PERMANENT) {
            throw new IllegalArgumentException("Gravity modifier durationTicks must be >= -1.");
        }
    }

    /** Returns whether this modifier automatically expires after a number of ticks. */
    public boolean isTimed() {
        return durationTicks >= 0;
    }

    /** Returns a copy with the given remaining duration in ticks (or {@link #PERMANENT}). */
    public GravityModifier withDuration(int durationTicks) {
        return new GravityModifier(id, sourceId, gravityVector, strengthMultiplier, priority, rotationParameters, durationTicks, absoluteStrength);
    }

    /** Returns a copy with {@code durationTicks} decremented by 1 (clamped at 0). */
    public GravityModifier tickDown() {
        if (durationTicks <= 0) {
            return this;
        }
        return withDuration(durationTicks - 1);
    }

    /** Returns the closest cardinal direction for legacy consumers. */
    public Direction direction() {
        return Direction.getNearest(gravityVector.x, gravityVector.y, gravityVector.z);
    }

    /**
     * Creates a modifier that only multiplies gravity strength without overriding
     * the entity's gravity direction (useful for moon boots, heavy armor, feather charms, etc.).
     */
    public static GravityModifier strengthOnly(UUID id, @NotNull String sourceId, double strengthMultiplier) {
        return strengthOnly(id, sourceId, strengthMultiplier, PERMANENT);
    }

    /**
     * Creates a timed modifier that only multiplies gravity strength without overriding
     * the entity's gravity direction.
     */
    public static GravityModifier strengthOnly(UUID id, @NotNull String sourceId, double strengthMultiplier, int durationTicks) {
        return new GravityModifier(id, sourceId, Direction.DOWN, strengthMultiplier, DEFAULT_PRIORITY, null, durationTicks);
    }

    public static GravityModifier timed(
        UUID id,
        @NotNull String sourceId,
        @NotNull Direction direction,
        double strengthMultiplier,
        int priority,
        int durationTicks
    ) {
        return new GravityModifier(id, sourceId, direction, strengthMultiplier, priority, RotationParameters.getDefault(), durationTicks);
    }

    public static GravityModifier timed(
        UUID id,
        @NotNull String sourceId,
        @NotNull Vec3 gravityVector,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters,
        int durationTicks
    ) {
        return new GravityModifier(id, sourceId, gravityVector, strengthMultiplier, priority, rotationParameters, durationTicks);
    }

    public static GravityModifier ofItem(UUID id, String itemKey, Direction dir, double strength) {
        return new GravityModifier(id, "item:" + itemKey, dir, strength, ITEM_PRIORITY, RotationParameters.getDefault());
    }

    public static GravityModifier ofCommand(UUID id, Direction dir, double strength) {
        return new GravityModifier(id, "command", dir, strength, COMMAND_PRIORITY, RotationParameters.getDefault());
    }

    public static GravityModifier ofZone(UUID id, String zoneName, Direction dir, double strength) {
        return new GravityModifier(id, "zone:" + zoneName, dir, strength, ZONE_PRIORITY, RotationParameters.getDefault());
    }

    public static GravityModifier ofEffect(UUID id, String effectName, Direction dir, double strength, int amplifier) {
        return new GravityModifier(id, "effect:" + effectName, dir, strength, EFFECT_PRIORITY + amplifier, RotationParameters.getDefault());
    }

    public static GravityModifier ofVehicle(UUID id, Direction dir, double strength) {
        return new GravityModifier(id, "vehicle", dir, strength, VEHICLE_PRIORITY, RotationParameters.getDefault());
    }

    public static GravityModifier ofDimension(UUID id, double strength) {
        return new GravityModifier(id, "dimension", Direction.DOWN, strength, DIMENSION_PRIORITY, RotationParameters.getDefault());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("source", sourceId);
        tag.putString("direction", direction().getName());
        tag.putDouble("gravityX", gravityVector.x);
        tag.putDouble("gravityY", gravityVector.y);
        tag.putDouble("gravityZ", gravityVector.z);
        tag.putDouble("strength", strengthMultiplier);
        tag.putInt("priority", priority);
        if (rotationParameters != null) {
            tag.put("rotation", rotationParameters.toTag());
        }
        if (durationTicks >= 0) {
            tag.putInt("duration", durationTicks);
        }
        if (absoluteStrength) {
            tag.putBoolean("absoluteStrength", true);
        }
        return tag;
    }

    public static GravityModifier fromTag(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String source = tag.getString("source");
        Direction dir = Direction.byName(tag.getString("direction"));
        Vec3 gravityVector = tag.contains("gravityX") && tag.contains("gravityY") && tag.contains("gravityZ")
            ? new Vec3(tag.getDouble("gravityX"), tag.getDouble("gravityY"), tag.getDouble("gravityZ"))
            : Vec3.atLowerCornerOf((dir == null ? Direction.DOWN : dir).getNormal());
        double strength = tag.getDouble("strength");
        int priority = tag.getInt("priority");
        RotationParameters rot = tag.contains("rotation") ? RotationParameters.fromTag(tag.getCompound("rotation")) : null;
        int duration = tag.contains("duration") ? tag.getInt("duration") : PERMANENT;
        boolean absolute = tag.getBoolean("absoluteStrength");
        return new GravityModifier(id, source, gravityVector, strength, priority, rot, duration, absolute);
    }

    public static ListTag toListTag(List<GravityModifier> modifiers) {
        ListTag list = new ListTag();
        for (GravityModifier m : modifiers) {
            list.add(m.toTag());
        }
        return list;
    }

    public static List<GravityModifier> fromListTag(ListTag list) {
        return list.stream()
            .map(t -> fromTag((CompoundTag) t))
            .sorted(Comparator.comparingInt(GravityModifier::priority).reversed())
            .toList();
    }

    /**
     * Compute the resulting cardinal direction from a list of modifiers.
     * Highest-priority vector wins (equal-priority fields blend vectorially) and is rounded to its nearest cardinal direction.
     */
    public static Optional<Direction> computeDirection(List<GravityModifier> modifiers) {
        return computeGravityVector(modifiers)
            .map(v -> Direction.getNearest(v.x, v.y, v.z));
    }

    /**
     * Compute the resulting world-space gravity vector from a list of modifiers.
     * When multiple direction-overriding modifiers share the same highest priority
     * (e.g. overlapping planets/moons), their gravity vectors combine by force-weighted
     * vector addition, producing smooth transitions and natural Lagrange points.
     */
    public static Optional<Vec3> computeGravityVector(List<GravityModifier> modifiers) {
        Optional<Integer> maxPriorityOpt = modifiers.stream()
            .filter(GravityModifier::overridesDefaultGravity)
            .map(GravityModifier::priority)
            .max(Integer::compareTo);
        if (maxPriorityOpt.isEmpty()) {
            return Optional.empty();
        }
        int maxPriority = maxPriorityOpt.get();
        List<GravityModifier> top = modifiers.stream()
            .filter(GravityModifier::overridesDefaultGravity)
            .filter(m -> m.priority() == maxPriority)
            .toList();
        if (top.size() == 1) {
            return Optional.of(top.get(0).gravityVector());
        }

        Vec3 combined = Vec3.ZERO;
        for (GravityModifier m : top) {
            double weight = Math.abs(m.strengthMultiplier());
            combined = combined.add(m.gravityVector().scale(weight));
        }
        if (combined.lengthSqr() > 1.0E-12) {
            return Optional.of(combined.normalize());
        }
        return Optional.of(top.get(0).gravityVector());
    }

    /**
     * If any highest-priority modifier defines an absolute local gravity field
     * (e.g. a planet or zone), returns the combined magnitude of all top-priority
     * absolute fields. Otherwise returns empty so ambient base + dimension gravity is used.
     */
    public static Optional<Double> computeAbsoluteFieldStrength(List<GravityModifier> modifiers) {
        Optional<Integer> maxAbsPriorityOpt = modifiers.stream()
            .filter(GravityModifier::absoluteStrength)
            .map(GravityModifier::priority)
            .max(Integer::compareTo);
        if (maxAbsPriorityOpt.isEmpty()) {
            return Optional.empty();
        }
        int maxAbsPriority = maxAbsPriorityOpt.get();
        List<GravityModifier> topAbs = modifiers.stream()
            .filter(GravityModifier::absoluteStrength)
            .filter(m -> m.priority() == maxAbsPriority)
            .toList();
        if (topAbs.size() == 1) {
            return Optional.of(topAbs.get(0).strengthMultiplier());
        }
        Vec3 netForce = Vec3.ZERO;
        for (GravityModifier m : topAbs) {
            netForce = netForce.add(m.gravityVector().scale(m.strengthMultiplier()));
        }
        return Optional.of(netForce.length());
    }

    private static boolean overridesDefaultGravity(GravityModifier modifier) {
        return modifier.priority() > DEFAULT_PRIORITY
            || modifier.gravityVector().distanceToSqr(new Vec3(0, -1, 0)) > 1.0E-12;
    }

    /**
     * Compute the resulting multiplicative strength factor from all non-absolute modifiers.
     */
    public static double computeMultiplicativeStrength(List<GravityModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> !m.absoluteStrength())
            .mapToDouble(GravityModifier::strengthMultiplier)
            .reduce(1.0, (a, b) -> a * b);
    }

    /**
     * Compute the resulting strength multiplier from a list of modifiers.
     * All multipliers multiply together.
     */
    public static double computeStrength(List<GravityModifier> modifiers) {
        return modifiers.stream()
            .mapToDouble(GravityModifier::strengthMultiplier)
            .reduce(1.0, (a, b) -> a * b);
    }

    /**
     * Get the rotation parameters from the highest priority modifier that has them.
     */
    public static Optional<RotationParameters> computeRotationParameters(List<GravityModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> m.rotationParameters() != null)
            .max(Comparator.comparingInt(GravityModifier::priority))
            .map(GravityModifier::rotationParameters);
    }
}