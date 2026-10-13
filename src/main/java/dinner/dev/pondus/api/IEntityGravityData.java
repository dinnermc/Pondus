package dinner.dev.pondus.api;

import dinner.dev.pondus.RotationAnimation;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IEntityGravityData {
    // Base gravity (set by commands, saved to NBT)
    double getBaseGravityStrength();
    Direction getBaseGravityDirection();

    void setBaseGravityDirection(Direction gravityDirection);

    // Current computed gravity
    Direction getCurrGravityDirection();
    double getCurrGravityStrength();

    void setBaseGravityStrength(double strength);
    void reset();

    // Modifier stack API
    void addModifier(GravityModifier modifier);
    void removeModifier(UUID modifierId);
    void removeModifiersBySource(String sourceId);
    List<GravityModifier> getModifiers();

    // Legacy compatibility (redirect to modifier stack)
    @Deprecated
    void applyGravityDirectionEffect(
            @NotNull Direction direction,
            @Nullable RotationParameters rotationParameters,
            double priority
    );
    @Deprecated
    void applyGravityStrengthEffect(double strengthMultiplier);

    void updateGravityStatus(boolean sendPacketIfNecessary);
    void forceApplyGravityChange();
    RotationAnimation getRotationAnimation();

    void applyGravityChange();

    void commonTick();

    void toNbt(CompoundTag tag);
    void fromNbt(CompoundTag tag);
}