package dinner.dev.pondus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dinner.dev.pondus.api.GravityFieldShapes;
import dinner.dev.pondus.network.S2CZoneVisualizationPacket.ZoneVisualData;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ClientZoneVisualizer {
    private static final double VIEW_DISTANCE = 64.0;
    private static final int MAX_ARROWS_PER_FRAME = 2048;
    private static final int CURVE_SEGMENTS = 36;
    private static boolean enabled;
    private static ResourceKey<Level> zonesDimension;
    private static List<ZoneVisualData> zones = List.of();

    private ClientZoneVisualizer() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void setZones(ResourceKey<Level> dimension, List<ZoneVisualData> newZones) {
        zonesDimension = dimension;
        zones = List.copyOf(newZones);
    }

    public static boolean hasZonesFor(ResourceKey<Level> dimension) {
        return dimension != null && dimension.equals(zonesDimension);
    }

    public static void reset() {
        enabled = false;
        zonesDimension = null;
        zones = List.of();
    }

    public static void render(WorldRenderContext context) {
        if (!enabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || zones.isEmpty() || !minecraft.level.dimension().equals(zonesDimension)) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 camera = context.camera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        int remainingArrows = MAX_ARROWS_PER_FRAME;
        AABB viewBounds = new AABB(
            camera.x - VIEW_DISTANCE, camera.y - VIEW_DISTANCE, camera.z - VIEW_DISTANCE,
            camera.x + VIEW_DISTANCE, camera.y + VIEW_DISTANCE, camera.z + VIEW_DISTANCE
        );

        for (ZoneVisualData zone : zones) {
            if (!zone.enabled() || !zone.getBounds().inflate(zone.transitionDistance()).intersects(viewBounds)) {
                continue;
            }
            float[] color = zone.centerGravity()
                ? new float[] {0.25F, 0.9F, 1.0F}
                : directionColor(zone.getDirection());
            renderOutline(poseStack, lines, zone, color);
            if (remainingArrows > 0) {
                remainingArrows -= renderArrows(poseStack, lines, zone, camera, color, remainingArrows);
            }
        }

        poseStack.popPose();
    }

    private static void renderOutline(
        PoseStack poseStack, VertexConsumer lines, ZoneVisualData zone, float[] color
    ) {
        AABB bounds = zone.getBounds();
        if (zone.getShape() == dinner.dev.pondus.zone.Zone.Shape.BOX) {
            LevelRenderer.renderLineBox(poseStack, lines, bounds, color[0], color[1], color[2], 0.9F);
            if (zone.transitionDistance() > 0) {
                LevelRenderer.renderLineBox(
                    poseStack, lines, bounds.inflate(zone.transitionDistance()),
                    color[0], color[1], color[2], 0.35F
                );
            }
            return;
        }

        if (zone.transitionDistance() > 0) {
            renderCurvedOutline(poseStack, lines, zone, zone.transitionDistance(), color, 0.35F);
        }
        renderCurvedOutline(poseStack, lines, zone, 0, color, 0.9F);
    }

    private static void renderCurvedOutline(
        PoseStack poseStack,
        VertexConsumer lines,
        ZoneVisualData zone,
        double extra,
        float[] color,
        float alpha
    ) {
        Vec3 center = zone.getCenter();
        double radius = zone.radius() + extra;
        if (zone.getShape() == dinner.dev.pondus.zone.Zone.Shape.SPHERE) {
            for (int plane = 0; plane < 3; plane++) {
                Vec3 previous = spherePoint(center, radius, plane, 0);
                for (int i = 1; i <= CURVE_SEGMENTS; i++) {
                    Vec3 next = spherePoint(center, radius, plane, Math.PI * 2 * i / CURVE_SEGMENTS);
                    line(poseStack, lines, previous, next, color, alpha);
                    previous = next;
                }
            }
            return;
        }

        Direction.Axis axis = zone.getAxis();
        double halfHeight = zone.halfHeight() + extra;
        Vec3 previousStart = cylinderPoint(center, radius, axis, -halfHeight, 0);
        Vec3 previousEnd = cylinderPoint(center, radius, axis, halfHeight, 0);
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double angle = Math.PI * 2 * i / CURVE_SEGMENTS;
            Vec3 start = cylinderPoint(center, radius, axis, -halfHeight, angle);
            Vec3 end = cylinderPoint(center, radius, axis, halfHeight, angle);
            line(poseStack, lines, previousStart, start, color, alpha);
            line(poseStack, lines, previousEnd, end, color, alpha);
            if (i % 3 == 1) {
                line(poseStack, lines, start, end, color, alpha);
            }
            previousStart = start;
            previousEnd = end;
        }
    }

    private static Vec3 spherePoint(Vec3 center, double radius, int plane, double angle) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        return switch (plane) {
            case 0 -> center.add(cos, sin, 0);
            case 1 -> center.add(cos, 0, sin);
            default -> center.add(0, cos, sin);
        };
    }

    private static Vec3 cylinderPoint(
        Vec3 center, double radius, Direction.Axis axis, double axial, double angle
    ) {
        double a = Math.cos(angle) * radius;
        double b = Math.sin(angle) * radius;
        return switch (axis) {
            case X -> center.add(axial, a, b);
            case Y -> center.add(a, axial, b);
            case Z -> center.add(a, b, axial);
        };
    }

    private static int renderArrows(
        PoseStack poseStack,
        VertexConsumer lines,
        ZoneVisualData zone,
        Vec3 camera,
        float[] color,
        int arrowBudget
    ) {
        AABB drawBounds = zone.getBounds().inflate(zone.transitionDistance());
        double minX = Math.max(drawBounds.minX, camera.x - VIEW_DISTANCE);
        double minY = Math.max(drawBounds.minY, camera.y - VIEW_DISTANCE);
        double minZ = Math.max(drawBounds.minZ, camera.z - VIEW_DISTANCE);
        double maxX = Math.min(drawBounds.maxX, camera.x + VIEW_DISTANCE);
        double maxY = Math.min(drawBounds.maxY, camera.y + VIEW_DISTANCE);
        double maxZ = Math.min(drawBounds.maxZ, camera.z + VIEW_DISTANCE);
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return 0;
        }

        int x0 = Mth.floor(minX);
        int y0 = Mth.floor(minY);
        int z0 = Mth.floor(minZ);
        int x1 = Mth.ceil(maxX);
        int y1 = Mth.ceil(maxY);
        int z1 = Mth.ceil(maxZ);
        long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
        int stride = Math.max(1, Mth.ceil(Math.cbrt(volume / (double) arrowBudget)));
        int renderedArrows = 0;

        for (int x = x0; x <= x1 && renderedArrows < arrowBudget; x += stride) {
            for (int y = y0; y <= y1 && renderedArrows < arrowBudget; y += stride) {
                for (int z = z0; z <= z1 && renderedArrows < arrowBudget; z += stride) {
                    Vec3 point = new Vec3(x + 0.5, y + 0.5, z + 0.5);
                    double influence = influence(zone, point);
                    if (influence <= 0.02) {
                        continue;
                    }

                    Vec3 direction = getArrowVector(zone, point);
                    double length = Math.min(0.28, stride * 0.32);
                    Vec3 tip = point.add(direction.scale(length));
                    float alpha = (float) (0.25 + 0.7 * influence);
                    line(poseStack, lines, point, tip, color, alpha);
                    Vec3 reference = Math.abs(direction.y) < 0.9
                        ? new Vec3(0, 1, 0)
                        : new Vec3(1, 0, 0);
                    Vec3 sideA = direction.cross(reference).normalize().scale(0.11);
                    Vec3 sideB = direction.cross(sideA).normalize().scale(0.11);
                    Vec3 base = tip.subtract(direction.scale(0.12));
                    line(poseStack, lines, tip, base.add(sideA), color, alpha);
                    line(poseStack, lines, tip, base.subtract(sideA), color, alpha);
                    line(poseStack, lines, tip, base.add(sideB), color, alpha);
                    line(poseStack, lines, tip, base.subtract(sideB), color, alpha);
                    renderedArrows++;
                }
            }
        }
        return renderedArrows;
    }

    private static Vec3 getArrowVector(ZoneVisualData zone, Vec3 point) {
        if (!zone.centerGravity()) {
            return Vec3.atLowerCornerOf(zone.getDirection().getNormal());
        }
        Vec3 towardCenter = zone.getCenter().subtract(point);
        if (towardCenter.lengthSqr() < 1.0E-8) {
            return Vec3.atLowerCornerOf(zone.getDirection().getNormal());
        }
        return towardCenter.normalize();
    }

    private static double influence(ZoneVisualData zone, Vec3 point) {
        return switch (zone.getShape()) {
            case BOX -> GravityFieldShapes.boxInfluence(
                point, zone.getBounds(), zone.transitionDistance()
            );
            case SPHERE -> GravityFieldShapes.sphereInfluence(
                point, zone.getCenter(), zone.radius(), zone.transitionDistance()
            );
            case CYLINDER -> GravityFieldShapes.cylinderInfluence(
                point, zone.getCenter(), zone.getAxis(), zone.radius(),
                zone.halfHeight(), zone.transitionDistance()
            );
        };
    }

    private static void line(
        PoseStack poseStack, VertexConsumer vertices, Vec3 from, Vec3 to, float[] color, float alpha
    ) {
        PoseStack.Pose pose = poseStack.last();
        vertices.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(pose, 0, 1, 0);
        vertices.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
            .setColor(color[0], color[1], color[2], alpha)
            .setNormal(pose, 0, 1, 0);
    }

    private static float[] directionColor(Direction direction) {
        return switch (direction) {
            case DOWN -> new float[]{0.3F, 0.65F, 1.0F};
            case UP -> new float[]{1.0F, 0.72F, 0.2F};
            case NORTH -> new float[]{0.3F, 1.0F, 0.8F};
            case SOUTH -> new float[]{1.0F, 0.35F, 0.55F};
            case EAST -> new float[]{0.85F, 0.45F, 1.0F};
            case WEST -> new float[]{0.5F, 1.0F, 0.35F};
        };
    }
}
