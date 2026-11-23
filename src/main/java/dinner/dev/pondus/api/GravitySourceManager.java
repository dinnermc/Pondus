package dinner.dev.pondus.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Internal server-tick integration for registered gravity sources.
 */
public final class GravitySourceManager {
    private static final String FIELD_SOURCE_PREFIX = "field:";
    private static final String LEGACY_ZONE_SOURCE_PREFIX = "zone:";
    private static final Map<ResourceLocation, GravitySource> GLOBAL_SOURCES = new LinkedHashMap<>();
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, GravitySource>> LEVEL_SOURCES = new LinkedHashMap<>();

    private record RegisteredSource(ResourceLocation id, GravitySource source) {}

    private GravitySourceManager() {}

    public static synchronized void register(@NotNull GravitySource source) {
        Objects.requireNonNull(source, "source");
        ResourceLocation id = Objects.requireNonNull(source.id(), "source id");
        GravitySource existing = GLOBAL_SOURCES.putIfAbsent(id, source);
        if (existing != null && existing != source) {
            throw new IllegalArgumentException("A gravity source is already registered with id " + id + ".");
        }
    }

    /**
     * Registers (or replaces) a world/dimension-scoped gravity source (e.g. for a BlockEntity).
     * Level-scoped sources are only evaluated for entities in that dimension and are
     * automatically cleared when the server stops so they never leak across worlds.
     */
    public static synchronized void registerForLevel(@NotNull ResourceKey<Level> dimension, @NotNull GravitySource source) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(source, "source");
        ResourceLocation id = Objects.requireNonNull(source.id(), "source id");
        LEVEL_SOURCES.computeIfAbsent(dimension, k -> new LinkedHashMap<>()).put(id, source);
    }

    public static synchronized boolean unregister(@NotNull ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        boolean removed = GLOBAL_SOURCES.remove(id) != null;
        for (Map<ResourceLocation, GravitySource> map : LEVEL_SOURCES.values()) {
            if (map.remove(id) != null) {
                removed = true;
            }
        }
        return removed;
    }

    public static synchronized boolean unregisterForLevel(@NotNull ResourceKey<Level> dimension, @NotNull ResourceLocation id) {
        Map<ResourceLocation, GravitySource> map = LEVEL_SOURCES.get(Objects.requireNonNull(dimension, "dimension"));
        return map != null && map.remove(Objects.requireNonNull(id, "id")) != null;
    }

    public static synchronized void clearLevelSources() {
        LEVEL_SOURCES.clear();
    }

    public static void tick(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || level.isClientSide()) {
            return;
        }

        ArrayList<RegisteredSource> sources;
        synchronized (GravitySourceManager.class) {
            Map<ResourceLocation, GravitySource> dimSources = LEVEL_SOURCES.get(serverLevel.dimension());
            int capacity = GLOBAL_SOURCES.size() + (dimSources == null ? 0 : dimSources.size());
            sources = new ArrayList<>(capacity);
            GLOBAL_SOURCES.forEach((id, source) -> sources.add(new RegisteredSource(id, source)));
            if (dimSources != null) {
                dimSources.forEach((id, source) -> sources.add(new RegisteredSource(id, source)));
            }
        }

        for (Entity entity : serverLevel.getAllEntities()) {
            if (!PondusAPI.canChangeGravity(entity)) {
                removeManagedModifiers(entity);
                continue;
            }

            Set<String> activeSourceIds = new HashSet<>();
            for (RegisteredSource registeredSource : sources) {
                GravityField field = registeredSource.source().getGravityField(entity);
                if (field == null) {
                    continue;
                }

                String sourceId = FIELD_SOURCE_PREFIX + registeredSource.id();
                activeSourceIds.add(sourceId);
                GravityModifier modifier = new GravityModifier(
                    modifierId(registeredSource.id(), entity.getUUID()),
                    sourceId,
                    field.gravityVector(),
                    field.strengthMultiplier(),
                    field.priority(),
                    field.rotationParameters(),
                    GravityModifier.PERMANENT,
                    field.absoluteStrength()
                );
                var existing = PondusAPI.getModifierBySource(entity, sourceId);
                if (existing.isEmpty() || !existing.get().equals(modifier)) {
                    PondusAPI.addModifier(entity, modifier);
                }
            }

            for (GravityModifier modifier : PondusAPI.getModifiers(entity)) {
                String sourceId = modifier.sourceId();
                if ((sourceId.startsWith(FIELD_SOURCE_PREFIX) && !activeSourceIds.contains(sourceId))
                    || sourceId.startsWith(LEGACY_ZONE_SOURCE_PREFIX)) {
                    PondusAPI.removeModifier(entity, modifier.id());
                }
            }
        }
    }

    private static void removeManagedModifiers(Entity entity) {
        for (GravityModifier modifier : PondusAPI.getModifiers(entity)) {
            if (modifier.sourceId().startsWith(FIELD_SOURCE_PREFIX)
                || modifier.sourceId().startsWith(LEGACY_ZONE_SOURCE_PREFIX)) {
                PondusAPI.removeModifier(entity, modifier.id());
            }
        }
    }

    private static UUID modifierId(ResourceLocation sourceId, UUID entityId) {
        return UUID.nameUUIDFromBytes(
            (FIELD_SOURCE_PREFIX + sourceId + ":" + entityId).getBytes(StandardCharsets.UTF_8)
        );
    }
}
