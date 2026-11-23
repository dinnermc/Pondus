package dinner.dev.pondus.command;

import dinner.dev.pondus.api.GravityModifier;
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.IEntityGravityData;
import dinner.dev.pondus.util.GCUtil;
import dinner.dev.pondus.util.RotationUtil;
import dinner.dev.pondus.zone.Zone;
import dinner.dev.pondus.zone.ZoneManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
            .then(zoneDefinitionCommand("create", false))
            .then(zoneDefinitionCommand("edit", true))
            .then(Commands.literal("remove")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                        ZoneManager.getInstance().getZones(ctx.getSource().getLevel()).keySet(), suggestionsBuilder
                    ))
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        Zone zone = ZoneManager.getInstance().getZone(id, ctx.getSource().getLevel());
                        if (zone == null) {
                            ctx.getSource().sendFailure(Component.literal("No zone with ID " + id + " found."));
                            return 0;
                        }
                        ZoneManager.getInstance().removeZone(id, ctx.getSource().getLevel());
                        ctx.getSource().sendSuccess(() -> Component.literal("Zone removed: " + id), true);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("enable")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                        ZoneManager.getInstance().getZones(ctx.getSource().getLevel()).keySet(), suggestionsBuilder
                    ))
                    .executes(ctx -> setZoneEnabled(ctx, true))
                )
            )
            .then(Commands.literal("disable")
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, suggestionsBuilder) -> SharedSuggestionProvider.suggest(
                        ZoneManager.getInstance().getZones(ctx.getSource().getLevel()).keySet(), suggestionsBuilder
                    ))
                    .executes(ctx -> setZoneEnabled(ctx, false))
                )
            )
            .then(Commands.literal("status")
                .executes(ctx -> {
                    Entity entity = ctx.getSource().getEntityOrException();
                    ZoneManager zoneManager = ZoneManager.getInstance();
                    StringBuilder status = new StringBuilder("Gravedad: ")
                        .append(PondusAPI.getGravityDirection(entity).getName())
                        .append(" (fuerza ")
                        .append(PondusAPI.getGravityStrength(entity))
                        .append("). Zonas: ");
                    boolean foundZone = false;
                    for (Zone zone : zoneManager.getZones(entity.level()).values()) {
                        double influence = zoneManager.getZoneInfluence(entity, zone);
                        if (influence > 0) {
                            if (foundZone) {
                                status.append(", ");
                            }
                            status.append(zone.getId())
                                .append(zone.contains(entity) ? " (" : " (transición; ")
                                .append(zone.hasCenterGravity()
                                    ? "center"
                                    : zone.getDirectionAt(entity.getBoundingBox().getCenter(),
                                        PondusAPI.getBaseGravityDirection(entity)).getName())
                                .append(" x")
                                .append(zone.getStrength())
                                .append(")");
                            foundZone = true;
                        }
                    }
                    if (!foundZone) {
                        status.append("ninguna");
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(status.toString()), false);
                    return 1;
                })
            )
            .then(Commands.literal("list")
                .executes(ctx -> {
                    Map<String, Zone> zones = ZoneManager.getInstance().getZones(ctx.getSource().getLevel());
                    if (zones.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("No zones defined."), true);
                    } else {
                        StringBuilder sb = new StringBuilder("Zones: ");
                        for (Map.Entry<String, Zone> e : zones.entrySet()) {
                            Zone zone = e.getValue();
                            sb.append("[")
                              .append(zone.getId())
                              .append("] shape=")
                              .append(zone.getShape().name().toLowerCase())
                              .append(zone.getShape() == Zone.Shape.BOX
                                  ? " " + zone.getPos1() + " to " + zone.getPos2()
                                  : " center=" + zone.getCenter()
                                      + (zone.getShape() == Zone.Shape.SPHERE
                                          ? " radius=" + zone.getRadius()
                                          : " axis=" + zone.getAxis().getName()
                                              + " radius=" + zone.getRadius()
                                              + " height=" + zone.getHalfHeight() * 2))
                              .append(" transition=")
                              .append(zone.getTransitionDistance())
                              .append(" dir=")
                              .append(zone.hasCenterGravity() ? "center" : zone.getDirection())
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
                            .executes(ctx -> executeAddModifier(ctx, List.of(ctx.getSource().getEntityOrException())))
                            .then(Commands.argument("entities", EntityArgument.entities())
                                .executes(ctx -> executeAddModifier(ctx, EntityArgument.getEntities(ctx, "entities")))
                            )
                            .then(Commands.argument("priority", IntegerArgumentType.integer())
                                .executes(ctx -> executeAddModifier(ctx, List.of(ctx.getSource().getEntityOrException())))
                                .then(Commands.argument("entities", EntityArgument.entities())
                                    .executes(ctx -> executeAddModifier(ctx, EntityArgument.getEntities(ctx, "entities")))
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("modifierId", StringArgumentType.word())
                    .executes(ctx -> executeRemoveModifier(ctx, List.of(ctx.getSource().getEntityOrException())))
                    .then(Commands.argument("entities", EntityArgument.entities())
                        .executes(ctx -> executeRemoveModifier(ctx, EntityArgument.getEntities(ctx, "entities")))
                    )
                )
                .then(Commands.literal("source")
                    .then(Commands.argument("sourceId", StringArgumentType.word())
                        .executes(ctx -> executeRemoveModifiersBySource(ctx, List.of(ctx.getSource().getEntityOrException())))
                        .then(Commands.argument("entities", EntityArgument.entities())
                            .executes(ctx -> executeRemoveModifiersBySource(ctx, EntityArgument.getEntities(ctx, "entities")))
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

    private static LiteralArgumentBuilder<CommandSourceStack> zoneDefinitionCommand(String name, boolean edit) {
        RequiredArgumentBuilder<CommandSourceStack, String> idArgument =
            Commands.argument("id", StringArgumentType.word());
        if (edit) {
            idArgument.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                ZoneManager.getInstance().getZones(ctx.getSource().getLevel()).keySet(), builder
            ));
        }

        idArgument
            .then(Commands.literal("box")
                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                    .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                        .then(zoneProperties(ctx -> Zone.box(
                            zoneId(ctx),
                            BlockPosArgument.getBlockPos(ctx, "pos1"),
                            BlockPosArgument.getBlockPos(ctx, "pos2"),
                            zoneDirection(ctx),
                            zoneUsesCenterGravity(ctx),
                            zoneStrength(ctx),
                            DoubleArgumentType.getDouble(ctx, "transition")
                        ), edit))
                    )
                )
            )
            .then(Commands.literal("sphere")
                .then(Commands.argument("center", BlockPosArgument.blockPos())
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.000001, 30000000))
                        .then(zoneProperties(ctx -> Zone.sphere(
                            zoneId(ctx),
                            BlockPosArgument.getBlockPos(ctx, "center"),
                            DoubleArgumentType.getDouble(ctx, "radius"),
                            zoneDirection(ctx),
                            zoneUsesCenterGravity(ctx),
                            zoneStrength(ctx),
                            DoubleArgumentType.getDouble(ctx, "transition")
                        ), edit))
                    )
                )
            )
            .then(Commands.literal("cylinder")
                .then(Commands.argument("center", BlockPosArgument.blockPos())
                    .then(Commands.argument("axis", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            List.of("x", "y", "z"), builder
                        ))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0.000001, 30000000))
                            .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.000001, 30000000))
                                .then(zoneProperties(ctx -> Zone.cylinder(
                                    zoneId(ctx),
                                    BlockPosArgument.getBlockPos(ctx, "center"),
                                    zoneAxis(ctx),
                                    DoubleArgumentType.getDouble(ctx, "radius"),
                                    DoubleArgumentType.getDouble(ctx, "height"),
                                    zoneDirection(ctx),
                                    zoneUsesCenterGravity(ctx),
                                    zoneStrength(ctx),
                                    DoubleArgumentType.getDouble(ctx, "transition")
                                ), edit))
                            )
                        )
                    )
                )
            )
            .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                    .then(zoneProperties(ctx -> Zone.box(
                        zoneId(ctx),
                        BlockPosArgument.getBlockPos(ctx, "pos1"),
                        BlockPosArgument.getBlockPos(ctx, "pos2"),
                        zoneDirection(ctx),
                        zoneUsesCenterGravity(ctx),
                        zoneStrength(ctx),
                        1.8
                    ), edit, false))
                )
            );

        return Commands.literal(name).then(idArgument);
    }

    @FunctionalInterface
    private interface ZoneFactory {
        Zone create(CommandContext<CommandSourceStack> context);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> zoneProperties(
        ZoneFactory factory,
        boolean edit
    ) {
        return zoneProperties(factory, edit, true);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> zoneProperties(
        ZoneFactory factory,
        boolean edit,
        boolean includeTransition
    ) {
        var strength = Commands.argument("strength", DoubleArgumentType.doubleArg(0, 20));
        if (includeTransition) {
            strength.then(Commands.argument("transition", DoubleArgumentType.doubleArg(0, 128))
                .executes(ctx -> saveZone(ctx, edit, factory)));
        } else {
            strength.executes(ctx -> saveZone(ctx, edit, factory));
        }
        return Commands.argument("direction", StringArgumentType.word())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                List.of("down", "up", "north", "south", "west", "east", "center"), builder
            ))
            .then(strength);
    }

    private static boolean zoneUsesCenterGravity(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "direction").equalsIgnoreCase("center");
    }

    private static Direction zoneDirection(CommandContext<CommandSourceStack> context) {
        if (zoneUsesCenterGravity(context)) {
            return Direction.DOWN;
        }
        String direction = StringArgumentType.getString(context, "direction").toLowerCase();
        return switch (direction) {
            case "up", "u" -> Direction.UP;
            case "down", "d" -> Direction.DOWN;
            case "north", "n" -> Direction.NORTH;
            case "south", "s" -> Direction.SOUTH;
            case "east", "e" -> Direction.EAST;
            case "west", "w" -> Direction.WEST;
            default -> Direction.DOWN;
        };
    }

    private static boolean isZoneDirection(String direction) {
        return switch (direction.toLowerCase()) {
            case "center", "up", "u", "down", "d", "north", "n", "south", "s", "east", "e", "west", "w" -> true;
            default -> false;
        };
    }

    private static int saveZone(CommandContext<CommandSourceStack> context, boolean edit, ZoneFactory factory) {
        String id = zoneId(context);
        if (!isZoneDirection(StringArgumentType.getString(context, "direction"))) {
            context.getSource().sendFailure(Component.literal("Direction must be up, down, north, south, east, west, or center."));
            return 0;
        }
        ZoneManager manager = ZoneManager.getInstance();
        Zone existing = manager.getZone(id, context.getSource().getLevel());
        if (edit && existing == null) {
            context.getSource().sendFailure(Component.literal("No zone with ID " + id + " found."));
            return 0;
        }
        if (!edit && existing != null) {
            context.getSource().sendFailure(Component.literal("A zone with ID " + id + " already exists."));
            return 0;
        }

        Zone zone = factory.create(context);
        manager.addZone(zone, context.getSource().getLevel());
        context.getSource().sendSuccess(
            () -> Component.literal("Zone " + (edit ? "updated: " : "created: ") + id),
            true
        );
        return 1;
    }

    private static String zoneId(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "id");
    }

    private static int setZoneEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "id");
        if (!ZoneManager.getInstance().setZoneEnabled(id, enabled, ctx.getSource().getLevel())) {
            ctx.getSource().sendFailure(Component.literal("No zone with ID " + id + " found."));
            return 0;
        }
        ctx.getSource().sendSuccess(
            () -> Component.literal("Zone " + (enabled ? "enabled: " : "disabled: ") + id),
            true
        );
        return 1;
    }

    private static double zoneStrength(CommandContext<CommandSourceStack> context) {
        return Math.max(0.0, DoubleArgumentType.getDouble(context, "strength"));
    }

    private static Direction.Axis zoneAxis(CommandContext<CommandSourceStack> context) {
        return switch (StringArgumentType.getString(context, "axis").toLowerCase()) {
            case "x" -> Direction.Axis.X;
            case "y" -> Direction.Axis.Y;
            case "z" -> Direction.Axis.Z;
            default -> throw new IllegalArgumentException("Cylinder axis must be x, y, or z.");
        };
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
                    combinedRelativeDirection = Direction.from2DDataValue(Direction.fromYRot(entity.getYRot()).get2DDataValue());
                    break;
                case "BACKWARD":
                    combinedRelativeDirection = Direction.from2DDataValue(2 + Direction.fromYRot(entity.getYRot()).get2DDataValue());
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

    private static int executeAddModifier(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        CommandSourceStack source = ctx.getSource();
        String sourceId = StringArgumentType.getString(ctx, "source");
        String dirStr = StringArgumentType.getString(ctx, "direction").toUpperCase();
        double strength = DoubleArgumentType.getDouble(ctx, "strength");
        int priorityArg = 0;
        try {
            priorityArg = ctx.getArgument("priority", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }
        final int priority = priorityArg;

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

    private static int executeRemoveModifier(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        CommandSourceStack source = ctx.getSource();
        String idStr = StringArgumentType.getString(ctx, "modifierId");
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

    private static int executeRemoveModifiersBySource(CommandContext<CommandSourceStack> ctx, Collection<? extends Entity> entities) {
        CommandSourceStack source = ctx.getSource();
        String sourceId = StringArgumentType.getString(ctx, "sourceId");

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
                StringBuilder sb = new StringBuilder("Modifiers for ").append(entity.getDisplayName().getString()).append(":\n");
                for (GravityModifier m : modifiers) {
                    sb.append("  [")
                      .append(m.id())
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