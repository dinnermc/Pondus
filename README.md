# Pondus

A lightweight NeoForge API and utility mod that provides full 3D gravity manipulation functionality for entities and dimensions.
---

### **Lineage & Credits**
This mod is a streamlined fork of **Gravity Control** (by *tfarecnim*) for NeoForge. All items and blocks have been removed to serve as a pure, bloat-free API and technical tool.

* **Mod Lineage:** *Pondus* (dinner) ← *Gravity Control* (tfarecnim) ← *Gravity API* (Fusion-Flux) ← *Gravity API* (Gaider10).

---

### **Features**
* Full directional gravity manipulation (6 DOF + view-relative directions).
* Per-entity and per-dimension gravity strength scaling.
* Pure technical focus: contains no survival blocks or items.

---

### **Commands**

All features can be tested or configured in-game using the `/gravity` command tree. 

*(Note: Omitting the `[entities]` argument will target the command sender).*

* **`/gravity set_base_direction <direction> [entities]`**
  Sets the base gravity direction (`down`, `up`, `north`, `south`, `west`, `east`). Base direction can be overridden by status effects or gravity anchors.
  * *Example:* `/gravity set_base_direction up @e[type=!minecraft:player]`

* **`/gravity set_base_strength <strength> [entities]`**
  Sets the base gravity multiplier. Status effects multiply with this base value instead of replacing it.
  * *Example:* `/gravity set_base_strength 0.5 @e`

* **`/gravity set_relative_base_direction <relativeDirection> [entities]`**
  Sets gravity direction relative to where the entity is currently looking (`forward`, `backward`, `left`, `right`, `up`, `down`).

* **`/gravity randomize_base_direction [entities]`**
  Assigns a random gravity direction to the targeted entities.

* **`/gravity reset [entities]`**
  Resets base gravity direction and strength back to vanilla defaults.

* **`/gravity view`**
  Displays the current base gravity direction and strength of the command sender.

* **`/gravity set_dimension_gravity_strength <strength>`**
  Sets the global gravity strength multiplier for the current dimension.

* **`/gravity view_dimension_info`**
  Displays the global gravity settings for the current dimension.
