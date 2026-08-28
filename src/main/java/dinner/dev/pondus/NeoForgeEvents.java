package dinner.dev.pondus;

import dinner.dev.pondus.api.GravityUpdateEvent;
import dinner.dev.pondus.api.IEntityGravityData;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

public class NeoForgeEvents {
    static void init() {
        NeoForge.EVENT_BUS.addListener(NeoForgeEvents::updateGravityAnchor);
    }

    static void updateGravityAnchor(GravityUpdateEvent event) {
        Entity entity = event.getEntity();
        IEntityGravityData entityGravity = event.getGravity();
        CommonEvents.handleGravity(entity,entityGravity);
    }
}
