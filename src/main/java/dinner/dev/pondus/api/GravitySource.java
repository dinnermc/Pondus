package dinner.dev.pondus.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A mod-provided source of gravity, evaluated on the logical server for each entity each tick.
 *
 * Implementations should keep this lookup inexpensive. Return {@code null} when the entity is
 * outside the source's influence. A source may provide at most one combined field per entity.
 */
public interface GravitySource {
    /** A globally unique, namespaced identifier owned by the registering mod. */
    @NotNull ResourceLocation id();

    /** Returns the current contribution for this entity, or {@code null} when it is inactive. */
    @Nullable GravityField getGravityField(Entity entity);
}
