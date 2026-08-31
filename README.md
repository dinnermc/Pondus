# Pondus

A lightweight NeoForge API and utility mod that provides full 3D gravity manipulation functionality for entities and dimensions.

---

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Lineage & Credits](#lineage--credits)
- [Commands](#commands)
- [API Usage for Mod Developers](#api-usage-for-mod-developers)
- [Configuration](#configuration)
- [Installation](#installation)
- [License](#license)
- [Wiki & Documentation](#wiki--documentation)

---

## Overview

Pondus is a streamlined fork of Gravity Control (by tfarecnim) for NeoForge. All items and blocks have been removed to serve as a pure, bloat-free API and technical tool that other mods can depend on to add gravity manipulation functionality.

**Mod Lineage:** `Pondus` (dinner) ← `Gravity Control` (tfarecnim) ← `Gravity API` (Fusion-Flux) ← `Gravity API` (Gaider10).

---

## Features

- **Full 3D Gravity Manipulation**: Control gravity in all 6 directions (up/down, north/south, east/west) plus view-relative directions
- **Per-Entity Control**: Set gravity direction and strength for individual entities or groups
- **Per-Dimension Control**: Adjust gravity strength for entire dimensions
- **Status Effect Integration**: Works with gravity-related status effects (status effect) mods seamlessly
- **Pure Technical Focus**: Contains no survival blocks or items - API only
- **High Performance**: Optimized for minimal server/client impact
- **Command-Line Access**: Full functionality accessible via `/gravity` commands
- **Event System**: GravityUpdateEvent for reacting to gravity changes
- **Data Attachments**: Efficient entity data storage using NeoForge's attachment system

---

## Lineage & Credits

This mod is a streamlined fork of **Gravity Control** (by *tfarecnim*) for NeoForge. All items and blocks have been removed to serve as a pure, bloat-free API and technical tool.

* **Mod Lineage:** `Pondus` (dinner) ← `Gravity Control` (tfarecnim) ← `Gravity API` (Fusion-Flux) ← `Gravity API` (Gaider10).
* **Credits**: Thanks to tfarecnim for making Gravity Control
* **Current Author**: Dinner

---

## Commands

All features can be tested or configured in-game using the `/gravity` command tree. 

*(Note: Omitting the `[entities]` argument will target the command sender.)*

### **`/gravity set_base_direction <direction> [entities]`**
Sets the base gravity direction (`down`, `up`, `north`, `south`, `west`, `east`). Base direction can be overridden by status effects or gravity anchors.
* *Example:* `/gravity set_base_direction up @e[type=!minecraft:player]`

### **`/gravity set_base_strength <strength> [entities]`**
Sets the base gravity multiplier. Status effects multiply with this base value instead of replacing it.
* *Example:* `/gravity set_base_strength 0.5 @e`

### **`/gravity set_relative_base_direction <relativeDirection> [entities]`**
Sets gravity direction relative to where the entity is currently looking (`forward`, `backward`, `left`, `right`, `up`, `down`).

### **`/gravity randomize_base_direction [entities]`**
Assigns a random gravity direction to the targeted entities.

### **`/gravity reset [entities]`**
Resets base gravity direction and strength back to vanilla defaults.

### **`/gravity view`**
Displays the current base gravity direction and strength of the command sender.

### **`/gravity set_dimension_gravity_strength <strength>`**
Sets the global gravity strength multiplier for the current dimension.

### **`/gravity view_dimension_info`**
Displays the global gravity settings for the current dimension.

---

## API Usage for Mod Developers

Pondus is designed as a library that other mods can depend on. Add it to your mod's dependencies and use the API to manipulate gravity.

### **Dependency Setup**

In your `build.gradle`:
```gradle
dependencies {
    // Replace with actual version or use Maven
    modImplementation "dinner.dev:pondus:1.0.0"
}
```

In your `mods.toml`:
```toml
[[dependencies.pondus]]
    modId="pondus"
    versionRange="[1.0.0,)"
    ordering="NONE"
    side="BOTH"
```

### **Core API Classes**

#### `PondusAPI` - Main Access Point
```java
import dinner.dev.pondus.api.PondusAPI;
import dinner.dev.pondus.api.IEntityGravityData;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

// Get gravity data for an entity
IEntityGravityData gravity = PondusAPI.getGravityData(entity);

// Set base gravity direction
PondusAPI.setBaseGravityDirection(entity, Direction.UP);

// Set base gravity strength (multiplier)
PondusAPI.setBaseGravityStrength(entity, 1.5);

// Reset to defaults
PondusAPI.resetGravity(entity);

// Get current values
Direction currentDir = gravity.getCurrGravityDirection();
double currentStrength = gravity.getCurrGravityStrength();
Direction baseDir = gravity.getBaseGravityDirection();
double baseStrength = gravity.getBaseGravityStrength();
```

#### `IEntityGravityData` - Entity Gravity Data Interface
```java
// Apply directional effect (overrides base for one tick)
gravity.applyGravityDirectionEffect(
    Direction.NORTH, 
    null,  // Optional entity causing the effect
    2.0    // Amplifier
);

// Apply strength effect (multiplies with base)
gravity.applyGravityStrengthEffect(
    null,  // Optional entity causing the effect
    0.5    // Multiplier (0.5 = half strength)
);

// Check if entity has custom gravity data
if (gravity.hasCustomDirection()) {
    // Entity has custom directional gravity
}

// Tick the data (call entity tick)
gravity.tick();
```

#### `GravityUpdateEvent` - Listen for Gravity Changes
```java
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.eventbus.api.EventBusSubscriber;
import dinner.dev.pondus.api.GravityUpdateEvent;

@EventBusSubscriber
public class GravityListener {
    @SubscribeEvent
    public static void onGravityChange(GravityUpdateEvent event) {
        Entity entity = event.getEntity();
        IEntityGravityData gravityData = event.getData();
        
        // React to gravity changes
        if (gravityData.getBaseGravityDirection() == Direction.UP) {
            // Entity has upside-down gravity
        }
    }
}
```

---

## Configuration

Pondus uses AutoConfig for mod configuration. Settings can be adjusted in-game via the mod menu or by editing the config file.

### **Configuration File Location**
`config/pondus.json`

### **Available Settings**

#### Client Settings
- **Keep World Look**: Maintain world-relative look direction when changing gravity
- **Camera Rotation Time**: Time (in milliseconds) for camera to rotate between gravity directions
- **Adjust Position After Changing Gravity**: Automatically adjust entity position when gravity changes

#### Server Settings
- **World Relative Velocity Transfer**: Preserve world-relative velocity when gravity changes
- **Reset Gravity On Dimension Change**: Reset gravity to defaults when changing dimensions
- **Reset Gravity On Respawn**: Reset gravity to defaults when entity respawns
- **Void Damage Above World for Upwards Gravity**: Take void damage when above world height with upward gravity
- **Void Damage On Falling Far for Horizontal Gravity**: Take void damage after falling far with horizontal gravity
- **Auto Jump On Gravity Plate Inner Corner**: Automatically jump when hitting inner corner of gravity plates
- **World Default Gravity Strength**: Default gravity strength for new dimensions

---

## Installation

### **For Players**
1. Download the latest Pondus release from [GitHub Releases](https://github.com/dinnermc/Pondus/releases)
2. Place the `.jar` file in your `mods` folder
3. Ensure you have NeoForge 21.1.249+ for Minecraft 1.21.1
4. Launch the game - Pondus will load automatically
5. Configure via the mod menu or edit `config/pondus.json`

### **For Mod Developers**
1. Add Pondus as a dependency in your mod's build.gradle
2. Add the dependency to your mods.toml
3. Use the PondusAPI in your mod code as shown above
4. Your mod will now be able to manipulate gravity for entities and dimensions

---

## License

This mod is licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International Public License (CC-BY-NC-SA-4.0)**.

You are free to:
- **Share** — copy and redistribute the material in any medium or format
- **Adapt** — remix, transform, and build upon the material

Under the following terms:
- **Attribution** — You must give appropriate credit, provide a link to the license, and indicate if changes were made
- **NonCommercial** — You may not use the material for commercial purposes
- **ShareAlike** — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original

See the [LICENSE](LICENSE) file for full terms.

*Note: This license applies to the mod itself. The gravity manipulation API concepts and functionality are free for use in other mods, but the specific implementation and code are covered by this license.*

---

## Wiki & Documentation

This README serves as the primary documentation. For additional information:

### **In-Game Documentation**
- Use `/gravity view` to check current gravity settings
- Refer to the mod menu (Esc -> Mods -> Pondus -> Config) for configuration options
- Tooltips are available for many commands and settings

### **Source Code**
- The mod is open source - explore the source for implementation details
- API classes are well-documented with Javadoc comments
- Example usage can be found in the mod's own command and event handling code

### **Community & Support**
- GitHub Issues: Report bugs, request features, or ask questions
- GitHub Discussions: Share ideas, showcase creations using the API, or get help
- Mod Discord/Minecraft Forums: Community support for mod developers

### **Official Wiki**
The GitHub wiki feature is available for extended documentation:
- Visit the "Wiki" tab on the repository page
- Or create wiki clones locally for offline access

---

## Built With

- [NeoForge](https://neoforged.net/) - Modern Minecraft modding loader
- [AutoConfig](https://github.com/shedaniel/AutoConfig) - Configuration system
- [JetBrains IntelliJ IDEA](https://www.jetbrains.com/idea/) - Development IDE
- [Git](https://git-scm.com/) - Version control
- [Gradle](https://gradle.org/) - Build automation

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

*Note: This is the initial release of Pondus as a pure API mod. Previous versions as Gravity Control/Gravity API are not directly comparable due to the removal of all items and blocks.*

---

*Created by Dinner • Licensed under CC-BY-NC-SA-4.0*