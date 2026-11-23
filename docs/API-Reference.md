# API Reference

Everything external mods need lives under `dinner.dev.pondus.api` (and `dinner.dev.pondus.zone.Zone` for persistent zones).

---

## 1. Reading & Controlling Entity Gravity (`PondusAPI`)

```java
// Read effective gravity
Direction dir   = PondusAPI.getGravityDirection(entity); // nearest cardinal (with 90° hysteresis)
Vec3 vec        = PondusAPI.getGravityVector(entity);    // continuous normalized 3D vector
double strength = PondusAPI.getGravityStrength(entity);  // final effective strength

// Base gravity (saved in entity NBT)
PondusAPI.setBaseGravityDirection(entity, Direction.UP);
PondusAPI.setBaseGravityStrength(entity, 1.5);
PondusAPI.resetGravity(entity);

// Dimension ambient gravity (synced automatically to clients)
PondusAPI.setDimensionGravityStrength(serverLevel, 0.0); // zero-G space dimension
double dimG = PondusAPI.getDimensionGravityStrength(level);

// Check whether an entity supports gravity changes
boolean supported = PondusAPI.canChangeGravity(entity);
```

### World ↔ Entity-Local Coordinate Helpers

```java
Vec3 localVel    = PondusAPI.worldToLocal(entity, worldVec);
Vec3 worldVel    = PondusAPI.localToWorld(entity, localVec);
Direction localD = PondusAPI.worldToLocal(entity, Direction.NORTH);
Direction worldD = PondusAPI.localToWorld(entity, Direction.UP);

Vec3 worldVelocity = PondusAPI.getWorldVelocity(entity);
PondusAPI.setWorldVelocity(entity, new Vec3(0, 0.5, 0));
Vec3 eyeOffset = PondusAPI.getEyeOffset(entity);
```

---

## 2. Priority System (`GravityModifier`)

| Constant | Value | Typical Use |
| --- | --- | --- |
| `DIMENSION_PRIORITY` | `-100` | Ambient dimension defaults |
| `DEFAULT_PRIORITY` | `0` | Strength-only modifiers (do **not** override direction) |
| `EFFECT_PRIORITY` | `25` | Status / potion effects |
| `ZONE_PRIORITY` | `50` | Gravity zones & planetary `GravitySource`s |
| `ITEM_PRIORITY` | `75` | Custom held items, anchors, armor |
| `COMMAND_PRIORITY` | `100` | Admin `/gravity` overrides |
| `VEHICLE_PRIORITY` | `200` | Mounted vehicles |

* **Direction selection:** Highest-priority direction-overriding modifier wins. When multiple modifiers share the **same highest priority** (e.g. overlapping planets), their vectors combine by force-weighted vector addition.
* **Strength stacking:**
  * If any top-priority modifier has `absoluteStrength = true` (zones, `GravityField.planetary`, `GravityField.absolute`), it establishes the local base gravity independent of ambient dimension gravity (so planets and ship floors work in `0.0` space).
  * All multiplicative modifiers (`absoluteStrength = false`, e.g. moon boots, potions) multiply on top of the resulting strength.

---

## 3. Custom Items, Armor & Inventory Charms (`ItemGravityRule`)

Register rules once during mod setup. Pondus evaluates them on the server and syncs automatically:

