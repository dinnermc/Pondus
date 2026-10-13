package dinner.dev.pondus;

import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.mob_effect.GravityDirectionMobEffect;
import dinner.dev.pondus.mob_effect.GravityInvertMobEffect;
import dinner.dev.pondus.mob_effect.refined.GravityStrengthMobEffect;
import dinner.dev.pondus.zone.ZoneManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public class CommonEvents {

    public static final String EFFECT_SOURCE_PREFIX = "effect:";

    public static void initFabric() {
        ServerTickEvents.END_WORLD_TICK.register(CommonEvents::onLevelTick);
    }

    public static void onLevelTick(ServerLevel level) {
        ZoneManager.getInstance().tick(level);
    }

    /**
     * Re-derives every mob-effect gravity modifier for the given entity.
     * Effect modifiers are transient: they are cleared and rebuilt every tick so
     * they vanish as soon as the corresponding effect ends, without needing
     * removal hooks on the effect itself.
     */
    public static void handleGravity(Entity entity, IEntityGravityData gravity) {
        // Clear previously applied effect modifiers (they are rebuilt below).
        for (GravityModifier modifier : gravity.getModifiers()) {
            if (modifier.sourceId().startsWith(EFFECT_SOURCE_PREFIX)) {
                gravity.removeModifier(modifier.id());
            }
        }

        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        // Direction effects: one modifier per active direction effect.
        for (Holder<MobEffect> dirEffect : GravityDirectionMobEffect.EFFECT_MAP.values()) {
            MobEffectInstance effectInstance = livingEntity.getEffect(dirEffect);
            if (effectInstance == null) {
                continue;
            }
            int amplifier = effectInstance.getAmplifier();
            Direction direction = ((GravityDirectionMobEffect) dirEffect.value()).gravityDirection;
            String sourceId = EFFECT_SOURCE_PREFIX + "gravity_direction_" + direction.getName();
            gravity.addModifier(new GravityModifier(
                UUID.nameUUIDFromBytes(sourceId.getBytes()),
                sourceId,
                direction,
                1.0,
                GravityModifier.EFFECT_PRIORITY + amplifier,
                null
            ));
        }

        // Invert effect: flip the current gravity direction.
        if (livingEntity.hasEffect(GravityInvertMobEffect.INSTANCE)) {
            String sourceId = EFFECT_SOURCE_PREFIX + "gravity_invert";
            gravity.addModifier(new GravityModifier(
                UUID.nameUUIDFromBytes(sourceId.getBytes()),
                sourceId,
                gravity.getCurrGravityDirection().getOpposite(),
                1.0,
                GravityModifier.EFFECT_PRIORITY + 5,
                null
            ));
        }

        // Strength effects: multiplicative, stacked per amplifier level.
        applyStrengthEffect(livingEntity, gravity, GravityStrengthMobEffect.INCREASE, "strength_increase");
        applyStrengthEffect(livingEntity, gravity, GravityStrengthMobEffect.DECREASE, "strength_decrease");
        applyStrengthEffect(livingEntity, gravity, GravityStrengthMobEffect.REVERSE, "strength_reverse");
    }

    private static void applyStrengthEffect(
        LivingEntity livingEntity, IEntityGravityData gravity,
        Holder<GravityStrengthMobEffect> effect, String name
    ) {
        @SuppressWarnings("unchecked")
        MobEffectInstance effectInstance = livingEntity.getEffect((Holder<MobEffect>) (Holder<?>) effect);
        if (effectInstance == null) {
            return;
        }
        int level = effectInstance.getAmplifier() + 1;
        double multiplier = effect.value().getGravityStrengthMultiplier(level);
        String sourceId = EFFECT_SOURCE_PREFIX + name;
        gravity.addModifier(new GravityModifier(
            UUID.nameUUIDFromBytes(sourceId.getBytes()),
            sourceId,
            gravity.getCurrGravityDirection(),
            multiplier,
            GravityModifier.EFFECT_PRIORITY,
            null
        ));
    }
}
