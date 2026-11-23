package dinner.dev.pondus;

import dinner.dev.pondus.api.GravitySourceManager;
import dinner.dev.pondus.api.ItemGravityManager;
import dinner.dev.pondus.zone.ZoneManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

public class NeoForgeEvents {

    public static void init() {
        GravitySourceManager.register(ZoneManager.getInstance());
        GravitySourceManager.register(ItemGravityManager.getInstance());
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::onServerStopped);
    }

    private static void onLevelTick(LevelTickEvent.Pre event) {
        if (!event.getLevel().isClientSide()) {
            GravitySourceManager.tick(event.getLevel());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        GravitySourceManager.clearLevelSources();
    }
}
