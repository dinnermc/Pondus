package dinner.dev.pondus;

import dinner.dev.pondus.network.PacketHandlerFabric;
import net.fabricmc.api.ClientModInitializer;

public class PondusClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PacketHandlerFabric.registerClientPackets();
        ZoneVisualizationKey.register();
    }
}
