package dinner.dev.pondus;

import dinner.dev.pondus.api.PondusAPI;import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.network.S2CLevelGravityPacket;
import dinner.dev.pondus.util.EntityGravityData;import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public class ClientPacketHandler {
    public static void handle(S2CLevelGravityPacket s2CLevelGravityPacket) {
        ClientLevel level = Minecraft.getInstance().level;
        //level.getCapability(PondusAPIForge.LEVEL_GRAVITY).ifPresent(iLevelGravityData -> iLevelGravityData.fromNbt(s2CLevelGravityPacket.data()));
    }

    public static void handle(S2CEntityGravityPacket s2CEntityGravityPacket) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level.getEntity(s2CEntityGravityPacket.entityID());
        if (entity != null) {

            EntityGravityData entityGravityData = PondusAPI.getGravityData(entity);

            entityGravityData.fromNbt(s2CEntityGravityPacket.data());
            entityGravityData.applyGravityChange();
        }
    }
}
