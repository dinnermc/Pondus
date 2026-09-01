# Changelog

All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-09-01
### Documentation Improvements

This update focuses on improving documentation and making it easier for mod developers to use Pondus as a dependency.

### Added
- Enhanced README.md with comprehensive "Using Pondus as a Dependency" section
- Clear instructions for Maven/Gradle setup including repository configuration
- Publishing instructions for maintainers (GitHub Packages, local Maven, etc.)
- Quick start guide for mod developers
- Better visibility of dependency coordinates in documentation

### Changed
- Restructured README.md to put dependency usage more prominently
- Improved API usage documentation with clearer examples
- Updated installation instructions to reference dependency usage
- Made Maven coordinates more visible throughout documentation

### Fixed
- None

### Dependency Information
- Group ID: `dinner.dev`
- Artifact ID: `pondus`
- Version: `1.0.1`
- Repository: Available via GitHub Packages at `https://maven.pkg.github.com/dinnermc/Pondus`
- Can also be installed to local Maven via `./gradlew publishToMavenLocal`

---
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