package dinner.dev.pondus;

import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.mob_effect.GravityDirectionMobEffect;
import dinner.dev.pondus.mob_effect.GravityInvertMobEffect;
import dinner.dev.pondus.mob_effect.refined.GravityStrengthMobEffect;
import dinner.dev.pondus.zone.ZoneManager;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.world.level.Level;

public class CommonEvents {

    public static void handleGravity(Entity entity, IEntityGravityData gravity) {
////////////////////////////////////////////////////////////////////////////////////
        if (entity instanceof LivingEntity livingEntity) {
            GravityStrengthMobEffect.INCREASE.value().apply(livingEntity,gravity,GravityStrengthMobEffect.INCREASE);
            GravityStrengthMobEffect.DECREASE.value().apply(livingEntity,gravity,GravityStrengthMobEffect.DECREASE);
            GravityStrengthMobEffect.REVERSE.value().apply(livingEntity,gravity,GravityStrengthMobEffect.REVERSE);
        }
////////////////////////////////////////////////////////////////////DIRECTION
        if (entity instanceof LivingEntity livingEntity) {
            for (Holder<MobEffect> dirEffect : GravityDirectionMobEffect.EFFECT_MAP.values()) {
                MobEffectInstance effectInstance = livingEntity.getEffect(dirEffect);
                if (effectInstance != null) {
                    int amplifier = effectInstance.getAmplifier();

                    gravity.applyGravityDirectionEffect(
                            ((GravityDirectionMobEffect)dirEffect.value()).gravityDirection,
                            null,
                            amplifier + 1.0
                    );
                }
            }
////////////////////////////////////////////////////////////////////////////////////////////INVERT
            if (livingEntity.hasEffect(GravityInvertMobEffect.INSTANCE)) {
                gravity.applyGravityDirectionEffect(
                        gravity.getCurrGravityDirection().getOpposite(),
                        null, 5
                );
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        ZoneManager.getInstance().tick(event.getLevel());
    }
}