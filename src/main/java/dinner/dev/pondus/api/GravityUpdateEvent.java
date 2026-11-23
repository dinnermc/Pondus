package dinner.dev.pondus.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;

public class GravityUpdateEvent {
    @FunctionalInterface
    public interface Callback {
        void onGravityUpdate(Entity entity, IEntityGravityData gravityData);
    }

    public static final Event<Callback> EVENT = EventFactory.createArrayBacked(
        Callback.class,
        listeners -> (entity, gravityData) -> {
            for (Callback listener : listeners) {
                listener.onGravityUpdate(entity, gravityData);
            }
        }
    );

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
