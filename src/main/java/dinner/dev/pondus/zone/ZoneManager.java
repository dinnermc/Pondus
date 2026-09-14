package dinner.dev.pondus.zone;

import com.google.common.collect.Maps;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.IEntityGravityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import java.util.Map;

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

    /** Remove zone by id string. */
    public void removeZone(String id) {
        zones.remove(id);
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
                IEntityGravityData data = PondusAPI.getGravityData(entity);
                if (data != null) {
                    data.applyGravityDirectionEffect(zone.getDirection(), null, zone.getStrength());
                }
            }
        }
    }
}