package dinner.dev.pondus.util;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.entity.player.PlayerEntity;
import net.minecraft.world.entity.projectile.AbstractArrowEntity;
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
    public final Entity entity;
    // Only used on client, not synchronized.
    @Nullable
    public final RotationAnimation animation;
    // if it equals entity.tickCount,
    // it means that the gravity update event has already fired in this tick
    protected long lastUpdateTickCount = 0;

    // only used on server side
    protected boolean needsSync = false;

    protected boolean initialized = false;
    // not synchronized
    protected Direction prevGravityDirection = Direction.DOWN;
    protected double prevGravityStrength = 1.0;
    protected Direction currGravityDirection = Direction.DOWN;
    protected double currGravityStrength = 1.0;
    @Nullable
    protected RotationParameters currentRotationParameters = RotationParameters.getDefault();

    // Modifier stack
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
        // Remove any existing modifier with the same ID
        modifiers.removeIf(m -> m.id().equals(modifier.id()));
        modifiers.add(modifier);
        needsSync = true;
    }

    @Override
    public void removeModifier(UUID modifierId) {
        boolean removed = modifiers.removeIf(m -> m.id().equals(modifierId));
        if (removed) {
            needsSync = true;
        }
    }

    @Override
    public void removeModifiersBySource(String sourceId) {
        boolean removed = modifiers.removeIf(m -> m.sourceId().equals(sourceId));
        if (removed) {
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
        // Legacy compatibility: create a temporary modifier with a generated ID
        UUID tempId = UUID.randomUUID();
        GravityModifier modifier = new GravityModifier(
            tempId,
            "legacy",
            direction,
            1.0,
            (int) priority,
            rotationParameters
        );
        addModifier(modifier);
    }

    @Override
    @Deprecated
    public void applyGravityStrengthEffect(double strengthMultiplier) {
        // Legacy: apply as a strength modifier with a temporary ID
        UUID tempId = UUID.randomUUID();
        GravityModifier modifier = new GravityModifier(
            tempId,
            "legacy_strength",
            currGravityDirection,
            strengthMultiplier,
            0,
            null
        );
        addModifier(modifier);
    }

    // ==================== Gravity Computation ====================

    protected void recomputeGravity() {
        Direction oldDir = currGravityDirection;
        double oldStrength = currGravityStrength;

        // Compute direction from modifier stack
        Optional<Direction> dirOpt = GravityModifier.computeDirection(modifiers);
        if (dirOpt.isPresent()) {
            currGravityDirection = dirOpt.get();
        } else {
            currGravityDirection = baseGravityDirection;
        }

        // Compute strength from modifier stack
        double modifierStrength = GravityModifier.computeStrength(modifiers);
        currGravityStrength = baseGravityStrength * modifierStrength;
        currGravityStrength *= PondusAPI.getDimensionGravityStrength(entity.level());
        currGravityStrength *= Pondus.config.gravityStrengthMultiplier;

        // Compute rotation parameters
        Optional<RotationParameters> rotOpt = GravityModifier.computeRotationParameters(modifiers);
        if (rotOpt.isPresent()) {
            currentRotationParameters = rotOpt.get();
        } else if (currentEffectPriority == Double.MIN_VALUE) {
            currentRotationParameters = RotationParameters.getDefault();
        }

        // Check if changed
        boolean changed = oldDir != currGravityDirection || Math.abs(oldStrength - currGravityStrength) > 0.0001;
        if (changed) {
            needsSync = true;
        }
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
        needsSync = true;
    }

    @Override
    public Direction getCurrGravityDirection() {
        return currGravityDirection;
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
        needsSync = true;
    }

    @Override
    public void reset() {
        baseGravityDirection = Direction.DOWN;
        baseGravityStrength = 1.0;
        removeModifiersBySource("command");
        needsSync = true;
    }

    // ==================== Tick & Sync ====================

    @Override
    public void commonTick() {
        if (!canChangeGravity()) {
            return;
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

    protected void syncEntity() {
        CompoundTag tag = new CompoundTag();
        toNbt(tag);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(entity,tag),entity,true);
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

        if (entity instanceof AreaEffectCloud || entity instanceof AbstractArrowEntity || entity instanceof EndCrystal) {
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
            if (entity instanceof PlayerEntity) {
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

        if (prevGravityDirection != currGravityDirection) {
            applyGravityDirectionChange(
                prevGravityDirection, currGravityDirection,
                currentRotationParameters, false
            );
            prevGravityDirection = currGravityDirection;
        }

        if (Math.abs(currGravityStrength - prevGravityStrength) > 0.0001) {
            prevGravityStrength = currGravityStrength;
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

        // Recompute gravity from modifier stack
        recomputeGravity();

        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            // Vehicle gravity takes precedence - add as modifier
            Direction vehicleDir = PondusAPI.getGravityDirection(vehicle);
            double vehicleStrength = PondusAPI.getGravityStrength(vehicle);
            UUID vehicleId = UUID.nameUUIDFromBytes(("vehicle:" + vehicle.getUUID()).getBytes());
            GravityModifier vehicleModifier = new GravityModifier(
                vehicleId,
                "vehicle",
                vehicleDir,
                vehicleStrength,
                GravityModifier.VEHICLE_PRIORITY,
                RotationParameters.getDefault()
            );
            addModifier(vehicleModifier);
            recomputeGravity();
        }

        lastUpdateTickCount = entity.tickCount;

        if (sendPacketIfNecessary) {
            boolean changed = false; // recomputeGravity already sets needsSync
            if (needsSync) {
                sendSyncPacketToOtherPlayers();
            }
        }
    }

    @Override
    public void fromNbt(CompoundTag tag) {
        if (tag.contains("baseGravityDirection")) {
            baseGravityDirection = Direction.byName(tag.getString("baseGravityDirection"));
        }
        else {
            baseGravityDirection = Direction.DOWN;
        }

        if (tag.contains("baseGravityStrength")) {
            baseGravityStrength = tag.getDouble("baseGravityStrength");
        }
        else {
            baseGravityStrength = 1.0;
        }

        // Load modifiers
        modifiers.clear();
        if (tag.contains("modifiers")) {
            ListTag list = tag.getList("modifiers", Tag.TAG_COMPOUND);
            for (Tag t : list) {
                modifiers.add(GravityModifier.fromTag((CompoundTag) t));
            }
        }
        // Sort by priority descending
        modifiers.sort(Comparator.comparingInt(GravityModifier::priority).reversed());

        // the current gravity is serialized to avoid unnecessary gravity rotation when entering world
        // do not deserialize it when for client player when not initializing
        if (!initialized || shouldAcceptServerSync()) {
            if (tag.contains("currentGravityDirection")) {
                currGravityDirection = Direction.byName(tag.getString("currentGravityDirection"));
            }
            else {
                currGravityDirection = Direction.DOWN;
            }

            if (tag.contains("currentGravityStrength")) {
                currGravityStrength = tag.getDouble("currentGravityStrength");
            }
            else {
                currGravityStrength = 1.0;
            }
        }

        if (!initialized) {
            prevGravityDirection = currGravityDirection;
            prevGravityStrength = currGravityStrength;
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
    }

    protected void postEvent() {
        Services.PLATFORM.postEvent(entity,this);
    }

    // ==================== Legacy Compatibility ====================

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
        needsSync = true;
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
        needsSync = true;
    }

    @Override
    public void forceApplyGravityChange() {
        prevGravityDirection = currGravityDirection;
        prevGravityStrength = currGravityStrength;
    }

    @Override
    public void applyGravityStrengthEffect(double strengthMultiplier) {
        // Legacy compatibility
        applyGravityStrengthEffect(strengthMultiplier);
    }

    @Override
    public void applyGravityChange() {
        if (currentRotationParameters == null) {
            currentRotationParameters = RotationParameters.getDefault();
        }

        if (prevGravityDirection != currGravityDirection) {
            applyGravityDirectionChange(
                prevGravityDirection, currGravityDirection,
                currentRotationParameters, false
            );
            prevGravityDirection = currGravityDirection;
        }

        if (Math.abs(currGravityStrength - prevGravityStrength) > 0.0001) {
            prevGravityStrength = currGravityStrength;
        }
    }

    @Override
    public void commonTick() {
        if (!canChangeGravity()) {
            return;
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

    @Override
    public RotationAnimation getRotationAnimation() {
        return animation;
    }

    protected boolean shouldAcceptServerSync() {
        return entity.level().isClientSide() && !GCUtil.isClientPlayer(entity);
    }

    protected void sendSyncPacketToOtherPlayers() {
        if (entity.level().isClientSide()) return;
        CompoundTag tag = new CompoundTag();
        toNbt(tag);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(entity,tag),entity,false);
    }

    protected void postEvent() {
        Services.PLATFORM.postEvent(entity,this);
    }
}