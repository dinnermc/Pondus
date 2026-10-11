package dinner.dev.pondus;

import dinner.dev.pondus.network.PacketHandlerFabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

public class PondusClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // This code runs when the client is initializing
        // Register render layers, etc.
        
        // Register client-bound packets
        PacketHandlerFabric.registerClientPackets();
    }
}