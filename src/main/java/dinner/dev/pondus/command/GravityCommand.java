package dinner.dev.pondus.command;

import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.util.GCUtil;
import dinner.dev.pondus.util.RotationUtil;
import dinner.dev.pondus.zone.Zone;
import dinner.dev.pondus.zone.ZoneManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GravityCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands
            .literal("gravity")
            .requires(source -> source.hasPermission(2));

        builder.then(Commands.literal("set_base_direction")
            .then(Commands.argument("direction", StringArgumentType.word())
                .executes(context -> {
                    Entity entity = context.getSource().getEntity();
                    Validate.isTrue(entity != null);
                    String dirStr = StringArgumentType.getString(context, "direction").toUpperCase();
                    Direction direction;
                    switch (dirStr) {
                        case "DOWN": direction = Direction.DOWN; break;
                        case "UP": direction = Direction.UP; break;
                        case "NORTH": direction = Direction.NORTH; break;
                        case "SOUTH": direction = Direction.SOUTH; break;
                        case "EAST": direction = Direction.EAST; break;
                        case "WEST": direction = Direction.WEST; break;
                        default:
                            throw new IllegalArgumentException("Invalid direction: " + dirStr);
                    }
                    PondusAPI.setBaseGravityDirection(entity, direction);
                    return 1;
                })
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> {
                        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                        String dirStr = StringArgumentType.getString(context, "direction").toUpperCase();
                        Direction direction;
                        switch (dirStr) {
                            case "DOWN": direction = Direction.DOWN; break;
                            case "UP": direction = Direction.UP; break;
                            case "NORTH": direction = Direction.NORTH; break;
                            case "SOUTH": direction = Direction.SOUTH; break;
                            case "EAST": direction = Direction.EAST; break;
                            case "WEST": direction = Direction.WEST; break;
                            default:
                                throw new IllegalArgumentException("Invalid direction: " + dirStr);
                        }
                        for (Entity entity : entities) {
                            PondusAPI.setBaseGravityDirection(entity, direction);
                        }
                        return entities.size();
                    })
                )
            )
        );

        builder.then(Commands.literal("reset")
            .executes(context -> {
                Entity entity = context.getSource().getEntity();
                Validate.isTrue(entity != null);
                PondusAPI.resetGravity(entity);
                return 1;
            })
            .then(Commands.argument("entities", EntityArgument.entities())
                .executes(context -> {
                    Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                    for (Entity entity : entities) {
                        PondusAPI.resetGravity(entity);
                    }
                    return entities.size();
                })
            )
        );

        builder.then(Commands.literal("set_base_strength")
            .then(Commands.argument("strength", DoubleArgumentType.doubleArg(-20, 20))
                .executes(context -> {
                    Entity entity = context.getSource().getEntity();
                    Validate.isTrue(entity != null);
                    double strength = DoubleArgumentType.getDouble(context, "strength");
                    return executeSetBaseStrength(List.of(entity), strength);
                })
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> {
                        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                        double strength = DoubleArgumentType.getDouble(context, "strength");
                        return executeSetBaseStrength(entities, strength);
                    })
                )
            )
        );

        builder.then(Commands.literal("view")
            .executes(context -> {
                Entity entity = context.getSource().getEntityOrException();

                IEntityGravityData component = PondusAPI.getGravityData(entity);

                context.getSource().sendSuccess(
                    () -> Component.translatable(
                        "gravity_changer.command.inform",
                        component.getBaseGravityDirection().getName(),
                        component.getBaseGravityStrength()
                    ), false
                );

                return 0;
            })
        );

        builder.then(Commands.literal("randomize_base_direction")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                Entity entity = source.getEntity();
                Validate.isTrue(entity != null);
                return executeRandomizeBaseDirection(source, List.of(entity));
            })
            .then(Commands.argument("entities", EntityArgument.entities())
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");
                    return executeRandomizeBaseDirection(source, entities);
                })
            )
        );

        builder.then(Commands.literal("set_relative_base_direction")
            .then(Commands.argument("relativeDirection", StringArgumentType.word())
                .executes(context -> {
                    String relStr = StringArgumentType.getString(context, "relativeDirection").toUpperCase();
                    Entity entity = context.getSource().getEntity();
                    Validate.isTrue(entity != null);

                    return executeSetRelativeBaseDir(
                        context.getSource(), relStr,
                        List.of(entity)
                    );
                })
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> {
                        String relStr = StringArgumentType.getString(context, "relativeDirection").toUpperCase();
                        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "entities");

                        return executeSetRelativeBaseDir(
                            context.getSource(), relStr,
                            entities
                        );
                    })
                )
            )
        );

        builder.then(Commands.literal("set_dimension_gravity_strength")
            .then(Commands.argument("strength", DoubleArgumentType.doubleArg(-20, 20))
                .executes(context -> {
                    ServerLevel world = context.getSource().getLevel();
                    double strength = DoubleArgumentType.getDouble(context, "strength");
                    PondusAPI.setDimensionGravityStrength(world, strength);
                    return 0;
                })
            )
        );

        builder.then(Commands.literal("view_dimension_info")
            .executes(context -> {
                ServerLevel world = context.getSource().getLevel();
                double strength = PondusAPI.getDimensionGravityStrength(world);
                context.getSource().sendSuccess(
                    () -> Component.translatable("gravity_changer.command.dimension_info", strength), false
                );
                return 0;
            })
        );

        // Zone subcommands
        builder.then(Commands.literal("zone")
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("x1", IntegerArgumentType.integer())
                        .then(Commands.argument("y1", IntegerArgumentType.integer())
                            .then(Commands.argument("z1", IntegerArgumentType.integer())
                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                    .then(Commands.argument("y2", IntegerArgumentType.integer())
                                        .then(Commands.argument("z2", IntegerArgumentType.integer())
                                            .then(Commands.argument("direction", StringArgumentType.word())
                                                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0, 20))
                                                    .executes(ctx -> {
                                                        String id = StringArgumentType.getString(ctx, "id");
                                                        int x1 = IntegerArgumentType.getInteger(ctx, "x1");
                                                        int y1 = IntegerArgumentType.getInteger(ctx, "y1");
                                                        int z1 = IntegerArgumentType.getInteger(ctx, "z1");
                                                        int x2 = IntegerArgumentType.getInteger(ctx, "x2");
                                                        int y2 = IntegerArgumentType.getInteger(ctx, "y2");
                                                        int z2 = IntegerArgumentType.getInteger(ctx, "z2");
                                                        BlockPos pos1 = new BlockPos(x1, y1, z1);
                                                        BlockPos pos2 = new BlockPos(x2, y2, z2);
                                                        String dirStr = StringArgumentType.getString(ctx, "direction").toUpperCase();
                                                        Direction dir;
                                                        switch (dirStr) {
                                                            case "DOWN": dir = Direction.DOWN; break;
                                                            case "UP": dir = Direction.UP; break;
                                                            case "NORTH": dir = Direction.NORTH; break;
                                                            case "SOUTH": dir = Direction.SOUTH; break;
                                                            case "EAST": dir = Direction.EAST; break;
                                                            case "WEST": dir = Direction.WEST; break;
                                                            default:
                                                                throw new IllegalArgumentException("Invalid direction: " + dirStr);
                                                        }
                                                        double strength = DoubleArgumentType.getDouble(ctx, "strength");
                                                        if (strength <= 0) strength = 1.0;
                                                        Zone existing = ZoneManager.getInstance().getZone(id);
                                                        if (existing != null) {
                                                            ctx.getSource().sendFailure(Component.literal("A zone with ID " + id + " already exists."));
                                                            return 0;
                                                        }
                                                        Zone zone = new Zone(id, pos1, pos2, dir, strength);
                                                        ZoneManager.getInstance().addZone(zone);
                                                        ctx.getSource().sendSuccess(() -> Component.literal("Zone created: " + id), true);
                                                        return 1;
                                                    })
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
                .then(Commands.literal("remove")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            Zone zone = ZoneManager.getInstance().getZone(id);
                            if (zone == null) {
                                ctx.getSource().sendFailure(Component.literal("No zone with ID " + id + " found."));
                                return 0;
                            }
                            ZoneManager.getInstance().removeZone(id);
                            ctx.getSource().sendSuccess(() -> Component.literal("Zone removed: " + id), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        Map<String, Zone> zones = ZoneManager.getInstance().getZones();
                        if (zones.isEmpty()) {
                            ctx.getSource().sendSuccess(() -> Component.literal("No zones defined."), true);
                        } else {
                            StringBuilder sb = new StringBuilder("Zones: ");
                            for (Map.Entry<String, Zone> e : zones.entrySet()) {
                                Zone zone = e.getValue();
                                sb.append("[")
                                  .append(zone.getId())
                                  .append("] ")
                                  .append(zone.getPos1())
                                  .append(" to ")
                                  .append(zone.getPos2())
                                  .append(" dir=")
                                  .append(zone.getDirection())
                                  .append(" strength=")
                                  .append(zone.getStrength())
                                  .append(" ");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), true);
                        }
                        return 0;
                    })
                )
            );

        // Modifier subcommands
        builder.then(Commands.literal("modifier")
            .then(Commands.literal("add")
                .then(Commands.argument("source", StringArgumentType.word())
                    .then(Commands.argument("direction", StringArgumentType.word())
                        .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.1, 100))
                            .executes(ctx -> executeAddModifier(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                            .then(Commands.argument("entities", EntityArgument.entities())
                                .executes(ctx -> executeAddModifier(ctx.getSource(), EntityArgument.getEntities(ctx, "entities")))
                            )
                            .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(ctx -> executeAddModifier(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                                .then(Commands.argument("entities", EntityArgument.entities())
                                    .executes(ctx -> executeAddModifier(ctx.getSource(), EntityArgument.getEntities(ctx, "entities")))
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("modifierId", StringArgumentType.word())
                    .executes(ctx -> executeRemoveModifier(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                    .then(Commands.argument("entities", EntityArgument.entities())
                        .executes(ctx -> executeRemoveModifier(ctx.getSource(), EntityArgument.getEntities(ctx, "entities")))
                    )
                )
                .then(Commands.literal("source")
                    .then(Commands.argument("sourceId", StringArgumentType.word())
                        .executes(ctx -> executeRemoveModifiersBySource(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                        .then(Commands.argument("entities", EntityArgument.entities())
                            .executes(ctx -> executeRemoveModifiersBySource(ctx.getSource(), EntityArgument.getEntities(ctx, "entities")))
                        )
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(ctx -> executeListModifiers(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(ctx -> executeListModifiers(ctx.getSource(), EntityArgument.getEntities(ctx, "entities")))
                )
            )
        );

        dispatcher.register(builder);
    }

    private static int executeSetBaseStrength(Collection<? extends Entity> entities, double strength) {
        for (Entity entity : entities) {
            PondusAPI.setBaseGravityStrength(entity, strength);
        }
        return entities.size();
    }

    private static int executeRandomizeBaseDirection(CommandSourceStack source, Collection<? extends Entity> entities) {
        RandomSource random = source.getLevel().random;
        for (Entity entity : entities) {
            Direction gravityDirection = Direction.getRandom(random);
            PondusAPI.setBaseGravityDirection(entity, gravityDirection);
        }
        return entities.size();
    }

    private static void getSendFeedback(CommandSourceStack source, Entity entity, Direction gravityDirection) {
        Component text = GCUtil.getDirectionText(gravityDirection);
        if (source.getEntity() != null && source.getEntity() == entity) {
            source.sendSuccess(() -> Component.translatable("commands.gravity.get.self", text), true);
        }
        else {
            source.sendSuccess(() -> Component.translatable("commands.gravity.get.other", entity.getDisplayName(), text), true);
        }
    }

    private static int executeSetRelativeBaseDir(
        CommandSourceStack source, String relStr,
        Collection<? extends Entity> entities
    ) {
        int i = 0;
        for (Entity entity : entities) {
            Direction gravityDirection = PondusAPI.getGravityDirection(entity);
            Direction combinedRelativeDirection;
            switch (relStr) {
                case "DOWN":
                    combinedRelativeDirection = Direction.DOWN;
                    break;
                case "UP":
                    combinedRelativeDirection = Direction.UP;
                    break;
                case "LEFT":
                    combinedRelativeDirection = Direction.from2DDataValue(-1 + Direction.fromYRot(entity.getYRot()).get2DDataValue());
                    break;
                case "RIGHT":
                    combinedRelativeDirection = Direction.from2DDataValue(1 + Direction.fromYRot(entity.getYRot()).get2DDataValue());
                    break;
                case "FORWARD":
                case "BACKWARD":
                    combinedRelativeDirection = Direction.from2DDataValue(0 + Direction.fromYRot(entity.getYRot()).get2DDataValue());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid relative direction: " + relStr);
            }
            Direction newGravityDirection = RotationUtil.dirPlayerToWorld(combinedRelativeDirection, gravityDirection);
            PondusAPI.setBaseGravityDirection(entity, newGravityDirection);

            getSendFeedback(source, entity, newGravityDirection);
            i++;
        }
        return i;
    }

    private static int executeAddModifier(CommandSourceStack source, Collection<? extends Entity> entities) {
        String sourceId = StringArgumentType.getString(source, "source");
        String dirStr = StringArgumentType.getString(source, "direction").toUpperCase();
        double strength = DoubleArgumentType.getDouble(source, "strength");
        int priority = source.getArgument("priority", Integer.class, 0);

        Direction direction;
        switch (dirStr) {
            case "DOWN": direction = Direction.DOWN; break;
            case "UP": direction = Direction.UP; break;
            case "NORTH": direction = Direction.NORTH; break;
            case "SOUTH": direction = Direction.SOUTH; break;
            case "EAST": direction = Direction.EAST; break;
            case "WEST": direction = Direction.WEST; break;
            default:
                throw new IllegalArgumentException("Invalid direction: " + dirStr);
        }

        int count = 0;
        for (Entity entity : entities) {
            UUID modId = UUID.randomUUID();
            GravityModifier modifier = new GravityModifier(
                UUID.randomUUID(),
                sourceId,
                direction,
                strength,
                priority,
                null
            );
            PondusAPI.addModifier(entity, modifier);
            count++;
        }

        source.sendSuccess(() -> Component.translatable(
            "commands.gravity.modifier.added", sourceId, direction.getName(), strength, priority
        ), true);

        return count;
    }

    private static int executeRemoveModifier(CommandSourceStack source, Collection<? extends Entity> entities) {
        String idStr = StringArgumentType.getString(source, "modifierId");
        UUID modId;
        try {
            modId = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid UUID: " + idStr));
            return 0;
        }

        int count = 0;
        for (Entity entity : entities) {
            PondusAPI.removeModifier(entity, modId);
            count++;
        }

        source.sendSuccess(() -> Component.translatable(
            "commands.gravity.modifier.removed", idStr
        ), true);

        return count;
    }

    private static int executeRemoveModifiersBySource(CommandSourceStack source, Collection<? extends Entity> entities) {
        String sourceId = StringArgumentType.getString(source, "sourceId");

        int count = 0;
        for (Entity entity : entities) {
            PondusAPI.removeModifiersBySource(entity, sourceId);
            count++;
        }

        source.sendSuccess(() -> Component.translatable(
            "commands.gravity.modifier.removed_source", sourceId
        ), true);

        return count;
    }

    private static int executeListModifiers(CommandSourceStack source, Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            List<GravityModifier> modifiers = PondusAPI.getModifiers(entity);
            if (modifiers.isEmpty()) {
                source.sendSuccess(() -> Component.translatable(
                    "commands.gravity.modifier.none", entity.getDisplayName()
                ), false);
            } else {
                StringBuilder sb = new StringBuilder("Modifiers for ").append(entity.getDisplayName()).append(":\n");
                for (GravityModifier m : modifiers) {
                    sb.append("  [")
                      .append(m.id().toString().substring(0, 8))
                      .append("] source=").append(m.sourceId())
                      .append(" dir=").append(m.direction().getName())
                      .append(" strength=").append(m.strengthMultiplier())
                      .append(" priority=").append(m.priority())
                      .append("\n");
                }
                source.sendSuccess(() -> Component.literal(sb.toString()), false);
            }
        }
        return entities.size();
    }
}