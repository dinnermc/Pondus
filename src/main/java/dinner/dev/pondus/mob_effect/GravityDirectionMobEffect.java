package dinner.dev.pondus.mob_effect;

import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumMap;
import java.util.UUID;

public class GravityDirectionMobEffect extends MobEffect {
    public static final int COLOR = 0x98D982;
    
    public static final ResourceLocation PHASE = Pondus.id("dir_mob_effect_phase");
    
    public final Direction gravityDirection;
    
    public GravityDirectionMobEffect(Direction gravityDirection) {
        super(MobEffectCategory.NEUTRAL, COLOR);
        this.gravityDirection = gravityDirection;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        
        // Add/update modifier for this effect
        UUID modId = UUID.nameUUIDFromBytes(("effect:gravity_direction_" + gravityDirection.getName()).getBytes());
        PondusAPI.addModifier(entity, new GravityModifier(
            modId,
            "effect:gravity_direction_" + gravityDirection.getName(),
            gravityDirection,
            1.0,
            GravityModifier.EFFECT_PRIORITY + amplifier,
            null
        ));
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        // Remove the modifier when effect ends
        if (!entity.level().isClientSide()) {
            UUID modId = UUID.nameUUIDFromBytes(("effect:gravity_direction_" + gravityDirection.getName()).getBytes());
            PondusAPI.removeModifier(entity, modId);
        }
    }

    public static final EnumMap<Direction, Holder<MobEffect>> EFFECT_MAP =
        new EnumMap<>(Direction.class);

    public static void init() {
    }
    
    static {
        for (Direction dir : Direction.values()) {
            Holder<MobEffect> effect = register(dir.getSerializedName(), new GravityDirectionMobEffect(dir));
            EFFECT_MAP.put(dir, effect);
        }
    }

    private static Holder.Reference<MobEffect> register(String name,MobEffect mobEffect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,Pondus.id(name),mobEffect);
    }
}
