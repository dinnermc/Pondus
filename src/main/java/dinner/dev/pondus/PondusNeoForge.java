package dinner.dev.pondus;

import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.command.GravityCommand;
import dinner.dev.pondus.network.PacketHandlerNeoForge;
import dinner.dev.pondus.network.S2CEntityGravityPacket;
import dinner.dev.pondus.network.S2CLevelGravityPacket;
import dinner.dev.pondus.network.S2CZoneVisualizationPacket;
import dinner.dev.pondus.platform.Services;
import dinner.dev.pondus.util.EntityGravityData;
import dinner.dev.pondus.zone.ZoneManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Pondus.MOD_ID)
public class PondusNeoForge {

    public PondusNeoForge(IEventBus bus) {
        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        bus.addListener(this::registerCaps);
        bus.addListener(this::setup);
        bus.addListener(this::register);
        bus.addListener(PacketHandlerNeoForge::register);
        // Use Forge to bootstrap the Common mod.
        Pondus.init();
        //NeoForge.EVENT_BUS.addGenericListener(Entity.class,this::attachEntity);
        //NeoForge.EVENT_BUS.addGenericListener(Level.class,this::attachLevel);
        NeoForge.EVENT_BUS.addListener(this::login);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::tracking);
        NeoForge.EVENT_BUS.addListener(this::clonePlayer);
        NeoForge.EVENT_BUS.addListener(this::respawn);
        NeoForge.EVENT_BUS.addListener(this::dimensionChange);
        NeoForgeEvents.init();
    }

    void register(RegisterEvent event) {
        if (event.getRegistry() == BuiltInRegistries.BLOCK) {
            Pondus.register();
        }
    }

    void setup(FMLCommonSetupEvent event) {
    }

    void login(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = player.serverLevel();
        syncLevelGravity(player, level);
        syncPlayerGravity(player);
    }

    void clonePlayer(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !Pondus.config.resetGravityOnRespawn) {
            CompoundTag data = new CompoundTag();
            PondusAPI.getGravityData(event.getOriginal()).toNbt(data);
            PondusAPI.getGravityData(event.getEntity()).fromNbt(data);
        }
    }

    void respawn(PlayerEvent.PlayerRespawnEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = player.serverLevel();
        syncLevelGravity(player, level);
        syncPlayerGravity(player);
    }

    void dimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = player.server.getLevel(event.getTo());
        if (level != null) {
            syncLevelGravity(player, level);
            Services.PLATFORM.sendToClient(
                S2CZoneVisualizationPacket.fromZones(
                    level.dimension(),
                    ZoneManager.getInstance().getZones(level).values()
                ),
                player
            );
        }
        syncPlayerGravity(player);
    }

    private static void syncLevelGravity(ServerPlayer player, ServerLevel level) {
        Services.PLATFORM.sendToClient(
            new S2CLevelGravityPacket(level.dimension(), PondusAPI.getDimensionGravityStrength(level)),
            player
        );
    }

    private static void syncPlayerGravity(ServerPlayer player) {
        EntityGravityData entityGravityData = PondusAPI.getGravityData(player);
        CompoundTag data = new CompoundTag();
        entityGravityData.toNbt(data);
        Services.PLATFORM.sendToTracking(new S2CEntityGravityPacket(player, data), player, true);
    }

    void tracking(PlayerEvent.StartTracking event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();

        EntityGravityData targetEntityGravitydata = PondusAPI.getGravityData(target);

        CompoundTag data = new CompoundTag();
        targetEntityGravitydata.toNbt(data);
        Services.PLATFORM.sendToClient(new S2CEntityGravityPacket(target, data),
                (ServerPlayer) player);

    }

    void commands(RegisterCommandsEvent event) {
        GravityCommand.register(event.getDispatcher());
    }


    void registerCaps(RegisterCapabilitiesEvent event) {

    }
}