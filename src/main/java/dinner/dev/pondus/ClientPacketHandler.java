package dinner.dev.pondus;

import dinner.dev.pondus.api.PondusAPI;import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.network.S2CLevelGravityPacket;
import dinner.dev.pondus.network.S2CZoneVisualizationPacket;
import dinner.dev.pondus.util.EntityGravityData;import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public class ClientPacketHandler {
    public static void handle(S2CLevelGravityPacket s2CLevelGravityPacket) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(s2CLevelGravityPacket.dimension())) {
            PondusAPI.setDimensionGravityStrength(level, s2CLevelGravityPacket.strength());
        }
    }

    public static void handle(S2CEntityGravityPacket s2CEntityGravityPacket) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(s2CEntityGravityPacket.entityID());
        if (entity != null) {
            EntityGravityData entityGravityData = PondusAPI.getGravityData(entity);
            entityGravityData.fromNbt(s2CEntityGravityPacket.data());
            entityGravityData.applyGravityChange();
        }
    }

    public static void handle(S2CZoneVisualizationPacket packet) {
        ClientZoneVisualizer.setZones(packet.dimension(), packet.zones());
    }
}