```java
// Moon Boots: 0.25x gravity while worn (strength-only, never hijacks direction)
PondusAPI.registerItemGravity(
    ModItems.MOON_BOOTS.get(),
    ItemGravityRule.wornIn(EquipmentSlot.FEET)
        .strength(0.25)
        .build()
);

// Gravity Anchor: forces DOWN gravity while held in mainhand or offhand (priority 75 beats zones)
PondusAPI.registerItemGravity(
    ModItems.GRAVITY_ANCHOR.get(),
    ItemGravityRule.heldInEitherHand()
        .direction(Direction.DOWN)
        .priority(GravityModifier.ITEM_PRIORITY)
        .build()
);

// Inventory charm (anywhere in player inventory) filtered by DataComponent or tag
PondusAPI.registerItemGravity(
    ResourceLocation.fromNamespaceAndPath("mymod", "feather_charm"),
    stack -> stack.is(MyTags.FEATHER_CHARMS),
    ItemGravityRule.inInventory()
        .when(stack -> stack.getDamageValue() < stack.getMaxDamage())
        .strength(0.5)
        .build()
);

// Dynamic item logic (inspected every tick when active)
PondusAPI.registerItemGravity(
    ModItems.GRAVITY_STAFF.get(),
    ItemGravityRule.heldInMainHand()
        .dynamic((entity, stack) -> {
            if (!entity.isShiftKeyDown()) return null;
            Vec3 look = entity.getLookAngle();
            return GravityField.ofDirection(
                Direction.getNearest(look.x, look.y, look.z),
                1.0,
                GravityModifier.ITEM_PRIORITY
            );
        })
        .build()
);
```

Supported slot triggers: `heldInMainHand()`, `heldInOffHand()`, `heldInEitherHand()`, `wornIn(EquipmentSlot)`, `wornInAnyArmorSlot()`, `inInventory()`.

---

## 4. Timed Gravity Modifiers (Wands, Spells, Consumables, Projectiles)

No tick trackers or cleanup boilerplate needed — Pondus counts down ticks on the server, persists remaining duration in NBT, and removes + syncs automatically on expiry:

```java
// Flip target entity UP for 5 seconds (100 ticks); re-applying refreshes timer
PondusAPI.applyTimedGravity(
    target,
    "mymod:gravity_wand",
    Direction.UP,
    1.0,
    GravityModifier.ITEM_PRIORITY,
    100
);

// Temporary 0.3x low-gravity without changing direction for 15 seconds (300 ticks)
PondusAPI.applyTimedGravityStrength(player, "mymod:moon_potion", 0.3, 300);
```

---

## 5. Custom `GravitySource`s (Blocks, Machines, Entities, Planets)

For world-backed or BlockEntity-backed sources, always prefer **`registerLevelGravitySource`** so the source only evaluates entities in its own dimension and is automatically cleared when the server stops:

```java
PondusAPI.registerLevelGravitySource(serverLevel, mySource);
// When removed:
PondusAPI.unregisterLevelGravitySource(serverLevel.dimension(), mySource.id());
```

For truly global singleton sources registered once at mod init:

```java
PondusAPI.registerGravitySource(myGlobalSource);
```

Helper factories on `GravityField`:
* `GravityField.ofDirection(Direction, strengthMultiplier, priority)`
* `GravityField.ofVector(Vec3, strengthMultiplier, priority)`
* `GravityField.strengthOnly(strengthMultiplier)`
* `GravityField.absolute(Vec3, gravityStrength, priority, rotationParameters)`
* `GravityField.planetary(Vec3, gravityStrength)`

Geometry helpers on `GravityFieldShapes`:
* `boxInfluence(point, bounds, transitionDistance)`
* `sphereInfluence(point, center, radius, transitionDistance)`
* `cylinderInfluence(point, center, axis, radius, halfHeight, transitionDistance)`
* `centerVector(point, center, coreDeadzone)`
* `coreScale(point, center, coreFadeRadius)`
* `inverseSquareStrength(distance, surfaceRadius, surfaceGravity, maxRange)`

---

## 6. Listening to Gravity Changes (`GravityUpdateEvent`)

Fired whenever an entity's effective gravity direction, vector, or strength changes:

### NeoForge
```java
@SubscribeEvent
public static void onGravityChange(GravityUpdateEvent event) {
    Entity entity = event.getEntity();
    IEntityGravityData data = event.getGravity();
}
```

### Fabric
```java
GravityUpdateEvent.EVENT.register((entity, data) -> {
    // React to gravity changes
});
```
