# Changelog

## 1.1.0 - 2026-11-23
### Gravity Zones, Planetary Physics & Addon API
- **Persistent per-dimension Gravity Zones**: box, sphere, and cylinder shapes with smooth spatial transitions, `center` radial gravity, `/gravity zone enable|disable`, and client-side wireframe/arrow visualizer (`G`).
- **Outer Wilds-ready planetary physics**:
  - Frame-rate-independent exponential camera damping for smooth high-speed movement around spheres and seamless cardinal face handoff.
  - 90° cardinal switch hysteresis + grounded lateral damping so walking/building on spherical planetoids feels stable.
  - Smooth inner core deadzone/fade and Newtonian `1/r²` helper (`GravityFieldShapes.inverseSquareStrength`).
  - Absolute-strength local fields (`GravityField.planetary`, `GravityField.absolute`) so planets, zones, and ship interiors work seamlessly inside zero-G space dimensions (`dimensionGravity = 0.0`).
  - Force-weighted vector addition for equal-priority gravity fields (forming natural Lagrange points between planets and moons).
- **Clean Addon Developer API (zero gameplay bloat)**:
  - `ItemGravityRule` + `PondusAPI.registerItemGravity(...)` for custom held items, armor slots, inventory charms, and dynamic item providers (`ITEM_PRIORITY = 75`).
  - Timed modifiers (`PondusAPI.applyTimedGravity`, `PondusAPI.applyTimedGravityStrength`) with automatic server countdown, NBT persistence, and sync.
  - Dimension-scoped `GravitySource` registration (`PondusAPI.registerLevelGravitySource`) with automatic cleanup on server stop.
  - Programmatic zone management (`addZone`, `removeZone`, `getZone`, `getZones`, `setZoneEnabled`, `getZoneInfluence`) and coordinate transform helpers (`worldToLocal`, `localToWorld`) directly on `PondusAPI`.
- **Major Bug Fixes**:
  - Fixed server-to-client sync bug where local players missed zone gravity updates (`needsSync` cleared by `sendSyncPacketToOtherPlayers`).
  - Fixed double gravity strength multiplication on `LivingEntity` (squared gravity strength) and added continuous 3D gravity vector support for living entities.
  - Fixed camera twist jumps (90°/180°), `NaN` quaternions/poles, and view misalignment when crossing faces.
  - Added entity gravity NBT persistence (`saveWithoutId`/`load`), `resetGravityOnRespawn` handling, `GravityUpdateEvent` firing, `S2CLevelGravityPacket` registration & sync, and fixed mob effect packet spam and negative `strength_reverse` crash.
  - Fixed inclusive block bounds (`max + 1`) on box zones, per-dimension zone storage, zero-G zone strength in commands, and `BACKWARD` relative gravity command.
- **Fabric Port Parity**:
  - Fixed broken Fabric mixins (`EntityMixinFabric`, `CameraMixinFabric`, `LivingEntityBreatheMixinFabric`), Fabric 1.21.1 `PayloadTypeRegistry` networking, entity tracking, dimension sync, and ported all v1.1.0 zone, planetary, visualizer, and API features to `fabric-port`.

## 1.0.1 - 2026-09-01
- doc of maven and complete readme, examples of dependency

## 1.0.0 - 2026-08-31
- initial release: clean fork of gravity control, only api
- commands /gravity, events, effects, config
- licence cc-by-nc-sa, logo

## 0.x (template gravitychanger)
- original base of gravity changer / gravity api
