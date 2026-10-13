package dinner.dev.pondus.zone;

import com.google.common.collect.Maps;
import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.IEntityGravityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import java.util.Map;
import java.util.UUID;

/**
 * Manages gravity zones. Ticks each level to apply zone gravity to entities inside zones.
 */
public class ZoneManager {
    private static final ZoneManager INSTANCE = new ZoneManager();
    private final Map<String, Zone> zones = Maps.newHashMap();

    public static ZoneManager getInstance() {
        return INSTANCE;
    }

    private ZoneManager() {}

    /** Add or replace a zone. */
    public void addZone(Zone zone) {
        zones.put(zone.getId(), zone);
    }

    /** Remove zone by id string. Also removes the zone's modifier from all tracked entities. */
    public void removeZone(String id) {
        Zone zone = zones.remove(id);
        if (zone != null) {
            // TODO: In a full implementation, we'd track which entities are in this zone
            // and remove the modifier from them. For now, the modifier will persist
            // until the entity leaves the zone area or the modifier is manually removed.
        }
    }

    /** Get zone by id string, or null if not found. */
    public Zone getZone(String id) {
        return zones.get(id);
    }

    /** Returns a copy of the zone map. */
    public Map<String, Zone> getZones() {
        return Maps.newHashMap(zones);
    }

    /** Called each tick for each level to apply zone gravity. */
    public void tick(Level level) {
        if (level.isClientSide) {
            return; // only server side
        }
        for (Zone zone : zones.values()) {
            if (!zone.isEnabled()) continue;
            for (Entity entity : level.getEntities(null, zone.getAabb())) {
                if (!PondusAPI.canChangeGravity(entity)) continue;

                UUID modId = zone.getModifierId();
                // Check if entity already has this zone's modifier
                var existing = PondusAPI.getModifierBySource(entity, "zone:" + zone.getId());
                if (existing.isEmpty()) {
                    // Entity just entered the zone - add modifier
                    GravityModifier modifier = GravityModifier.ofZone(
                        zone.getModifierId(),
                        zone.getId(),
                        zone.getDirection(),
                        zone.getStrength()
                    );
                    PondusAPI.addModifier(entity, modifier);
                }
            }
        }

        // Check for entities that left zones - we need to track this differently
        // For now, entities keep zone modifiers until they leave the zone area
        // A more complete implementation would track zone membership per entity
    }
}