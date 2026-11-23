# Outer Wilds & Planetary Gravity Guide

Pondus is designed so space mods can build smooth, Outer Wilds-style solar systems on top of a tiny, bloat-free API.

## 1. Make Space Truly Zero-G

Set your space dimension's ambient gravity to `0.0`:

```java
PondusAPI.setDimensionGravityStrength(spaceLevel, 0.0);
```

In open space, entities float weightlessly (`currGravityStrength == 0.0`). Because `GravityField.planetary(...)` and persistent `Zone`s use **`absoluteStrength = true`**, planets, moons, and ship interiors still apply their full local gravity inside a `0.0` space dimension!

## 2. Create Orbiting Newtonian Planets & Moons (`1/r²`)

Use `GravityFieldShapes.inverseSquareStrength(...)` + `GravityFieldShapes.centerVector(...)` + `GravityField.planetary(...)`:

```java
import dinner.dev.pondus.api.GravityField;
import dinner.dev.pondus.api.GravityFieldShapes;
import dinner.dev.pondus.api.GravitySource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

public final class CelestialBodyGravity implements GravitySource {
    private final ResourceLocation id;
    private final Supplier<Vec3> centerSupplier; // updated along orbital path!
    private final double surfaceRadius;
    private final double surfaceGravity;
    private final double maxRange;

    public CelestialBodyGravity(
        ResourceLocation id,
        Supplier<Vec3> centerSupplier,
        double surfaceRadius,
        double surfaceGravity,
        double maxRange
    ) {
        this.id = id;
        this.centerSupplier = centerSupplier;
        this.surfaceRadius = surfaceRadius;
        this.surfaceGravity = surfaceGravity;
        this.maxRange = maxRange;
    }

    @Override
    public ResourceLocation id() {
        return id;
    }

    @Override
    public GravityField getGravityField(Entity entity) {
        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        Vec3 bodyCenter = centerSupplier.get();
        double distance = entityCenter.distanceTo(bodyCenter);

        double strength = GravityFieldShapes.inverseSquareStrength(
            distance, surfaceRadius, surfaceGravity, maxRange
        );
        if (strength <= 1.0E-4) {
            return null;
        }

        Vec3 dir = GravityFieldShapes.centerVector(entityCenter, bodyCenter, 0.35);
        if (dir.lengthSqr() < 1.0E-8) {
            return null;
        }

        return GravityField.planetary(dir, strength);
    }
}
```

Register each body scoped to your space dimension:

```java
PondusAPI.registerLevelGravitySource(spaceLevel, timberHearth);
PondusAPI.registerLevelGravitySource(spaceLevel, attlerockMoon);
```

### What Pondus Handles Automatically
* **Natural Lagrange points:** Because `timberHearth` and `attlerockMoon` share `ZONE_PRIORITY`, Pondus adds their force vectors (`vecA * sA + vecB * sB`), creating a real zero-G balance point between planet and moon.
* **Smooth high-speed orbiting camera:** Pondus uses continuous frame-rate-independent exponential quaternion damping and seamless cardinal face handoff (no 20 Hz stuttering and no 45° face-seam stalls).
* **Stable voxel walking:** 90° cardinal hysteresis prevents edge flickering, and grounded lateral pull is damped (suppressed while sneaking) so walking/building on a voxel planet feels solid while mid-air jumps follow the full 3D radial vector.

## 3. Artificial Gravity Inside a Spaceship

Give your ship interior a higher priority (e.g. `GravityModifier.ZONE_PRIORITY + 10`) with `GravityField.absolute(...)` so standing inside the ship overrides external planetary gravity cleanly:

```java
return GravityField.absolute(
    shipDownVector,
    1.0,
    GravityModifier.ZONE_PRIORITY + 10,
    new RotationParameters(false, false, 300)
);
```

## 4. Jetpacks & Suit Thrusters

Use `PondusAPI.localToWorld(entity, localThrust)` or `PondusAPI.getWorldVelocity(entity)` / `PondusAPI.setWorldVelocity(entity, vel)` so thruster impulses always align cleanly with the player's current gravity orientation!
