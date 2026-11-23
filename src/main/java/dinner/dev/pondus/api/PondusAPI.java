package dinner.dev.pondus.api;

import dinner.dev.pondus.EntityDuck;
import dinner.dev.pondus.EntityTags;
import dinner.dev.pondus.RotationAnimation;
import dinner.dev.pondus.attachments.DataAttachmentUtil;
import dinner.dev.pondus.network.S2CLevelGravityPacket;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.util.EntityGravityData;
import dinner.dev.pondus.util.RotationUtil;
import dinner.dev.pondus.zone.Zone;
import dinner.dev.pondus.zone.ZoneManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public interface PondusAPI {

    static EntityGravityData getGravityData(Entity entity) {
        return ((EntityDuck)entity).getGravityData();
    }

    static double getBaseGravityStrength(Entity entity) {
        return getGravityData(entity).getBaseGravityStrength();
    }
    /**
     * Returns the applied gravity direction for the given entity
     */
    static Direction getGravityDirection(Entity entity) {
        IEntityGravityData data = getGravityData(entity);
        return data == null ? Direction.DOWN : data.getCurrGravityDirection();
        //return getGravityComponent(entity).getCurrGravityDirection();
    }

    /**
     * Returns the normalized world-space gravity vector. Legacy gravity systems may still
     * use {@link #getGravityDirection(Entity)} for collision and movement orientation.
     */
    static Vec3 getGravityVector(Entity entity) {
        IEntityGravityData data = getGravityData(entity);
        return data == null ? new Vec3(0, -1, 0) : data.getCurrGravityVector();
    }

    static double getGravityStrength(Entity entity) {
        IEntityGravityData data = getGravityData(entity);
        return data == null ? 1 : data.getCurrGravityStrength();
    }

    static void setBaseGravityDirection(
            Entity entity, Direction gravityDirection
    ) {
        IEntityGravityData component = getGravityData(entity);
        component.setBaseGravityDirection(gravityDirection);
    }

    /**
     * Returns the main gravity direction for the given entity
     * This may not be the applied gravity direction for the player, see PondusAPIFabric#getAppliedGravityDirection
     */
    static Direction getBaseGravityDirection(Entity entity) {
        return getGravityData(entity).getBaseGravityDirection();
    }

    static void setBaseGravityStrength(Entity entity, double strength) {
        IEntityGravityData component = getGravityData(entity);
        component.setBaseGravityStrength(strength);
    }

    static void resetGravity(Entity entity) {
        if (!EntityTags.canChangeGravity(entity)) {return;}

        getGravityData(entity).reset();
    }

    /**
     * Instantly set gravity direction on client side without performing animation.
     * Not needed in normal cases.
     * (Used by ImmPtl)
     */
    static void instantlySetClientBaseGravityDirection(Entity entity, Direction direction) {
        Validate.isTrue(entity.level().isClientSide(), "should only be used on client");

        IEntityGravityData component = getGravityData(entity);

        component.setBaseGravityDirection(direction);

        component.updateGravityStatus(false);

        component.forceApplyGravityChange();
    }

    @Nullable
    static RotationAnimation getRotationAnimation(Entity entity) {
        IEntityGravityData data = getGravityData(entity);
        return data == null ? null :data.getRotationAnimation();
    }

    // ==================== Modifier Stack API ====================

    /**
     * Add a gravity modifier from a specific source.
     * The modifier with the highest priority determines the gravity direction.
     * All strength multipliers multiply together.
     */
    static void addModifier(Entity entity, GravityModifier modifier) {
        getGravityData(entity).addModifier(modifier);
    }

    /**
     * Remove a specific modifier by its ID.
     */
    static void removeModifier(Entity entity, UUID modifierId) {
        getGravityData(entity).removeModifier(modifierId);
    }

    /**
     * Remove all modifiers from a specific source (e.g. "zone:spawn", "effect:gravity_increase").
     */
    static void removeModifiersBySource(Entity entity, String sourceId) {
        getGravityData(entity).removeModifiersBySource(sourceId);
    }

    /**
     * Get all active modifiers for an entity (read-only copy).
     */
    static List<GravityModifier> getModifiers(Entity entity) {
        return getGravityData(entity).getModifiers();
    }

    /**
     * Get the highest priority modifier for a given source, if any.
     */
    static Optional<GravityModifier> getModifierBySource(Entity entity, String sourceId) {
        return getGravityData(entity).getModifiers().stream()
            .filter(m -> m.sourceId().equals(sourceId))
            .max(Comparator.comparingInt(GravityModifier::priority));
    }

    static double getDimensionGravityStrength(Level world) {
        return DataAttachmentUtil.getLevelGravityData(world);
    }

    static void setDimensionGravityStrength(Level world, double strength) {
        DataAttachmentUtil.setLevelGravityData(world,strength);
        // Fabric has no built-in attachment sync, so manually mirror the value to
        // every client currently in that dimension. Clients in other dimensions
        // receive it when they join or change world.
        if (world instanceof ServerLevel serverLevel) {
            Services.PLATFORM.sendToLevel(new S2CLevelGravityPacket(serverLevel.dimension(), strength), serverLevel);
        }
    }

    /**
     * Returns the world relative velocity for the given entity
     * Using minecraft's methods to get the velocity will return entity local velocity
     */
    static Vec3 getWorldVelocity(Entity entity) {
        return RotationUtil.vecPlayerToWorld(entity.getDeltaMovement(), getGravityDirection(entity));
    }

    /**
     * Sets the world relative velocity for the given player
     * Using minecraft's methods to set the velocity of an entity will set player relative velocity
     */
    static void setWorldVelocity(Entity entity, Vec3 worldVelocity) {
        entity.setDeltaMovement(RotationUtil.vecWorldToPlayer(worldVelocity, getGravityDirection(entity)));
    }

    /**
     * Returns eye position offset from feet position for the given entity
     */
    static Vec3 getEyeOffset(Entity entity) {
        return RotationUtil.vecPlayerToWorld(0, (double) entity.getEyeHeight(), 0, getGravityDirection(entity));
    }

    static boolean canChangeGravity(Entity entity) {
        return EntityTags.canChangeGravity(entity);
    }

    /**
     * Applies (or refreshes) a timed gravity modifier on an entity.
     * Calling again with the same {@code sourceId} replaces the existing modifier and resets its timer.
     * Automatically expires after {@code durationTicks} server ticks and syncs to clients.
     */
    static void applyTimedGravity(
        Entity entity,
        String sourceId,
        Direction direction,
        double strengthMultiplier,
        int priority,
        int durationTicks
    ) {
        applyTimedGravity(
            entity, sourceId, Vec3.atLowerCornerOf(direction.getNormal()),
            strengthMultiplier, priority, RotationParameters.getDefault(), durationTicks
        );
    }

    /**
     * Applies (or refreshes) a timed continuous-vector gravity modifier on an entity.
     */
    static void applyTimedGravity(
        Entity entity,
        String sourceId,
        Vec3 gravityVector,
        double strengthMultiplier,
        int priority,
        @Nullable RotationParameters rotationParameters,
        int durationTicks
    ) {
        if (!canChangeGravity(entity) || durationTicks <= 0) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("timed:" + sourceId).getBytes(StandardCharsets.UTF_8));
        addModifier(entity, GravityModifier.timed(
            id, sourceId, gravityVector, strengthMultiplier, priority, rotationParameters, durationTicks
        ));
    }

    /**
     * Applies (or refreshes) a timed strength-only gravity multiplier (does not override direction).
     * Ideal for consumables, feather/moon charms, temporary low-G abilities, etc.
     */
    static void applyTimedGravityStrength(
        Entity entity,
        String sourceId,
        double strengthMultiplier,
        int durationTicks
    ) {
        if (!canChangeGravity(entity) || durationTicks <= 0) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("timed_strength:" + sourceId).getBytes(StandardCharsets.UTF_8));
        addModifier(entity, GravityModifier.strengthOnly(id, sourceId, strengthMultiplier, durationTicks));
    }

    // ==================== Custom Items / Armor API ====================

    /**
     * Registers a gravity rule for a specific {@link Item} (held, worn in armor slots, or in inventory).
     * Register during mod setup. Returns the generated rule ID that can be passed to {@link #unregisterItemGravity}.
     */
    static ResourceLocation registerItemGravity(Item item, ItemGravityRule rule) {
        return ItemGravityManager.getInstance().register(item, rule);
    }

    /**
     * Registers a gravity rule for any {@link ItemStack} matching {@code matcher}
     * (useful for item tags, DataComponents, or enchantments).
     */
    static void registerItemGravity(ResourceLocation ruleId, Predicate<ItemStack> matcher, ItemGravityRule rule) {
        ItemGravityManager.getInstance().register(ruleId, matcher, rule);
    }

    /** Unregisters a previously registered item gravity rule. */
    static boolean unregisterItemGravity(ResourceLocation ruleId) {
        return ItemGravityManager.getInstance().unregister(ruleId);
    }

    // ==================== Persistent Zones API ====================

    /** Adds or replaces a persistent gravity zone in the given server dimension and syncs visualizers. */
    static void addZone(ServerLevel level, Zone zone) {
        ZoneManager.getInstance().addZone(zone, level);
    }

    /** Removes a persistent gravity zone by ID from the given server dimension. */
    static void removeZone(ServerLevel level, String zoneId) {
        ZoneManager.getInstance().removeZone(zoneId, level);
    }

    /** Gets a persistent gravity zone by ID in the given server dimension, or {@code null} if not found. */
    @Nullable
    static Zone getZone(ServerLevel level, String zoneId) {
        return ZoneManager.getInstance().getZone(zoneId, level);
    }

    /** Returns a snapshot of all persistent gravity zones in the given server dimension. */
    static Map<String, Zone> getZones(ServerLevel level) {
        return ZoneManager.getInstance().getZones(level);
    }

    /** Enables or disables a persistent zone, persists the change, and syncs visualizers. */
    static boolean setZoneEnabled(ServerLevel level, String zoneId, boolean enabled) {
        return ZoneManager.getInstance().setZoneEnabled(zoneId, enabled, level);
    }

    /** Computes an entity's smooth spatial influence ({@code 0.0} to {@code 1.0}) for the given zone. */
    static double getZoneInfluence(Entity entity, Zone zone) {
        return ZoneManager.getInstance().getZoneInfluence(entity, zone);
    }

    // ==================== Coordinate Transform Helpers ====================

    /** Converts a world-space vector into the entity's gravity-relative local coordinate space. */
    static Vec3 worldToLocal(Entity entity, Vec3 worldVec) {
        return RotationUtil.vecWorldToPlayer(worldVec, getGravityDirection(entity));
    }

    /** Converts an entity's gravity-relative local vector into world space. */
    static Vec3 localToWorld(Entity entity, Vec3 localVec) {
        return RotationUtil.vecPlayerToWorld(localVec, getGravityDirection(entity));
    }

    /** Converts a world cardinal direction into the entity's gravity-relative local direction. */
    static Direction worldToLocal(Entity entity, Direction worldDirection) {
        return RotationUtil.dirWorldToPlayer(worldDirection, getGravityDirection(entity));
    }

    /** Converts an entity's gravity-relative local direction into a world cardinal direction. */
    static Direction localToWorld(Entity entity, Direction localDirection) {
        return RotationUtil.dirPlayerToWorld(localDirection, getGravityDirection(entity));
    }

    // ==================== Gravity Sources API ====================

    /**
     * Registers a global mod-provided gravity source. Register global sources once during mod initialization.
     * For world/block-entity-backed sources, prefer {@link #registerLevelGravitySource}.
     */
    static void registerGravitySource(GravitySource source) {
        GravitySourceManager.register(source);
    }

    /**
     * Registers (or replaces) a dimension-scoped gravity source (e.g. for a BlockEntity).
     * Only evaluated in that dimension and automatically cleared when the server stops.
     */
    static void registerLevelGravitySource(ResourceKey<Level> dimension, GravitySource source) {
        GravitySourceManager.registerForLevel(dimension, source);
    }

    /** Convenience overload for {@link #registerLevelGravitySource(ResourceKey, GravitySource)}. */
    static void registerLevelGravitySource(ServerLevel level, GravitySource source) {
        GravitySourceManager.registerForLevel(level.dimension(), source);
    }

    /**
     * Unregisters a previously registered global or level-scoped source.
     * Its modifiers are removed on the next server tick.
     *
     * @return whether a source with this id was registered
     */
    static boolean unregisterGravitySource(ResourceLocation sourceId) {
        return GravitySourceManager.unregister(sourceId);
    }

    /** Unregisters a level-scoped gravity source for a specific dimension. */
    static boolean unregisterLevelGravitySource(ResourceKey<Level> dimension, ResourceLocation sourceId) {
        return GravitySourceManager.unregisterForLevel(dimension, sourceId);
    }
}
