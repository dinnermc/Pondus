package dinner.dev.pondus;

import dinner.dev.pondus.zone.ZoneManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.Level;

public class CommonEvents {

    public static void initFabric() {
        ServerTickEvents.END_LEVEL_TICK.register(CommonEvents::onLevelTick);
    }

    public static void onLevelTick(Level level) {
        ZoneManager.getInstance().tick(level);
    }
}