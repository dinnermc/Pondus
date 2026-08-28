package dinner.dev.pondus.mixin.client;

import dinner.dev.pondus.PondusClient;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ScreenEffectRenderer.class)
public class ScreenEffectRendererNeoForgeMixin {
    @Inject(
            method = "getOverlayBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void inject_getInWallBlockState(Player player, CallbackInfoReturnable<BlockState> cir) {
        PondusClient.inject_getInWallBlockState(player, cir);
    }
}
