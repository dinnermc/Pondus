package dinner.dev.pondus;

import com.mojang.blaze3d.platform.InputConstants;
import dinner.dev.pondus.network.C2SRequestZoneVisualizationPacket;
import dinner.dev.pondus.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ZoneVisualizationKey {
    private static final KeyMapping TOGGLE = new KeyMapping(
        "key.pondus.toggle_zone_visualization",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_F9,
        "key.categories.pondus"
    );

    private ZoneVisualizationKey() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        NeoForge.EVENT_BUS.addListener(ZoneVisualizationKey::onClientTick);
        NeoForge.EVENT_BUS.addListener(ZoneVisualizationKey::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ZoneVisualizationKey::onLogout);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastRequestedDimension;

    private static void onClientTick(ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            lastRequestedDimension = null;
            return;
        }

        while (TOGGLE.consumeClick()) {
            boolean enabled = ClientZoneVisualizer.toggle();
            if (enabled) {
                lastRequestedDimension = minecraft.level.dimension();
                Services.PLATFORM.sendToServer(new C2SRequestZoneVisualizationPacket());
            }
            player.displayClientMessage(
                Component.translatable(enabled
                    ? "message.pondus.zone_visualization.enabled"
                    : "message.pondus.zone_visualization.disabled"),
                true
            );
        }

        if (ClientZoneVisualizer.isEnabled()) {
            var currentDimension = minecraft.level.dimension();
            if (!currentDimension.equals(lastRequestedDimension)
                && !ClientZoneVisualizer.hasZonesFor(currentDimension)) {
                lastRequestedDimension = currentDimension;
                Services.PLATFORM.sendToServer(new C2SRequestZoneVisualizationPacket());
            }
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        lastRequestedDimension = null;
        ClientZoneVisualizer.reset();
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        ClientZoneVisualizer.render(event);
    }
}
