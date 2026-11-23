# Getting Started

## Requirements

* **Minecraft:** `1.21.1`
* **Java:** `21`
* **Loaders:**
  * **NeoForge** `21.1+` (branch: `master`)
  * **Fabric** `0.16+` + Fabric API (branch: `fabric-port`)
* **Dependency:** Cloth Config `15.0.140+`

## Adding Pondus to Your Mod (`build.gradle`)

Add the Modrinth Maven repository:

```gradle
repositories {
    maven {
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup("maven.modrinth")
        }
    }
}
```

### NeoForge

```gradle
dependencies {
    implementation "maven.modrinth:pondus:${project.pondus_version}"
}
```

### Fabric (Loom)

```gradle
dependencies {
    modImplementation "maven.modrinth:pondus:${project.pondus_version}"
}
```

In `gradle.properties`:

```properties
pondus_version=1.1.0
```

## Quick Sanity Check In-Game

Once loaded in a dev world, run:

```text
/gravity zone create planet sphere ~ ~10 ~ 8 center 1 4
```

Press **`G`** to toggle the wireframe & vector arrow visualizer, then walk or fly around the sphere.
