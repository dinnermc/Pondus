package dinner.dev.pondus;

import dinner.dev.pondus.zone.ZoneManager;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class NeoForgeEvents {
    @SubscribeEvent
    public static void onLevelTick(LevelEvent.Tick event) {
        if (!event.getLevel().isClientSide()) {
            ZoneManager.getInstance().tick(event.getLevel());
        }
    }
}