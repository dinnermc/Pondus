package dinner.dev.pondus.api;

import net.minecraft.world.entity.Entity;

public class GravityUpdateEvent {
    private final Entity entity;
    private final IEntityGravityData gravityData;

    public GravityUpdateEvent(Entity entity, IEntityGravityData gravityData) {
        this.entity = entity;
        this.gravityData = gravityData;
    }

    public Entity getEntity() {
        return entity;
    }

    public IEntityGravityData getGravity() {
        return gravityData;
    }
}
