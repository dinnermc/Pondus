package dinner.dev.pondus;

import dinner.dev.pondus.util.QuaternionUtil;
import dinner.dev.pondus.util.RotationUtil;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RotationAnimation {
    // Exponential damping rate (1/s) for continuous vector tracking (e.g. orbiting planets).
    private static final double VECTOR_DAMPING_SPEED = 14.0;
    // Below this angular difference on a cardinal face switch, hand off seamlessly to the
    // continuous damper instead of starting a discrete animation that stalls camera tracking.
    private static final double SEAMLESS_HANDOFF_ANGLE_RAD = Math.toRadians(18.0);
    private static final double MAX_FRAME_DT_SECONDS = 0.05;

    private boolean inAnimation = false;
    private Quaternionf startGravityRotation;
    private Direction startGravityDirection;
    private Direction endGravityDirection;
    private Vec3 relativeRotationCenter = Vec3.ZERO;
    private float strafeInputMultiplier = 1.0F;
    private Vec3 currentGravityVector = new Vec3(0, -1, 0);
    private Direction currentGravityDirection = Direction.DOWN;
    private Quaternionf smoothedRotation = new Quaternionf();
    private Quaternionf vectorEndRotation = new Quaternionf();
    private long lastRenderNanos = 0L;
    
    private long startTimeMs;
    private long endTimeMs;
    
    public void startRotationAnimation(
        Direction newGravity, Direction prevGravity,
        long durationTimeMs, Entity entity, long timeMs,
        boolean rotateView, Vec3 relativeRotationCenter
    ) {
        update(timeMs);
        Quaternionf currentAnimatedGravityRotation = peekCurrentGravityRotation(timeMs);

        if (currentGravityVector.lengthSqr() < 1.0E-12) {
            currentGravityVector = Vec3.atLowerCornerOf(newGravity.getNormal());
        }
        currentGravityDirection = newGravity;
        vectorEndRotation = RotationUtil.getWorldRotationQuaternion(currentGravityVector, newGravity);
        Quaternionf newEndGravityRotation = new Quaternionf(vectorEndRotation);
        if (durationTimeMs <= 0) {
            inAnimation = false;
            smoothedRotation = new Quaternionf(vectorEndRotation);
            lastRenderNanos = System.nanoTime();
            return;
        }
        
        Validate.notNull(entity);
        
        Vec3 newLookingDirection = getNewLookingDirection(
            newGravity, prevGravity, entity, rotateView, currentAnimatedGravityRotation
        );
        
        Quaternionf oldViewRotation = QuaternionUtil.getViewRotation(entity.getXRot(), entity.getYRot());
        
        // camera rotation = view rotation(pitch and yaw) * gravity rotation(animated)
        Quaternionf currentAnimatedCameraRotation = new Quaternionf().set(oldViewRotation).mul(currentAnimatedGravityRotation);
        
        Vec3 newLocalLook = QuaternionUtil.rotate(newLookingDirection, newEndGravityRotation);
        Vec2 newYawAndPitch = RotationUtil.vecToRot(newLocalLook);
        float newPitch = Mth.clamp(newYawAndPitch.y, -90.0F, 90.0F);
        float newYaw = newYawAndPitch.x;
        float deltaYaw = Mth.wrapDegrees(newYaw - entity.getYRot());
        float deltaPitch = newPitch - entity.getXRot();
        entity.setYRot(entity.getYRot() + deltaYaw);
        entity.setXRot(entity.getXRot() + deltaPitch);
        entity.yRotO += deltaYaw;
        entity.xRotO += deltaPitch;
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.yBodyRot += deltaYaw;
            livingEntity.yBodyRotO += deltaYaw;
            livingEntity.yHeadRot += deltaYaw;
            livingEntity.yHeadRotO += deltaYaw;
        }
        
        Quaternionf newViewRotation = QuaternionUtil.getViewRotation(entity.getXRot(), entity.getYRot());
        Quaternionf newCameraRotation = new Quaternionf(newViewRotation).mul(newEndGravityRotation);
        Vec3 oldRight = QuaternionUtil.rotate(new Vec3(1, 0, 0), currentAnimatedCameraRotation);
        Vec3 newRight = QuaternionUtil.rotate(new Vec3(1, 0, 0), newCameraRotation);
        
        // gravity rotation = (view rotation^-1) * camera rotation
        Quaternionf animationStartGravityRotation = new Quaternionf().set(newViewRotation).conjugate().mul(currentAnimatedCameraRotation);

        float dot = Mth.clamp(Math.abs(animationStartGravityRotation.dot(vectorEndRotation)), 0.0F, 1.0F);
        double residualAngleRad = 2.0 * Math.acos(dot);

        // When crossing a cardinal face seam on a continuous sphere, the camera is already
        // tilted nearly to the target orientation; hand off directly to continuous damping
        // instead of stalling inside a discrete animation.
        if (residualAngleRad <= SEAMLESS_HANDOFF_ANGLE_RAD) {
            inAnimation = false;
            strafeInputMultiplier = 1.0F;
            this.relativeRotationCenter = Vec3.ZERO;
            smoothedRotation = new Quaternionf(animationStartGravityRotation);
            lastRenderNanos = System.nanoTime();
            return;
        }

        strafeInputMultiplier = oldRight.dot(newRight) < 0 ? -1.0F : 1.0F;
        this.relativeRotationCenter = relativeRotationCenter;
        inAnimation = true;
        startGravityRotation = animationStartGravityRotation;
        smoothedRotation = new Quaternionf(animationStartGravityRotation);
        startGravityDirection = prevGravity;
        endGravityDirection = newGravity;
        startTimeMs = timeMs;
        endTimeMs = timeMs + durationTimeMs;
        lastRenderNanos = System.nanoTime();
    }
    
    private Vec3 getNewLookingDirection(
        Direction newGravity, Direction prevGravity, Entity player,
        boolean rotateView, Quaternionf currentAnimatedGravityRotation
    ) {
        Quaternionf inverseGravity = new Quaternionf(currentAnimatedGravityRotation).conjugate();
        Vec3 oldLookingDirection = QuaternionUtil.rotate(
            RotationUtil.rotToVec(player.getYRot(), player.getXRot()),
            inverseGravity
        );
    
        if (!rotateView) {
            return oldLookingDirection;
        }
        
        if (newGravity == prevGravity.getOpposite()) {
            return oldLookingDirection.scale(-1);
        }
        
        Quaternionf deltaRotation = QuaternionUtil.getRotationBetween(
            Vec3.atLowerCornerOf(prevGravity.getNormal()),
            Vec3.atLowerCornerOf(newGravity.getNormal())
        );
        
        Vector3f lookingDirection = new Vector3f((float) oldLookingDirection.x, (float) oldLookingDirection.y, (float) oldLookingDirection.z);
        lookingDirection.rotate(deltaRotation);
        Vec3 newLookingDirection = new Vec3(lookingDirection);
        return newLookingDirection;
    }
    
    /**
     * It returns the rotation that applies to world for rendering.
     * To get the rotation that applies entity, conjugate it.
     */
    public Quaternionf getCurrentGravityRotation(Direction currentGravity, long timeMs) {
        update(timeMs);
        long nowNanos = System.nanoTime();
        double dtSeconds = lastRenderNanos == 0L
            ? 0.0
            : Mth.clamp((nowNanos - lastRenderNanos) * 1.0E-9, 0.0, MAX_FRAME_DT_SECONDS);
        lastRenderNanos = nowNanos;

        if (inAnimation) {
            double delta = (double) (timeMs - startTimeMs) / (endTimeMs - startTimeMs);
            smoothedRotation = RotationUtil.interpolate(
                startGravityRotation, vectorEndRotation, mapProgress((float) delta)
            );
            return new Quaternionf(smoothedRotation);
        }

        if (Math.abs(smoothedRotation.dot(vectorEndRotation)) < 0.9999995F) {
            if (dtSeconds > 0.0) {
                float alpha = (float) (1.0 - Math.exp(-VECTOR_DAMPING_SPEED * dtSeconds));
                smoothedRotation = RotationUtil.interpolate(smoothedRotation, vectorEndRotation, alpha);
            }
            return new Quaternionf(smoothedRotation);
        }

        smoothedRotation.set(vectorEndRotation);
        return new Quaternionf(vectorEndRotation);
    }

    private Quaternionf peekCurrentGravityRotation(long timeMs) {
        if (inAnimation) {
            double delta = (double) (timeMs - startTimeMs) / (endTimeMs - startTimeMs);
            return RotationUtil.interpolate(
                startGravityRotation, vectorEndRotation, mapProgress((float) delta)
            );
        }
        return new Quaternionf(smoothedRotation);
    }

    public void setGravityVector(Vec3 gravityVector, Direction gravityDirection, long timeMs) {
        Vec3 normalized = normalizeGravityVector(gravityVector, gravityDirection);
        if (normalized.distanceToSqr(currentGravityVector) < 1.0E-8
            && gravityDirection == currentGravityDirection) {
            return;
        }

        currentGravityVector = normalized;
        // Only update vectorEndRotation immediately when staying in the same cardinal frame;
        // cross-face transitions update vectorEndRotation inside startRotationAnimation so
        // smoothedRotation is never slerped between two mismatched cardinal yaw frames.
        if (gravityDirection == currentGravityDirection) {
            vectorEndRotation = RotationUtil.getWorldRotationQuaternion(normalized, gravityDirection);
        }
    }

    public void initializeGravityVector(Vec3 gravityVector) {
        Vec3 normalized = normalizeGravityVector(gravityVector, Direction.DOWN);
        Direction nearest = Direction.getNearest(normalized.x, normalized.y, normalized.z);
        initializeGravityVector(normalized, nearest);
    }

    public void initializeGravityVector(Vec3 gravityVector, Direction gravityDirection) {
        currentGravityVector = normalizeGravityVector(gravityVector, gravityDirection);
        currentGravityDirection = gravityDirection;
        smoothedRotation = RotationUtil.getWorldRotationQuaternion(currentGravityVector, gravityDirection);
        vectorEndRotation = new Quaternionf(smoothedRotation);
        lastRenderNanos = System.nanoTime();
    }

    private static Vec3 normalizeGravityVector(Vec3 vector, Direction fallbackDirection) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.y)
            || !Double.isFinite(vector.z) || vector.lengthSqr() < 1.0E-12) {
            return Vec3.atLowerCornerOf(fallbackDirection.getNormal());
        }
        return vector.scale(1.0 / Math.sqrt(vector.lengthSqr()));
    }
    
    public void update(long timeMs) {
        if (timeMs > endTimeMs) {
            inAnimation = false;
        }
    }
    
    /**
     * When doing gravity flipping, the rotation center is the player bounding box center.
     * But the player feet pos changes abruptly. So we need special calculation to eye offset.
     *
     * Note when rotateView is false, it will cause non-smooth eye offset change
     */
    public Vec3 getEyeOffset(
        Quaternionf gravityRot, Vec3 localEyeOffset, Direction newGravity
    ) {
        Quaternionf gravityRotForEntity = new Quaternionf(gravityRot).conjugate();
        
        if (!inAnimation || relativeRotationCenter.equals(Vec3.ZERO)) {
            return QuaternionUtil.rotate(localEyeOffset, gravityRotForEntity);
        }
        
        Vec3 rotationCenterOffset = RotationUtil.vecPlayerToWorld(relativeRotationCenter, newGravity);
        
        Vec3 eyeOffsetFromRotationCenter = localEyeOffset.subtract(relativeRotationCenter);
        Vec3 rotatedEyeOffsetFromRotationCenter =
            QuaternionUtil.rotate(eyeOffsetFromRotationCenter, gravityRotForEntity);
        
        return rotationCenterOffset.add(rotatedEyeOffsetFromRotationCenter);
    }
    
    private static float mapProgress(float delta) {
        return Mth.clamp((delta * delta * (3 - 2 * delta)), 0, 1);
    }
    
    public boolean isInAnimation() {
        return inAnimation;
    }

    public boolean isRotationActive(long timeMs) {
        update(timeMs);
        return inAnimation || Math.abs(smoothedRotation.dot(vectorEndRotation)) < 0.9999995F;
    }

    public boolean isRotatingBetween(Direction first, Direction second) {
        return inAnimation
            && (startGravityDirection == first && endGravityDirection == second
                || startGravityDirection == second && endGravityDirection == first);
    }

    public float getStrafeInputMultiplier() {
        return inAnimation ? strafeInputMultiplier : 1.0F;
    }
}
