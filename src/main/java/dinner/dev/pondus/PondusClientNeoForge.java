package dinner.dev.pondus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = Pondus.MOD_ID,dist = Dist.CLIENT)
public class PondusClientNeoForge {

    public PondusClientNeoForge(IEventBus modBus) {
        modBus.addListener(ZoneVisualizationKey::register);
    }
}
