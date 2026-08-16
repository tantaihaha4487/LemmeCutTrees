# LemmeCutTrees

Server-authoritative Fabric tree felling for Minecraft Java Edition.

Hold a configured axe, sneak, and break a qualifying tree log. LemmeCutTrees
handles the remaining logs and leaves while preserving vanilla block-breaking
behavior, drops, tool durability, exhaustion, sounds, and compatible Fabric
callbacks.

## Features

- Tree felling: Detects configured trees and breaks their connected logs in a
  controlled queue.
- Leaf clearing: Removes connected leaves when enabled, without consuming extra
  axe durability.
- Vanilla behavior: Uses the server's normal block-breaking path for drops,
  enchantments, tool behavior, exhaustion, sounds, and callbacks.
- Durability protection: Reserves enough axe durability for the complete tree
  before felling starts. Queued trees are also accounted for.
- Safe interruption: Stops queued work when the player dies, disconnects,
  changes dimension, or stops holding a configured axe; blocks that are no
  longer interactable are skipped safely.
- Configurable detection: Add tree species, log and leaf blocks, diagonal leaf
  connections, size limits, height limits, and detection ranges.
- Server feedback: Shows an active actionbar while the feature is active and
  plays activation/deactivation sounds for the player.
- Dedicated-server friendly: Gameplay is authoritative on the logical server;
  connecting clients do not need the mod installed.

## Installation

1. Install Fabric Loader for Minecraft `26.1.2` on the server.
2. Install the matching Fabric API in the server's `mods` directory.
3. Put the `lemmecuttrees` JAR in the same `mods` directory.
4. Start the server once. The default configuration is created at
   `config/lemmecuttrees.yml`.
5. Adjust the configuration if needed, then restart the server or run
   `/lemmecuttrees reload`.

## Usage

The default activation flow is:

1. Hold one of the configured axes in your main hand.
2. Hold Shift while targeting a tree log.
3. Break the log normally.
4. If the block is a qualifying tree, the first block breaks normally and the
   remaining logs are processed progressively.

The default configuration recognizes:

<img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/oak_log.png" width="16" height="16" alt="Oak Log"> Oak · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/oak_log.png" width="16" height="16" alt="Oak Log"> Azalea · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/birch_log.png" width="16" height="16" alt="Birch Log"> Birch · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/spruce_log.png" width="16" height="16" alt="Spruce Log"> Spruce · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/jungle_log.png" width="16" height="16" alt="Jungle Log"> Jungle · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/dark_oak_log.png" width="16" height="16" alt="Dark Oak Log"> Dark Oak · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/acacia_log.png" width="16" height="16" alt="Acacia Log"> Acacia · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/mangrove_log.png" width="16" height="16" alt="Mangrove Log"> Mangrove · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/cherry_log.png" width="16" height="16" alt="Cherry Log"> Cherry · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/pale_oak_log.png" width="16" height="16" alt="Pale Oak Log"> Pale Oak · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/crimson_stem.png" width="16" height="16" alt="Crimson Stem"> Crimson · <img src="https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/26.1.2/assets/minecraft/textures/block/warped_stem.png" width="16" height="16" alt="Warped Stem"> Warped

A tree must satisfy the configured log, leaf, size, height, shape, and vertical
ratio checks. Blocks that have already changed or that the player can no longer
interact with are skipped safely.

When a tree is accepted, the player receives a green `LemmeCutTrees Activated`
actionbar and activation sound. The actionbar remains visible while the player
continues holding an allowed axe. Switching to another item sends a red
`LemmeCutTrees Deactivated` actionbar and deactivation sound.

If the axe cannot cover the required log durability, the tree is not accepted
and the player receives a durability warning. Creative players are not limited
by the durability reservation.

## Enchantments and cutting speed

| Enchantment/effect | Cutting speed |
| --- | --- |
| Efficiency I-V | Faster; higher level cuts faster |
| Haste | Faster |
| Mining Fatigue | Slower |
| Unbreaking, Mending, Fortune, Silk Touch | No effect |

<details>
<summary>Configuration</summary>

### Configuration

The generated file is `config/lemmecuttrees.yml`. It is ordinary YAML and can
be edited while the server is stopped.

### General settings

| Key | Default | Description |
| --- | ---: | --- |
| `require-shift` | `true` | Require the player to sneak before tree detection runs. |
| `clear-leaves` | `true` | Break detected leaves after the logs are processed. |
| `cutting-speed.multiplier` | `1.0` | Multiplies the vanilla per-log break time. Higher values are slower. |
| `cutting-speed.minimum-ticks-per-log` | `1` | Lower bound for the delay between queued log breaks. |
| `cutting-speed.maximum-ticks-per-log` | `200` | Upper bound for the delay between queued log breaks. |

### Detection settings

| Key | Default | Description |
| --- | ---: | --- |
| `detection.scan-distance` | `256` | Maximum distance used while discovering connected logs. |
| `detection.required-logs` | `4` | Minimum logs required for a detected tree. |
| `detection.required-leaves` | `10` | Minimum connected leaves required for a detected tree. |
| `detection.maximum-logs` | `250` | Maximum logs allowed in one tree. |
| `detection.maximum-cut-height` | `5` | Maximum vertical distance from the broken origin to the lowest detected log. |
| `detection.leaf-detect-range` | `6` | Range used to decide whether a tree has enough leaves. |
| `detection.leaf-break-range` | `6` | Range of leaves that may be cleared. |
| `detection.maximum-horizontal-log-run` | `6` | Maximum straight horizontal log run. |
| `detection.minimum-vertical-log-ratio` | `0.5` | Minimum vertical-to-horizontal log ratio. |
| `detection.include-player-placed-leaves` | `false` | Include persistent/player-placed leaves in detection and clearing. |

### Axes and tree mappings

`allowed-axes` controls which main-hand items can activate the feature. The
default list contains wooden, stone, copper, iron, golden, diamond, and
netherite axes.

Each entry in `trees` defines a species with `logs` and `leaves` block ID lists.
The optional per-tree settings are:

- `diagonal-leaves`: Allow diagonal leaf connections.
- `required-logs`: Override the global minimum log count.
- `leaf-detect-range`: Override the global leaf detection range.
- `leaf-break-range`: Override the global leaf clearing range.
- `maximum-horizontal-log-run`: Override the global horizontal run limit.

Use namespaced IDs such as `minecraft:oak_log`. Invalid IDs, duplicate tree
names, duplicate mappings, non-axe allowed items, and invalid numeric values
are rejected when the configuration loads.

### Reloading

Operators with the server's gamemaster command permission can run:

```text
/lemmecuttrees reload
```

Valid changes replace the active configuration. If the new YAML is invalid,
the previous configuration remains active and the command reports the failure.

</details>

## License and attribution

LemmeCutTrees is licensed under the [GNU General Public License v3.0](LICENSE).

The tree-detection defaults are derived from the deliberately limited subset
of [TreeFeller 1.30.2](https://github.com/ThizThizzyDizzy/tree-feller) by
ThizThizzyDizzy. See [NOTICE](NOTICE) for the attribution details.
