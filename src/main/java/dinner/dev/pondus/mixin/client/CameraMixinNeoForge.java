package dinner.dev.pondus.mixin.client;

import dinner.dev.pondus.RotationAnimation;
import dinner.dev.pondus.api.PondusAPI;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)//caused by forge patch
public class CameraMixinNeoForge {
    @Shadow
    private Entity entity;

    @Shadow
    @Final
    private Quaternionf rotation;

    @Inject(
            method = "setRotation(FFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void inject_setRotation(CallbackInfo ci) {
        if (this.entity != null) {
            Direction gravityDirection = PondusAPI.getGravityDirection(this.entity);
            RotationAnimation animation = PondusAPI.getRotationAnimation(entity);
            if (animation == null) {
                return;
            }
            float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
            long timeMs = entity.level().getGameTime() * 50 + (long) (partialTick * 50);
            if (PondusAPI.getGravityVector(entity).distanceToSqr(new Vec3(0, -1, 0)) < 1.0E-8
                && !animation.isRotationActive(timeMs)) {
                return;
            }

            Quaternionf rotation = new Quaternionf(animation.getCurrentGravityRotation(gravityDirection, timeMs));
            rotation.conjugate();
            rotation.mul(this.rotation);
            this.rotation.set(rotation.x(), rotation.y(), rotation.z(), rotation.w());
        }
    }
}
