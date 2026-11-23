package dinner.dev.pondus;

import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.mob_effect.GravityDirectionMobEffect;
import dinner.dev.pondus.mob_effect.GravityInvertMobEffect;
import dinner.dev.pondus.mob_effect.refined.GravityStrengthMobEffect;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommonEvents {

    public static final String EFFECT_SOURCE_PREFIX = "effect:";

    /**
     * Re-derives mob-effect gravity modifiers for the given entity and diffs them
     * against currently active effect modifiers so unchanged effects do not trigger
     * unnecessary network sync packets every tick.
     */
    public static void handleGravity(Entity entity, IEntityGravityData gravity) {
        List<GravityModifier> existingModifiers = gravity.getModifiers();
        Map<UUID, GravityModifier> existingEffects = new LinkedHashMap<>();
        List<GravityModifier> nonEffectModifiers = new ArrayList<>();
        for (GravityModifier modifier : existingModifiers) {
            if (modifier.sourceId().startsWith(EFFECT_SOURCE_PREFIX)) {
                existingEffects.put(modifier.id(), modifier);
            } else {
                nonEffectModifiers.add(modifier);
            }
        }

        Map<UUID, GravityModifier> desiredEffects = new LinkedHashMap<>();
        if (entity instanceof LivingEntity livingEntity) {
            List<GravityModifier> directionCandidates = new ArrayList<>(nonEffectModifiers);

            // Direction effects: one modifier per active direction effect.
            for (Holder<MobEffect> dirEffect : GravityDirectionMobEffect.EFFECT_MAP.values()) {
                MobEffectInstance effectInstance = livingEntity.getEffect(dirEffect);
                if (effectInstance == null) {
                    continue;
                }
                int amplifier = effectInstance.getAmplifier();
                Direction direction = ((GravityDirectionMobEffect) dirEffect.value()).gravityDirection;
                String sourceId = EFFECT_SOURCE_PREFIX + "gravity_direction_" + direction.getName();
                UUID id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8));
                GravityModifier modifier = new GravityModifier(
                    id,
                    sourceId,
                    direction,
                    1.0,
                    GravityModifier.EFFECT_PRIORITY + amplifier,
                    null
                );
                desiredEffects.put(id, modifier);
                directionCandidates.add(modifier);
            }

            // Invert effect: flip the effective direction computed without invert.
            if (livingEntity.hasEffect(GravityInvertMobEffect.INSTANCE)) {
                Direction effectiveDirection = GravityModifier.computeDirection(directionCandidates)
                    .orElseGet(gravity::getBaseGravityDirection);
                String sourceId = EFFECT_SOURCE_PREFIX + "gravity_invert";
                UUID id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8));
                desiredEffects.put(id, new GravityModifier(
                    id,
                    sourceId,
                    effectiveDirection.getOpposite(),
                    1.0,
                    GravityModifier.EFFECT_PRIORITY + 5,
                    null
                ));
            }

            // Strength effects: multiplicative only (DEFAULT_PRIORITY + DOWN does not override direction).
            addStrengthEffect(livingEntity, desiredEffects, GravityStrengthMobEffect.INCREASE, "strength_increase");
            addStrengthEffect(livingEntity, desiredEffects, GravityStrengthMobEffect.DECREASE, "strength_decrease");
            addStrengthEffect(livingEntity, desiredEffects, GravityStrengthMobEffect.REVERSE, "strength_reverse");
        }

        for (UUID id : existingEffects.keySet()) {
            if (!desiredEffects.containsKey(id)) {
                gravity.removeModifier(id);
            }
        }
        for (Map.Entry<UUID, GravityModifier> entry : desiredEffects.entrySet()) {
            GravityModifier existing = existingEffects.get(entry.getKey());
            if (existing == null || !existing.equals(entry.getValue())) {
                gravity.addModifier(entry.getValue());
            }
        }
    }

    private static void addStrengthEffect(
        LivingEntity livingEntity, Map<UUID, GravityModifier> desiredEffects,
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
        UUID id = UUID.nameUUIDFromBytes(sourceId.getBytes(StandardCharsets.UTF_8));
        desiredEffects.put(id, new GravityModifier(
            id,
            sourceId,
            Direction.DOWN,
            multiplier,
            GravityModifier.DEFAULT_PRIORITY,
            null
        ));
    }
}
