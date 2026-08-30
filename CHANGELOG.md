# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-08-31
### Initial Release as Pondus API Mod

This is the initial release of Pondus as a pure gravity modification API, converted from the GravityChanger template.

### Added
- Core gravity manipulation API (PondusAPI, IEntityGravityData, GravityUpdateEvent)
- Complete command set (/gravity subcommands)
- Event system for gravity changes
- Data attachment system for entity gravity data
- Configuration system via AutoConfig
- Mixin-based gravity mechanics integration
- Mob effects for gravity manipulation (strength, direction, invert)
- Status effect integration
- Network synchronization for multiplayer
- Client and server platform helpers
- No items or blocks - pure API only
- License: CC-BY-NC-SA-4.0
- Author: Dinner
- Description: "Adds high performance Custom Gravity to minecraft. Fork of "Gravity Changer Unofficial Port""
- Logo: pondus.png

### Changed
- Converted from GravityChanger template to Pondus API-only mod
- Removed all item and block definitions
- Renamed all references from "gravitychanger"/"GravityChanger" to "pondus"/"Pondus"
- Updated MOD_ID from "dinner.dev.pondus" to "pondus"
- Fixed service loading issue for IPlatformHelper
- Updated version to 1.0.0
- Updated author to Dinner
- Updated description as requested
- Fixed license consistency between gradle.properties and neoforge.mods.toml

### Fixed
- Service loading error: "Failed to load service for dinner.dev.pondus.platform.services.IPlatformHelper"
- MOD_ID mismatch issues
- Dependency configuration syntax errors
- Author and description attribution

### Removed
- All item classes (0 Item.java files)
- All block classes (0 Block.java files)
- GravityPlating block and item
- All item/block specific content
- Unused or redundant code from template

### Known Issues
- None reported at initial release

---
*This CHANGELOG.md follows the Keep a Changelog format.*