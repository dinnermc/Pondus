# Pondus Wiki

Welcome to the **Pondus** documentation — a lightweight, high-performance 3D custom gravity API for **Minecraft 1.21.1** (**NeoForge** & **Fabric**).

Pondus ships **zero gameplay bloat** (no custom blocks or items of its own). It gives addon developers clean primitives to build custom gravity items, armor, spells, machines, zones, zero-G space dimensions, and planetary gravity systems.

## Pages

1. **[Getting Started](Getting-Started.md)** — Installing Pondus as a Gradle dependency (NeoForge & Fabric) and requirements.
2. **[API Reference](API-Reference.md)** — Complete guide to `PondusAPI`, `ItemGravityRule`, timed modifiers, `GravityModifier`, `GravityField`, `GravitySource`, `GravityUpdateEvent`, and coordinate transforms.
3. **[Gravity Zones](Gravity-Zones.md)** — Persistent per-dimension box/sphere/cylinder zones, commands, client wireframe visualizer (`F9`), and programmatic Zone API.
4. **[Planetary & Zero-G Gravity Guide](Planetary-Gravity.md)** — Building zero-G space dimensions, orbiting Newtonian `1/r²` planets and moons, Lagrange points, and artificial ship gravity.

## Core Architecture at a Glance

| Concept | Purpose |
| --- | --- |
| **Base Gravity** | Per-entity fallback direction & strength (persisted in NBT). |
| **Dimension Gravity** | Ambient gravity multiplier for empty space in a dimension (e.g. `1.0` Overworld, `0.0` Space). |
| **Modifier Stack (`GravityModifier`)** | Priority-ordered modifiers (effects, zones, items, commands, vehicles). Equal top-priority fields blend vectorially; multiplicative modifiers scale strength. |
| **Absolute Local Fields** | Planets, zones, and ship interiors (`absoluteStrength = true`) provide local gravity even inside `0.0` zero-G dimensions. |
| **`GravitySource`** | Server-evaluated dynamic field providers (global or dimension-scoped with automatic cleanup). |
| **`ItemGravityRule`** | Zero-boilerplate declarative binding for custom held items, armor, inventory charms, and dynamic items. |
