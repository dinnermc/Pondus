package dinner.dev.pondus.api;

import dinner.dev.pondus.Pondus;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Built-in {@link GravitySource} that evaluates registered {@link ItemGravityRule}s
 * for held items, worn armor, and inventory charms.
 */
public final class ItemGravityManager implements GravitySource {
    private static final ItemGravityManager INSTANCE = new ItemGravityManager();
    private static final Vec3 DOWN_VECTOR = new Vec3(0, -1, 0);

    private record RegisteredRule(ResourceLocation id, Predicate<ItemStack> matcher, ItemGravityRule rule) {}

    private final Map<ResourceLocation, RegisteredRule> rules = new LinkedHashMap<>();
    private boolean hasInventoryRules = false;

    private ItemGravityManager() {}

    public static ItemGravityManager getInstance() {
        return INSTANCE;
    }

    @Override
    public @NotNull ResourceLocation id() {
        return Pondus.id("item_gravity");
    }

    public synchronized ResourceLocation register(@NotNull Item item, @NotNull ItemGravityRule rule) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(rule, "rule");
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ResourceLocation ruleId = ResourceLocation.fromNamespaceAndPath(
            itemId.getNamespace(),
            "item_gravity/" + itemId.getPath() + "/" + rule.trigger().name().toLowerCase()
        );
        register(ruleId, stack -> stack.is(item), rule);
        return ruleId;
    }

    public synchronized void register(
        @NotNull ResourceLocation ruleId,
        @NotNull Predicate<ItemStack> matcher,
        @NotNull ItemGravityRule rule
    ) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(matcher, "matcher");
        Objects.requireNonNull(rule, "rule");
        rules.put(ruleId, new RegisteredRule(ruleId, matcher, rule));
        recomputeInventoryFlag();
    }

    public synchronized boolean unregister(@NotNull ResourceLocation ruleId) {
        boolean removed = rules.remove(Objects.requireNonNull(ruleId, "ruleId")) != null;
        if (removed) {
            recomputeInventoryFlag();
        }
        return removed;
    }

    private void recomputeInventoryFlag() {
        hasInventoryRules = rules.values().stream()
            .anyMatch(r -> r.rule().trigger() == ItemGravityRule.SlotTrigger.INVENTORY);
    }

    @Override
    public @Nullable GravityField getGravityField(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }

        List<RegisteredRule> snapshot;
        boolean scanInventory;
        synchronized (this) {
            if (rules.isEmpty()) {
                return null;
            }
            snapshot = new ArrayList<>(rules.values());
            scanInventory = hasInventoryRules;
        }

        boolean matchedAny = false;
        double combinedStrength = 1.0;
        GravityField winningDirectionField = null;
        RotationParameters winningRotation = null;
        int winningRotationPriority = Integer.MIN_VALUE;

        for (RegisteredRule entry : snapshot) {
            ItemStack matchedStack = findMatchingStack(living, entry, scanInventory);
            if (matchedStack.isEmpty()) {
                continue;
            }
            GravityField field = entry.rule().evaluate(living, matchedStack);
            if (field == null) {
                continue;
            }
            matchedAny = true;
            combinedStrength *= field.strengthMultiplier();

            boolean overridesDirection = field.priority() > GravityModifier.DEFAULT_PRIORITY
                || field.gravityVector().distanceToSqr(DOWN_VECTOR) > 1.0E-12;
            if (overridesDirection && (winningDirectionField == null || field.priority() > winningDirectionField.priority())) {
                winningDirectionField = field;
            }
            if (field.rotationParameters() != null && field.priority() >= winningRotationPriority) {
                winningRotation = field.rotationParameters();
                winningRotationPriority = field.priority();
            }
        }

        if (!matchedAny) {
            return null;
        }

        if (winningDirectionField == null) {
            return new GravityField(Direction.DOWN, combinedStrength, GravityModifier.DEFAULT_PRIORITY, winningRotation);
        }

        return new GravityField(
            winningDirectionField.gravityVector(),
            combinedStrength,
            winningDirectionField.priority(),
            winningRotation != null ? winningRotation : winningDirectionField.rotationParameters()
        );
    }

    private static ItemStack findMatchingStack(
        LivingEntity living,
        RegisteredRule entry,
        boolean scanInventory
    ) {
        Predicate<ItemStack> matcher = entry.matcher();
        ItemGravityRule rule = entry.rule();
        return switch (rule.trigger()) {
            case MAINHAND -> checkSlot(living, EquipmentSlot.MAINHAND, matcher, rule);
            case OFFHAND -> checkSlot(living, EquipmentSlot.OFFHAND, matcher, rule);
            case EITHER_HAND -> {
                ItemStack main = checkSlot(living, EquipmentSlot.MAINHAND, matcher, rule);
                yield !main.isEmpty() ? main : checkSlot(living, EquipmentSlot.OFFHAND, matcher, rule);
            }
            case HEAD -> checkSlot(living, EquipmentSlot.HEAD, matcher, rule);
            case CHEST -> checkSlot(living, EquipmentSlot.CHEST, matcher, rule);
            case LEGS -> checkSlot(living, EquipmentSlot.LEGS, matcher, rule);
            case FEET -> checkSlot(living, EquipmentSlot.FEET, matcher, rule);
            case ANY_ARMOR -> {
                for (EquipmentSlot slot : new EquipmentSlot[] {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                }) {
                    ItemStack stack = checkSlot(living, slot, matcher, rule);
                    if (!stack.isEmpty()) {
                        yield stack;
                    }
                }
                yield ItemStack.EMPTY;
            }
            case INVENTORY -> {
                if (!scanInventory) {
                    yield ItemStack.EMPTY;
                }
                if (living instanceof Player player) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (matches(stack, matcher, rule)) {
                            yield stack;
                        }
                    }
                    for (ItemStack stack : player.getInventory().armor) {
                        if (matches(stack, matcher, rule)) {
                            yield stack;
                        }
                    }
                    for (ItemStack stack : player.getInventory().offhand) {
                        if (matches(stack, matcher, rule)) {
                            yield stack;
                        }
                    }
                    yield ItemStack.EMPTY;
                }
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack stack = checkSlot(living, slot, matcher, rule);
                    if (!stack.isEmpty()) {
                        yield stack;
                    }
                }
                yield ItemStack.EMPTY;
            }
        };
    }

    private static ItemStack checkSlot(
        LivingEntity living,
        EquipmentSlot slot,
        Predicate<ItemStack> matcher,
        ItemGravityRule rule
    ) {
        ItemStack stack = living.getItemBySlot(slot);
        return matches(stack, matcher, rule) ? stack : ItemStack.EMPTY;
    }

    private static boolean matches(ItemStack stack, Predicate<ItemStack> matcher, ItemGravityRule rule) {
        return !stack.isEmpty() && matcher.test(stack) && rule.matchesStack(stack);
    }
}
