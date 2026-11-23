# Gravity Zones

Pondus includes persistent, **per-dimension** gravity zones that survive restarts and chunk reloads, support smooth spatial transitions, and can be created via commands or programmatically via `PondusAPI`.

## Commands (OP Level 2)

```text
/gravity zone create <id> box <pos1> <pos2> <direction> <strength> <transition>
/gravity zone create <id> sphere <center> <radius> <direction> <strength> <transition>
/gravity zone create <id> cylinder <center> <axis> <radius> <height> <direction> <strength> <transition>

/gravity zone edit <id> <shape and arguments...>
/gravity zone enable <id>
/gravity zone disable <id>
/gravity zone remove <id>
/gravity zone list
/gravity zone status
```

* **`direction`**: `down`, `up`, `north`, `south`, `east`, `west`, or **`center`** (continuous radial gravity toward the geometric center of the box, sphere, or cylinder).
* **`strength`**: `0` to `20` (`0` creates a true zero-G bubble!).
* **`transition`**: smooth falloff distance in blocks outside the core shape (`0` = hard boundary).
* **Box coordinates**: inclusive block bounds just like `/fill`.
* **Per-dimension storage**: Overworld, Nether, End, and modded dimensions each keep their own independent zones.

### Examples

```text
# Spherical planetoid with radial gravity and 4-block atmosphere transition
/gravity zone create planet sphere 0 100 0 12 center 1 4

# Zero-G chamber
/gravity zone create zerog_room box 10 64 10 25 75 25 down 0 2

# Vertical updraft shaft
/gravity zone create elevator cylinder 0 70 0 y 4 30 up 1.2 2
```

## Client Visualizer

Press **`G`** (rebindable in Controls → Pondus) to toggle the in-world zone visualizer for your current dimension:
* Bright wireframe = core shape (100% influence).
* Dim wireframe = outer transition boundary.
* Arrows = direction of pull (inward cyan arrows for `center` zones).

## Programmatic Zone API

```java
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.zone.Zone;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

// Create a spherical planet zone (supports BlockPos or exact Vec3 center)
Zone planet = Zone.sphere(
    "mymod:home_planet",
    new Vec3(100.5, 120.0, -40.5),
    18.0,
    Direction.DOWN,
    true,   // centerGravity
    1.0,    // strength
    6.0     // transitionDistance
);

PondusAPI.addZone(serverLevel, planet);
PondusAPI.setZoneEnabled(serverLevel, "mymod:home_planet", false);
PondusAPI.removeZone(serverLevel, "mymod:home_planet");
```
