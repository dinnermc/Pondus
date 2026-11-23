package dinner.dev.pondus.api;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Declarative rule describing how a custom item, armor piece, or inventory charm
 * modifies the gravity of the entity holding or wearing it.
 *
 * <p>Register rules via {@link PondusAPI#registerItemGravity}.</p>
 */
public final class ItemGravityRule {

    public enum SlotTrigger {
        MAINHAND,
        OFFHAND,
        EITHER_HAND,
        HEAD,
        CHEST,
        LEGS,
        FEET,
        ANY_ARMOR,
        INVENTORY
    }

    private final SlotTrigger trigger;
    private final Predicate<ItemStack> stackFilter;
    private final BiFunction<LivingEntity, ItemStack, @Nullable GravityField> provider;

    private ItemGravityRule(
        SlotTrigger trigger,
        Predicate<ItemStack> stackFilter,
        BiFunction<LivingEntity, ItemStack, @Nullable GravityField> provider
    ) {
        this.trigger = trigger;
        this.stackFilter = stackFilter;
        this.provider = provider;
    }

    public SlotTrigger trigger() {
        return trigger;
    }

    public boolean matchesStack(ItemStack stack) {
        return !stack.isEmpty() && stackFilter.test(stack);
    }

    public @Nullable GravityField evaluate(LivingEntity entity, ItemStack stack) {
        return provider.apply(entity, stack);
    }

    public static Builder heldInMainHand() {
        return new Builder(SlotTrigger.MAINHAND);
    }

    public static Builder heldInOffHand() {
        return new Builder(SlotTrigger.OFFHAND);
    }

    public static Builder heldInEitherHand() {
        return new Builder(SlotTrigger.EITHER_HAND);
    }

    public static Builder wornIn(@NotNull EquipmentSlot slot) {
        Objects.requireNonNull(slot, "slot");
        return switch (slot) {
            case MAINHAND -> new Builder(SlotTrigger.MAINHAND);
            case OFFHAND -> new Builder(SlotTrigger.OFFHAND);
            case HEAD -> new Builder(SlotTrigger.HEAD);
            case CHEST -> new Builder(SlotTrigger.CHEST);
            case LEGS -> new Builder(SlotTrigger.LEGS);
            case FEET -> new Builder(SlotTrigger.FEET);
            case BODY -> new Builder(SlotTrigger.ANY_ARMOR);
        };
    }

    public static Builder wornInAnyArmorSlot() {
        return new Builder(SlotTrigger.ANY_ARMOR);
    }

    public static Builder inInventory() {
        return new Builder(SlotTrigger.INVENTORY);
    }

    public static final class Builder {
        private final SlotTrigger trigger;
        private Predicate<ItemStack> stackFilter = stack -> true;
        private @Nullable Vec3 gravityVector;
        private double strengthMultiplier = 1.0;
        private @Nullable Integer priority;
        private @Nullable RotationParameters rotationParameters;
        private @Nullable BiFunction<LivingEntity, ItemStack, @Nullable GravityField> dynamicProvider;

        private Builder(SlotTrigger trigger) {
            this.trigger = trigger;
        }

        /** Optional predicate to restrict when the stack applies (e.g. DataComponents, charge, damage). */
        public Builder when(@NotNull Predicate<ItemStack> filter) {
            this.stackFilter = Objects.requireNonNull(filter, "filter");
            return this;
        }

        /** Sets a cardinal gravity direction. Defaults priority to {@link GravityModifier#ITEM_PRIORITY}. */
        public Builder direction(@NotNull Direction direction) {
            Objects.requireNonNull(direction, "direction");
            this.gravityVector = Vec3.atLowerCornerOf(direction.getNormal());
            return this;
        }

        /** Sets a continuous world-space gravity vector. Defaults priority to {@link GravityModifier#ITEM_PRIORITY}. */
        public Builder gravityVector(@NotNull Vec3 gravityVector) {
            this.gravityVector = GravityField.normalizeGravityVector(gravityVector);
            return this;
        }

        /**
         * Sets the gravity strength multiplier (e.g. {@code 0.25} for moon boots, {@code 0.0} for zero-G).
         * If no direction or vector is configured, the rule modifies strength only without
         * overriding the entity's gravity direction.
         */
        public Builder strength(double strengthMultiplier) {
            if (!Double.isFinite(strengthMultiplier)) {
                throw new IllegalArgumentException("Strength multiplier must be finite.");
            }
            this.strengthMultiplier = strengthMultiplier;
            return this;
        }

        /** Overrides the default modifier priority. */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /** Sets custom rotation parameters when this item changes gravity direction. */
        public Builder rotation(@Nullable RotationParameters rotationParameters) {
            this.rotationParameters = rotationParameters;
            return this;
        }

        /**
         * Supplies a dynamic provider evaluated each server tick when the item matches its slot trigger.
         * Return {@code null} when the item should currently have no effect.
         */
        public Builder dynamic(@NotNull BiFunction<LivingEntity, ItemStack, @Nullable GravityField> provider) {
            this.dynamicProvider = Objects.requireNonNull(provider, "provider");
            return this;
        }

        public ItemGravityRule build() {
            if (dynamicProvider != null) {
                return new ItemGravityRule(trigger, stackFilter, dynamicProvider);
            }

            final GravityField staticField;
            if (gravityVector != null) {
                int resolvedPriority = priority != null ? priority : GravityModifier.ITEM_PRIORITY;
                staticField = new GravityField(gravityVector, strengthMultiplier, resolvedPriority, rotationParameters);
            } else {
                int resolvedPriority = priority != null ? priority : GravityModifier.DEFAULT_PRIORITY;
                staticField = new GravityField(Direction.DOWN, strengthMultiplier, resolvedPriority, rotationParameters);
            }
            return new ItemGravityRule(trigger, stackFilter, (entity, stack) -> staticField);
        }
    }
}
