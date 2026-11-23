package dinner.dev.pondus.util;

import com.mojang.logging.LogUtils;
import dinner.dev.pondus.CommonEvents;
import dinner.dev.pondus.EntityTags;
import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.RotationAnimation;
import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.RotationParameters;
import dinner.dev.pondus.mixin.EntityAccessor;
import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.platform.Services;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EntityGravityData implements IEntityGravityData {

    protected static final Logger LOGGER = LogUtils.getLogger();
    private static final double GRAVITY_VECTOR_SYNC_THRESHOLD_SQR = 4.0E-5;
    private static final double CARDINAL_SWITCH_HYSTERESIS = 0.16;
    public final Entity entity;
    // Only used on client, not synchronized.
    @Nullable
    public final RotationAnimation animation;
    // if it equals entity.tickCount,
    // it means that the gravity update event has already fired in this tick
    protected long lastUpdateTickCount = 0;

    // only used on server side
    protected boolean needsSync = false;
    private Vec3 syncedGravityVector = new Vec3(0, -1, 0);

    protected boolean initialized = false;
    // not synchronized
    protected Direction prevGravityDirection = Direction.DOWN;
    protected Vec3 prevGravityVector = new Vec3(0, -1, 0);
    protected double prevGravityStrength = 1.0;
    protected Direction currGravityDirection = Direction.DOWN;
    protected Vec3 currGravityVector = new Vec3(0, -1, 0);
    protected double currGravityStrength = 1.0;
    // the base gravity direction
    protected Direction baseGravityDirection = Direction.DOWN;
    // the base gravity strength
    protected double baseGravityStrength = 1.0;
    @Nullable
    protected RotationParameters currentRotationParameters = RotationParameters.getDefault();

    // Modifier stack (effects are re-derived each tick, zones/commands/vehicles persist)
    protected final List<GravityModifier> modifiers = new ArrayList<>();

    public EntityGravityData(Entity entity) {
        this.entity = entity;
        if (entity.level().isClientSide()) {
            animation = new RotationAnimation();
        }
        else {
            animation = null;
        }
    }

    // ==================== GravityModifier API ====================

    @Override
    public void addModifier(GravityModifier modifier) {
        Direction oldGravityDirection = currGravityDirection;
        double oldGravityStrength = currGravityStrength;
        // Replace any existing modifier with the same ID.
        modifiers.removeIf(m -> m.id().equals(modifier.id()));
        modifiers.add(modifier);
        modifiers.sort(Comparator.comparingInt(GravityModifier::priority).reversed());
        recomputeGravity();
        if (oldGravityDirection != currGravityDirection
            || syncedGravityVector.distanceToSqr(currGravityVector) >= GRAVITY_VECTOR_SYNC_THRESHOLD_SQR
            || Math.abs(oldGravityStrength - currGravityStrength) > 0.0001) {
            needsSync = true;
        }
    }

    @Override
    public void removeModifier(UUID modifierId) {
        if (modifiers.removeIf(m -> m.id().equals(modifierId))) {
            recomputeGravity();
            needsSync = true;
        }
    }

    @Override
    public void removeModifiersBySource(String sourceId) {
        if (modifiers.removeIf(m -> m.sourceId().equals(sourceId))) {
            recomputeGravity();
            needsSync = true;
        }
    }

    @Override
    public List<GravityModifier> getModifiers() {
        return List.copyOf(modifiers);
    }

    @Override
    @Deprecated
    public void applyGravityDirectionEffect(
            @NotNull Direction direction,
            @Nullable RotationParameters rotationParameters,
            double priority
    ) {
        // Legacy compatibility: model the effect as a modifier with a stable ID.
        String sourceId = "legacy_direction";
        UUID id = UUID.nameUUIDFromBytes(
            (sourceId + ":" + direction.getName() + ":" + (int) priority).getBytes()
        );
        addModifier(new GravityModifier(
            id, sourceId, direction, 1.0, (int) priority, rotationParameters
        ));
    }

    @Override
    @Deprecated
    public void applyGravityStrengthEffect(double strengthMultiplier) {
        // Legacy compatibility: model the strength change as a modifier.
        String sourceId = "legacy_strength";
        UUID id = UUID.nameUUIDFromBytes(sourceId.getBytes());
        addModifier(new GravityModifier(
            id, sourceId, currGravityDirection, strengthMultiplier, 0, null
        ));
    }

    // ==================== Gravity Computation ====================

    protected void recomputeGravity() {
        // The vector retains continuous source directions; cardinal alignment uses hysteresis
        // so walking across 45-degree sphere seams does not oscillate between faces.
        Optional<Vec3> gravityVector = GravityModifier.computeGravityVector(modifiers);
        currGravityVector = gravityVector.orElseGet(
            () -> Vec3.atLowerCornerOf(baseGravityDirection.getNormal())
        );
        currGravityDirection = resolveCardinalDirection(currGravityVector, currGravityDirection);

        // Strength: absolute local fields (planets, zones) establish local gravity independent
        // of ambient dimension gravity (so planets work in zero-G space); multiplicative
        // modifiers (items, potions, effects) then scale the resulting strength.
        double multiplicativeStrength = GravityModifier.computeMultiplicativeStrength(modifiers);
        Optional<Double> absoluteFieldStrength = GravityModifier.computeAbsoluteFieldStrength(modifiers);
        if (absoluteFieldStrength.isPresent()) {
            currGravityStrength = absoluteFieldStrength.get() * multiplicativeStrength;
        } else {
            currGravityStrength = baseGravityStrength
                * PondusAPI.getDimensionGravityStrength(entity.level())
                * multiplicativeStrength;
        }
        currGravityStrength *= Pondus.config.gravityStrengthMultiplier;

        // Rotation parameters: highest priority modifier that supplies them.
        Optional<RotationParameters> rotOpt = GravityModifier.computeRotationParameters(modifiers);
        currentRotationParameters = rotOpt.orElseGet(RotationParameters::getDefault);
    }

    private static Direction resolveCardinalDirection(Vec3 gravityVector, Direction currentDirection) {
        Direction nearest = Direction.getNearest(gravityVector.x, gravityVector.y, gravityVector.z);
        if (currentDirection == null || nearest == currentDirection) {
            return nearest;
        }
        double nearestDot = gravityVector.dot(Vec3.atLowerCornerOf(nearest.getNormal()));
        if (nearestDot > 0.999) {
            return nearest;
        }
        double currentDot = gravityVector.dot(Vec3.atLowerCornerOf(currentDirection.getNormal()));
        if (currentDot > 0.0 && nearestDot - currentDot < CARDINAL_SWITCH_HYSTERESIS) {
            return currentDirection;
        }
        return nearest;
    }

    // ==================== Base Gravity (Commands) ====================

    @Override
    public double getBaseGravityStrength() {
        return baseGravityStrength;
    }

    @Override
    public void setBaseGravityStrength(double strength) {
        if (!canChangeGravity()) {
            return;
        }

        baseGravityStrength = strength;
        recomputeGravity();
        needsSync = true;
    }

    @Override
    public Direction getCurrGravityDirection() {
        return currGravityDirection;
    }

    @Override
    public Vec3 getCurrGravityVector() {
        return currGravityVector;
    }

    @Override
    public double getCurrGravityStrength() {
        return currGravityStrength;
    }

    protected boolean canChangeGravity() {
        return EntityTags.canChangeGravity(entity);
    }

    public Direction getPrevGravityDirection() {
        return prevGravityDirection;
    }

    @Override
    public Direction getBaseGravityDirection() {
        return baseGravityDirection;
    }

    @Override
    public void setBaseGravityDirection(Direction gravityDirection) {
        if (!canChangeGravity()) {
            return;
        }

        baseGravityDirection = gravityDirection;
        recomputeGravity();
        needsSync = true;
    }

    @Override
    public void reset() {
        baseGravityDirection = Direction.DOWN;
        baseGravityStrength = 1.0;
        modifiers.clear();
        recomputeGravity();
        needsSync = true;
    }

    /**
     * Not needed in normal cases.
     * Only used in {@link PondusAPI#instantlySetClientBaseGravityDirection(Entity, Direction)}
     * Used by ImmPtl.
     */
    @Override
    public void forceApplyGravityChange() {
        prevGravityDirection = currGravityDirection;
        prevGravityVector = currGravityVector;
        prevGravityStrength = currGravityStrength;
    }

    public boolean isDefault() {
        return baseGravityDirection == Direction.DOWN
            && Math.abs(baseGravityStrength - 1.0) <= 1.0E-6
            && modifiers.isEmpty();
    }

    @Override
    public void commonTick() {
        if (!canChangeGravity()) {
            return;
        }

        if (!entity.level().isClientSide()) {
            tickTimedModifiers();
        }

        updateGravityStatus(true);

        applyGravityChange();

        if (!entity.level().isClientSide()) {
            if (needsSync) {
                needsSync = false;
                syncEntity();
            }
        }
    }

    private void tickTimedModifiers() {
        boolean expiredAny = false;
        for (int i = modifiers.size() - 1; i >= 0; i--) {
            GravityModifier modifier = modifiers.get(i);
            if (!modifier.isTimed()) {
                continue;
            }
            if (modifier.durationTicks() <= 1) {
                modifiers.remove(i);
                expiredAny = true;
            } else {
                modifiers.set(i, modifier.tickDown());
            }
        }
        if (expiredAny) {
            recomputeGravity();
            needsSync = true;
        }
    }

    protected void syncEntity() {
        CompoundTag tag = new CompoundTag();
        toNbt(tag);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(entity,tag),entity,true);
        syncedGravityVector = currGravityVector;
    }

    // ==================== Gravity Application ====================

    // getVelocity() does not return the actual velocity. It returns the velocity plus acceleration.
    // Even if the entity is standing still, getVelocity() will still give a downwards vector.
    // The real velocity is this tick position subtract last tick position
    private static Vec3 getRealWorldVelocity(Entity entity, Direction prevGravityDirection) {
        if (entity.isControlledByLocalInstance()) {
            return new Vec3(
                entity.getX() - entity.xo,
                entity.getY() - entity.yo,
                entity.getZ() - entity.zo
            );
        }

        return RotationUtil.vecPlayerToWorld(entity.getDeltaMovement(), prevGravityDirection);
    }

    @NotNull
    private static Vec3 getLocalRotationCenter(
        Entity entity,
        Direction oldGravity, Direction newGravity, RotationParameters rotationParameters
    ) {
        if (entity instanceof EndCrystal) {
            //In the middle of the block below
            return new Vec3(0, -0.5, 0);
        }

        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        if (newGravity.getOpposite() == oldGravity) {
            // In the center of the hit-box
            return new Vec3(0, dimensions.height() / 2, 0);
        }
        else {
            return Vec3.ZERO;
        }
    }

    private static Vec3 getPositionAdjustmentOffset(
        AABB entityBoundingBox, AABB nearbyCollisionUnion, Direction movingDirection
    ) {
        Direction.Axis axis = movingDirection.getAxis();
        double offset = 0;
        if (movingDirection.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            double pushing = nearbyCollisionUnion.max(axis);
            double pushed = entityBoundingBox.min(axis);
            if (pushing > pushed) {
                offset = pushing - pushed;
            }
        }
        else {
            double pushing = nearbyCollisionUnion.min(axis);
            double pushed = entityBoundingBox.max(axis);
            if (pushing < pushed) {
                offset = pushed - pushing;
            }
        }

        return new Vec3(movingDirection.step()).scale(offset);
    }

    public void applyGravityDirectionChange(
        Direction oldGravity, Direction newGravity,
        RotationParameters rotationParameters, boolean isInitialization
    ) {
        if (!canChangeGravity()) {
            return;
        }

        // update bounding box
        entity.setBoundingBox(((EntityAccessor) entity).gc_makeBoundingBox());

        // A weird thing is that,
        // using `entity.setPos(entity.position())` to a painting on client side
        // make the painting move wrongly, because Painting overrides `trackingPosition()`.
        // No entity other than Painting overrides that method.
        // It seems to be legacy code from early versions of Minecraft.

        if (isInitialization) {
            return;
        }

        entity.fallDistance = 0;

        long timeMs = entity.level().getGameTime() * 50;

        Vec3 relativeRotationCenter = getLocalRotationCenter(
            entity, oldGravity, newGravity, rotationParameters
        );
        Vec3 oldPos = entity.position();
        Vec3 oldLastTickPos = new Vec3(entity.xOld, entity.yOld, entity.zOld);
        Vec3 rotationCenter = oldPos.add(RotationUtil.vecPlayerToWorld(relativeRotationCenter, oldGravity));
        Vec3 newPos = rotationCenter.subtract(RotationUtil.vecPlayerToWorld(relativeRotationCenter, newGravity));
        Vec3 posTranslation = newPos.subtract(oldPos);
        Vec3 newLastTickPos = oldLastTickPos.add(posTranslation);

        entity.setPos(newPos);
        entity.xo = newLastTickPos.x;
        entity.yo = newLastTickPos.y;
        entity.zo = newLastTickPos.z;
        entity.xOld = newLastTickPos.x;
        entity.yOld = newLastTickPos.y;
        entity.zOld = newLastTickPos.z;

        adjustEntityPosition(oldGravity, newGravity, entity.getBoundingBox());

        if (entity.level().isClientSide()) {
            Validate.notNull(animation, "gravity animation is null");

            int rotationTimeMS = rotationParameters.rotationTimeMS();

            animation.startRotationAnimation(
                newGravity, oldGravity,
                rotationTimeMS,
                entity, timeMs, rotationParameters.rotateView(),
                relativeRotationCenter
            );
        }

        Vec3 realWorldVelocity = getRealWorldVelocity(entity, oldGravity);
        if (rotationParameters.rotateVelocity()) {
            // Rotate velocity with gravity, this will cause things to appear to take a sharp turn
            Vector3f worldSpaceVec = realWorldVelocity.toVector3f();
            worldSpaceVec.rotate(RotationUtil.getRotationBetween(oldGravity, newGravity));
            entity.setDeltaMovement(RotationUtil.vecWorldToPlayer(new Vec3(worldSpaceVec), newGravity));
        }
        else {
            // Velocity will be conserved relative to the world, will result in more natural motion
            entity.setDeltaMovement(RotationUtil.vecWorldToPlayer(realWorldVelocity, newGravity));
        }
    }

    // Adjust position to avoid suffocation in blocks when changing gravity
    private void adjustEntityPosition(Direction oldGravity, Direction newGravity, AABB entityBoundingBox) {
        if (!Pondus.config.adjustPositionAfterChangingGravity) {
            return;
        }

        if (entity instanceof AreaEffectCloud || entity instanceof AbstractArrow || entity instanceof EndCrystal) {
            return;
        }

        // for example, if gravity changed from down to north, move up
        // if gravity changed from down to up, also move up
        Direction movingDirection = oldGravity.getOpposite();

        Iterable<VoxelShape> collisions = entity.level().getCollisions(
            entity,
            entityBoundingBox.inflate(-0.01) // shrink to avoid floating point error
        );
        AABB totalCollisionBox = null;
        for (VoxelShape collision : collisions) {
            if (!collision.isEmpty()) {
                AABB boundingBox = collision.bounds();
                if (totalCollisionBox == null) {
                    totalCollisionBox = boundingBox;
                }
                else {
                    totalCollisionBox = totalCollisionBox.minmax(boundingBox);
                }
            }
        }

        if (totalCollisionBox != null) {
            Vec3 positionAdjustmentOffset = getPositionAdjustmentOffset(
                entityBoundingBox, totalCollisionBox, movingDirection
            );
            if (entity instanceof Player) {
                LOGGER.info("Adjusting player position {} {}", positionAdjustmentOffset, entity);
            }
            entity.setPos(entity.position().add(positionAdjustmentOffset));
        }
    }

    @Override
    public RotationAnimation getRotationAnimation() {
        return animation;
    }

    @Override
    public void applyGravityChange() {
        if (currentRotationParameters == null) {
            currentRotationParameters = RotationParameters.getDefault();
        }

        boolean changed = false;
        if (prevGravityDirection != currGravityDirection) {
            applyGravityDirectionChange(
                prevGravityDirection, currGravityDirection,
                currentRotationParameters, false
            );
            prevGravityDirection = currGravityDirection;
            changed = true;
        }

        if (prevGravityVector.distanceToSqr(currGravityVector) > 1.0E-8) {
            prevGravityVector = currGravityVector;
            changed = true;
        }

        if (Math.abs(currGravityStrength - prevGravityStrength) > 0.0001) {
            prevGravityStrength = currGravityStrength;
            changed = true;
        }

        if (changed) {
            postEvent();
        }
    }

    @Override
    public void updateGravityStatus(boolean sendPacketIfNecessary) {
        // for the remote players and non-player entities,
        // their effect data is not synchronized to the client
        // (possibly for making it harder to cheat for hacked clients)
        // then we don't calculate its gravity in normal way in client
        if (shouldAcceptServerSync()) {
            return;
        }

        Direction oldGravityDirection = currGravityDirection;
        Vec3 oldGravityVector = currGravityVector;
        double oldGravityStrength = currGravityStrength;

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            // Vehicle gravity fully overrides the rider's gravity.
            currGravityDirection = PondusAPI.getGravityDirection(vehicle);
            currGravityVector = PondusAPI.getGravityVector(vehicle);
            currGravityStrength = PondusAPI.getGravityStrength(vehicle);
        }
        else {
            // Effects contribute transient modifiers; rebuild and recompute the stack.
            CommonEvents.handleGravity(entity, this);
            recomputeGravity();
        }

        if (entity.level().isClientSide()
            && (currGravityDirection != oldGravityDirection
                || currGravityVector.distanceToSqr(oldGravityVector) > 1.0E-8)
            && animation != null) {
            animation.setGravityVector(currGravityVector, currGravityDirection, entity.level().getGameTime() * 50);
        }

        lastUpdateTickCount = entity.tickCount;

        if (sendPacketIfNecessary && !entity.level().isClientSide()) {
            boolean changed = oldGravityDirection != currGravityDirection
                    || syncedGravityVector.distanceToSqr(currGravityVector) >= GRAVITY_VECTOR_SYNC_THRESHOLD_SQR
                    || Math.abs(oldGravityStrength - currGravityStrength) > 0.0001;
            if (changed) {
                needsSync = true;
            }
        }
    }

    @Override
    public void fromNbt(CompoundTag tag) {
        Direction oldGravityDirection = currGravityDirection;
        Vec3 oldGravityVector = currGravityVector;
        if (tag.contains("baseGravityDirection")) {
            Direction parsedBase = Direction.byName(tag.getString("baseGravityDirection"));
            baseGravityDirection = parsedBase != null ? parsedBase : Direction.DOWN;
        }
        else {
            baseGravityDirection = Direction.DOWN;
        }

        if (tag.contains("baseGravityStrength")) {
            double parsedStrength = tag.getDouble("baseGravityStrength");
            baseGravityStrength = Double.isFinite(parsedStrength) ? parsedStrength : 1.0;
        }
        else {
            baseGravityStrength = 1.0;
        }

        // Load modifiers
        modifiers.clear();
        if (tag.contains("modifiers")) {
            ListTag list = tag.getList("modifiers", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                try {
                    modifiers.add(GravityModifier.fromTag((CompoundTag) t));
                } catch (IllegalArgumentException ignored) {
                }
            }
            modifiers.sort(Comparator.comparingInt(GravityModifier::priority).reversed());
        }

        // the current gravity is serialized to avoid unnecessary gravity rotation when entering world
        // for the local player after initialization, recompute immediately from the synced modifiers
        if (!initialized || shouldAcceptServerSync()) {
            if (tag.contains("currentGravityDirection")) {
                Direction parsedCurr = Direction.byName(tag.getString("currentGravityDirection"));
                currGravityDirection = parsedCurr != null ? parsedCurr : Direction.DOWN;
            }
            else {
                currGravityDirection = Direction.DOWN;
            }

            if (tag.contains("currentGravityStrength")) {
                double parsedCurrStrength = tag.getDouble("currentGravityStrength");
                currGravityStrength = Double.isFinite(parsedCurrStrength) ? parsedCurrStrength : 1.0;
            }
            else {
                currGravityStrength = 1.0;
            }
            if (tag.contains("currentGravityVectorX")
                && tag.contains("currentGravityVectorY")
                && tag.contains("currentGravityVectorZ")) {
                Vec3 loadedVector = new Vec3(
                    tag.getDouble("currentGravityVectorX"),
                    tag.getDouble("currentGravityVectorY"),
                    tag.getDouble("currentGravityVectorZ")
                );
                if (loadedVector.lengthSqr() > 1.0E-12) {
                    currGravityVector = loadedVector.normalize();
                } else {
                    currGravityVector = Vec3.atLowerCornerOf(currGravityDirection.getNormal());
                }
            } else {
                currGravityVector = Vec3.atLowerCornerOf(currGravityDirection.getNormal());
            }
            Optional<RotationParameters> rotOpt = GravityModifier.computeRotationParameters(modifiers);
            currentRotationParameters = rotOpt.orElseGet(RotationParameters::getDefault);
        } else {
            recomputeGravity();
        }

        if (initialized && animation != null
            && (currGravityDirection != oldGravityDirection
                || currGravityVector.distanceToSqr(oldGravityVector) > 1.0E-8)) {
            animation.setGravityVector(currGravityVector, currGravityDirection, entity.level().getGameTime() * 50);
        }

        if (!initialized) {
            prevGravityDirection = currGravityDirection;
            prevGravityVector = currGravityVector;
            prevGravityStrength = currGravityStrength;
            if (animation != null) {
                animation.initializeGravityVector(currGravityVector, currGravityDirection);
            }
            initialized = true;
            applyGravityDirectionChange(
                    prevGravityDirection, currGravityDirection, currentRotationParameters, true
            );
        }
    }

    @Override
    public void toNbt(@NotNull CompoundTag tag) {
        tag.putString("baseGravityDirection", baseGravityDirection.getName());
        tag.putString("currentGravityDirection", currGravityDirection.getName());
        tag.putDouble("currentGravityVectorX", currGravityVector.x);
        tag.putDouble("currentGravityVectorY", currGravityVector.y);
        tag.putDouble("currentGravityVectorZ", currGravityVector.z);

        tag.putDouble("baseGravityStrength", baseGravityStrength);
        tag.putDouble("currentGravityStrength", currGravityStrength);

        // Save modifiers
        tag.put("modifiers", GravityModifier.toListTag(modifiers));
    }

    protected boolean shouldAcceptServerSync() {
        return entity.level().isClientSide() && !GCUtil.isClientPlayer(entity);
    }

    protected void sendSyncPacketToOtherPlayers() {
        if (entity.level().isClientSide()) return;
        CompoundTag tag = new CompoundTag();
        toNbt(tag);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(entity,tag),entity,false);
        syncedGravityVector = currGravityVector;
        needsSync = false;
    }

    protected void postEvent() {
        Services.PLATFORM.postEvent(entity,this);
    }
}
