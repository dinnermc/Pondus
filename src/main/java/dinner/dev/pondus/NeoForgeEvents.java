package dinner.dev.pondus;

import dinner.dev.pondus.zone.ZoneManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class NeoForgeEvents {

    public static void init() {
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onLevelTick);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!event.getLevel().isClientSide()) {
            ZoneManager.getInstance().tick(event.getLevel());
        }
    }
}
