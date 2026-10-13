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

import java.util.UUID;

public class GravityInvertMobEffect extends MobEffect {
    
    public static final int COLOR = 0x98D982;
    
    public static final ResourceLocation PHASE = Pondus.id("invert_mob_effect_phase");

    public static final ResourceLocation ID = Pondus.id("invert");

    public static final Holder<MobEffect> INSTANCE = register(new GravityInvertMobEffect());
    
    private GravityInvertMobEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    public static void init() {
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;
        
        // Invert effect: add a modifier with the opposite direction of base gravity
        Direction baseDir = PondusAPI.getBaseGravityDirection(entity);
        Direction invertedDir = baseDir.getOpposite();
        
        UUID modId = UUID.nameUUIDFromBytes("effect:gravity_invert".getBytes());
        PondusAPI.addModifier(entity, new GravityModifier(
            modId,
            "effect:gravity_invert",
            invertedDir,
            1.0,
            GravityModifier.EFFECT_PRIORITY + amplifier,
            null
        ));
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, net.minecraft.world.entity.ai.attributes.AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);
        if (!entity.level().isClientSide()) {
            UUID modId = UUID.nameUUIDFromBytes("effect:gravity_invert".getBytes());
            PondusAPI.removeModifier(entity, modId);
        }
    }

    private static Holder.Reference<MobEffect> register(MobEffect mobEffect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ID, mobEffect);
    }
}