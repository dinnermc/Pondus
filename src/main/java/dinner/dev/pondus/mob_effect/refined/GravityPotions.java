package dinner.dev.pondus.mob_effect.refined;

import dinner.dev.pondus.Pondus;
import dinner.dev.pondus.mob_effect.GravityDirectionMobEffect;
import dinner.dev.pondus.util.GCUtil;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

import java.util.EnumMap;

public class GravityPotions {

    public static final EnumMap<Direction, Potion> DIR_POTIONS = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            Potion potion = new Potion(
                    new MobEffectInstance(
                            GCUtil.dirtyCast(GravityDirectionMobEffect.EFFECT_MAP.get(direction)), 9600, 1
                    )
            );
            DIR_POTIONS.put(direction, potion);
        }
    }

    public static Potion STRENGTH_DECR_POTION_0 = new Potion(
        new MobEffectInstance(
            GCUtil.dirtyCast(GravityStrengthMobEffect.DECREASE), 9600, 0
        )
    );
    
    public static Potion STRENGTH_DECR_POTION_1 = new Potion(
        new MobEffectInstance(
                GCUtil.dirtyCast(GravityStrengthMobEffect.DECREASE), 9600, 1
        )
    );
    
    public static Potion STRENGTH_INCR_POTION_0 = new Potion(
        new MobEffectInstance(
            GCUtil.dirtyCast(GravityStrengthMobEffect.INCREASE), 9600, 0
        )
    );
    
    public static Potion STRENGTH_INCR_POTION_1 = new Potion(
        new MobEffectInstance(
            GCUtil.dirtyCast(GravityStrengthMobEffect.INCREASE), 9600, 1
        )
    );

    static {
        Registry.register(
                BuiltInRegistries.POTION,
                Pondus.id("gravity_decr_0"),
                GravityPotions.STRENGTH_DECR_POTION_0
        );

        Registry.register(
                BuiltInRegistries.POTION,
                Pondus.id("gravity_decr_1"),
                GravityPotions.STRENGTH_DECR_POTION_1
        );

        Registry.register(
                BuiltInRegistries.POTION,
                Pondus.id("gravity_incr_0"),
                GravityPotions.STRENGTH_INCR_POTION_0
        );

        Registry.register(
                BuiltInRegistries.POTION,
                Pondus.id("gravity_incr_1"),
                GravityPotions.STRENGTH_INCR_POTION_1
        );

        for (Direction direction : Direction.values()) {
            Potion potion = GravityPotions.DIR_POTIONS.get(direction);
            Registry.register(
                    BuiltInRegistries.POTION,
                    GravityPotions.getPotionId(direction),
                    potion
            );
        }
    }

    public static void init() {

    }
    
    public static ResourceLocation getPotionId(Direction direction) {
        return switch (direction) {
            case DOWN -> Pondus.id("gravity_down_0");
            case UP -> Pondus.id("gravity_up_0");
            case NORTH -> Pondus.id("gravity_north_0");
            case SOUTH -> Pondus.id("gravity_south_0");
            case WEST -> Pondus.id("gravity_west_0");
            case EAST -> Pondus.id("gravity_east_0");
        };
    }
    
    public static final Potion[] ALL = new Potion[]{
        STRENGTH_DECR_POTION_0,
        STRENGTH_DECR_POTION_1,
        STRENGTH_INCR_POTION_0,
        STRENGTH_INCR_POTION_1,
        DIR_POTIONS.get(Direction.DOWN),
        DIR_POTIONS.get(Direction.UP),
        DIR_POTIONS.get(Direction.NORTH),
        DIR_POTIONS.get(Direction.SOUTH),
        DIR_POTIONS.get(Direction.WEST),
        DIR_POTIONS.get(Direction.EAST)
    };
}
