package dinner.dev.pondus.api;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A gravity modifier from a specific source.
 * Each modifier has an ID, direction, strength multiplier, priority, and source identifier.
 */
public record GravityModifier(
    UUID id,
    @NotNull String sourceId,        // e.g. "command", "zone:spawn", "effect:gravity_increase", "vehicle"
    @NotNull Direction direction,
    double strengthMultiplier,        // multiplicative: 1.0 = normal, 2.0 = double gravity
    int priority,                     // higher = wins for direction
    @Nullable RotationParameters rotationParameters
) {
    public static final int DEFAULT_PRIORITY = 0;
    public static final int COMMAND_PRIORITY = 100;
    public static final int ZONE_PRIORITY = 50;
    public static final int EFFECT_PRIORITY = 25;
    public static final int VEHICLE_PRIORITY = 200;
    public static final int DIMENSION_PRIORITY = -100;

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
        tag.putString("direction", direction.getName());
        tag.putDouble("strength", strengthMultiplier);
        tag.putInt("priority", priority);
        if (rotationParameters != null) {
            tag.put("rotation", rotationParameters.toTag());
        }
        return tag;
    }

    public static GravityModifier fromTag(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        String source = tag.getString("source");
        Direction dir = Direction.byName(tag.getString("direction"));
        double strength = tag.getDouble("strength");
        int priority = tag.getInt("priority");
        RotationParameters rot = tag.contains("rotation") ? RotationParameters.fromTag(tag.getCompound("rotation")) : null;
        return new GravityModifier(id, source, dir, strength, priority, rot);
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
     * Compute the resulting direction from a list of modifiers.
     * Highest priority non-null direction wins.
     */
    public static Optional<Direction> computeDirection(List<GravityModifier> modifiers) {
        return modifiers.stream()
            .filter(m -> m.direction() != Direction.DOWN || m.priority() > DEFAULT_PRIORITY)
            .max(Comparator.comparingInt(GravityModifier::priority))
            .map(GravityModifier::direction);
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