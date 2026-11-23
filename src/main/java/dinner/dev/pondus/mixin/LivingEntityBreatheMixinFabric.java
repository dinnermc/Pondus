package dinner.dev.pondus.mixin;

import dinner.dev.pondus.api.PondusAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityBreatheMixinFabric {
    @Redirect(
            method = "baseTick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;containing(DDD)Lnet/minecraft/core/BlockPos;",
                    ordinal = 0
            )
    )
    private BlockPos redirect_baseTick_blockPos(double x, double y, double z) {
        Entity self = (Entity) (Object) this;
        Direction gravityDirection = PondusAPI.getGravityDirection(self);
        if (gravityDirection == Direction.DOWN) {
            return BlockPos.containing(x, y, z);
        }
        return BlockPos.containing(self.getEyePosition());
    }
}
