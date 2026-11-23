package dinner.dev.pondus;

import com.mojang.blaze3d.platform.InputConstants;
import dinner.dev.pondus.network.C2SRequestZoneVisualizationPacket;
import dinner.dev.pondus.platform.Services;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

public final class ZoneVisualizationKey {
    private static final KeyMapping TOGGLE = new KeyMapping(
        "key.pondus.toggle_zone_visualization",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G,
        "key.categories.pondus"
    );
    private static ResourceKey<Level> lastRequestedDimension;

    private ZoneVisualizationKey() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE);
        ClientTickEvents.END_CLIENT_TICK.register(ZoneVisualizationKey::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(ClientZoneVisualizer::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            lastRequestedDimension = null;
            ClientZoneVisualizer.reset();
        });
    }

    private static void onClientTick(Minecraft minecraft) {
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
            ResourceKey<Level> currentDimension = minecraft.level.dimension();
            if (!currentDimension.equals(lastRequestedDimension)
                && !ClientZoneVisualizer.hasZonesFor(currentDimension)) {
                lastRequestedDimension = currentDimension;
                Services.PLATFORM.sendToServer(new C2SRequestZoneVisualizationPacket());
            }
        }
    }
}
