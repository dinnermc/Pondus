package dinner.dev.pondus.mob_effect.refined;

import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public class GravityStrengthMobEffect extends MobEffect {
    
    public final double base;
    public final int signum;
    
    public static final Holder<GravityStrengthMobEffect> INCREASE =
        register("strength_increase",new GravityStrengthMobEffect(0x98D982, 1.2, 1));
    public static final Holder<GravityStrengthMobEffect> DECREASE =
        register("strength_decrease",new GravityStrengthMobEffect(0x98D982, 0.7, 1));
    
    // it turns gravity into levitation but does not change player orientation
    public static final Holder<GravityStrengthMobEffect> REVERSE =
            register("strength_reverse",new GravityStrengthMobEffect(0x98D982, 1.0, -1));
    
    protected GravityStrengthMobEffect(int color, double base, int signum) {
        super(MobEffectCategory.NEUTRAL, color);
        this.base = base;
        this.signum = signum;
    }

    public static void init() {
    }
    
    public double getGravityStrengthMultiplier(int level) {
        return Math.pow(base, level) * signum;
    }
    
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        
        double multiplier = getGravityStrengthMultiplier(amplifier);
        UUID modId = UUID.nameUUIDFromBytes(("effect:gravity_strength_" + (signum > 0 ? "increase" : signum < 0 ? "reverse" : "decrease")).getBytes());
        
        // Strength effects are multiplicative - add as a modifier with current direction
        PondusAPI.addModifier(entity, new GravityModifier(
            UUID.nameUUIDFromBytes(("effect:gravity_strength_" + (signum > 0 ? "increase" : signum < 0 ? "reverse" : "decrease")).getBytes()),
            "effect:gravity_strength",
            net.minecraft.core.Direction.DOWN, // Direction doesn't matter for pure strength
            getGravityStrengthMultiplier(amplifier),
            GravityModifier.EFFECT_PRIORITY + amplifier,
            null
        ));
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        if (!entity.level().isClientSide()) {
            UUID modId = UUID.nameUUIDFromBytes(("effect:gravity_strength_" + (signum > 0 ? "increase" : signum < 0 ? "reverse" : "decrease")).getBytes());
            PondusAPI.removeModifier(entity, modId);
        }
    }

    public static void init() {
    }
    
    public double getGravityStrengthMultiplier(int level) {
        return Math.pow(base, level) * signum;
    }

    private static <M extends MobEffect> Holder.Reference<M> register(String string, M mobEffect) {
        return (Holder.Reference<M>) Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Pondus.id(string), mobEffect);
    }
}