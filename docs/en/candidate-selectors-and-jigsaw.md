# Candidate Selection, Structures And Jigsaw Blocks

[简体中文](../candidate-selectors-and-jigsaw.md) | **English** · [Guide](guide.md)

## Choose The Correct Target

“Add candidate” opens the selector matching the field type.
Adding a value returns to the candidate list; choosing it from that list fills the original field.
Cancelling a resource selector does not overwrite that field.

| Field | Selector | Stored value |
| --- | --- | --- |
| Item/acquisition/reward | Shared item picker | Item ID or supported encoded stack |
| Image/icon/background | Shared resource picker | Resource reference |
| World structure/biome | Location target picker | Raw registry ID |
| Complete location condition | Location editor | Encoded kind/dimension/target or coordinates |
| Imported structure file | NBT library | Library-relative filename |
| Task reference | Task picker | Task ID |

`minecraft:village_plains` is a world structure ID.
`structure||minecraft:village_plains` is a complete location condition with unrestricted dimension.
`minecraft/village/plains/town_centers/plains_fountain_01.nbt` is an imported library reference.
They serve different APIs.

## Preview A World Structure

Select the world target, open Structure Preview, then open a template's preview icon.
The browser reads templates referenced by the world's actual structure start-pool resources.
Returning preserves the selected world target.

![Real template](../images/structure-template-en-1920.png)

Procedural structures may have no corresponding start pool/template.
Code-registered pools without matching readable JSON may also be unavailable.
These cases show an explanation while the structure can still be selected as a locate target.
Pool browsing is currently a local author-world tool.

Preview bounds: each axis up to 32 blocks, up to 4096 non-air blocks, up to 128 entity data entries.
Entities are not rendered by this block-model preview.
For multiple palettes the preview uses the first palette without changing the source.
Traversal is bounded to 32 pools, 256 templates and 16 levels of nested element data.

## Inspect Connectors

Open the Jigsaw Blocks button in the model toolbar.
Use its searchable point list or click a cyan model marker.

| Visual/control | Meaning |
| --- | --- |
| Gold cube outline | Selected connector |
| Gold direction line | Outward connection face |
| Cyan direction line | Connector top |
| Cyan point | Another selectable connector, including occluded ones |
| Left-drag | Rotate |
| Wheel over model | Zoom |
| Middle-drag | Pan |
| Double middle-click/reset | Reset view |
| Wheel/drag over properties | Scroll instructions/properties |
| Bottom pool button | Browse this connector's actual candidate templates |

![Connector properties](../images/jigsaw-inspector-en-1920.png)

Wide windows use model/inspector columns; narrow windows use stacked areas.
The bottom pool button stays reachable. Closing nested templates returns to the original point.
The preview exposes only whitelisted jigsaw metadata, not arbitrary container or command-block NBT.

## Fields And Matching

| Field | Role |
| --- | --- |
| `name` | Current connector's own identity |
| `target` | Required name on the candidate connector |
| `pool` | Template pool from which to choose the next piece |
| `final_state` | Replacement block state when jigsaws are not retained |
| `joint` | `aligned` or `rollable` |
| `orientation` | Block-state front/top directions, e.g. `east_up` |
| `selection_priority` | 1.21.1 connector trial priority; larger first |
| `placement_priority` | 1.21.1 queued expansion priority; larger first |

1.20.1 does not provide the two priority fields.
The inspector shows priorities when present in template metadata.
Pool `weight` is a separate concept.

```text
Current A.pool   = demo:rooms
Current A.target = demo:door  ---->  Candidate B.name = demo:door
Current A.front  = east      <-->   Candidate B.front = west
Current A.top    = up               Candidate B.top = up
```

Minecraft tests rotated candidate pieces.
The two fronts must oppose, and **A.target must equal B.name**.
For A's `aligned` joint, top directions must also match.
For `rollable`, top matching is unrestricted.
B's target need not point back to A's name.
Missing/invalid joint metadata defaults to aligned for horizontal fronts and rollable for vertical fronts.

Matching does not guarantee placement: collisions, depth, empty elements, terrain projection and
processors can still affect generation. This is individual-template inspection, not full assembly simulation.

## A Minimal Room

In an administrator creative test world:

```mcfunction
/give @s minecraft:jigsaw
/give @s minecraft:structure_block
```

Build a small room with an outward-facing entry connector B:

```text
name: demo:door
target: minecraft:empty
pool: minecraft:empty
final_state: minecraft:air
joint: aligned
```

Save the room including B with a structure block under `demo:room`.
The world's generated save location is:

```text
<world>/generated/demo/structures/room.nbt
```

Create a datapack:

```text
demo_jigsaw/
  pack.mcmeta
  data/demo/worldgen/template_pool/rooms.json
  data/demo/structure/room.nbt
```

The last path is for **1.21.1**.
For **1.20.1**, use `data/demo/structures/room.nbt` with the plural folder.
The world's generated save path remains plural and is a different location.
Use NBT saved for the target version rather than loading newer templates into an older game.

`pack.mcmeta` for 1.21.1:

```json
{"pack":{"pack_format":48,"description":"Jigsaw room example"}}
```

Use `pack_format: 15` for 1.20.1.

`rooms.json`:

```json
{
  "name": "demo:rooms",
  "fallback": "minecraft:empty",
  "elements": [
    {
      "weight": 1,
      "element": {
        "element_type": "minecraft:single_pool_element",
        "location": "demo:room",
        "processors": "minecraft:empty",
        "projection": "rigid"
      }
    }
  ]
}
```

Restart/re-enter the test world after worldgen registry changes; do not assume a `/reload`
alone refreshed all pool definitions.
Place a starting connector A in open space:

```text
name: demo:start
target: demo:door
pool: demo:rooms
final_state: minecraft:air
joint: aligned
levels: 1
keep jigsaws: enabled for the first test
```

Use the vanilla Generate button to test actual placement.
When satisfied, disable keep-jigsaws and confirm final-state replacement.
Generation changes the world; the mod's inspection panel does not execute it.
The example's full custom datapack generation has not been tested on every loader;
connector semantics and real village-template browsing have focused coverage.

![In-game instructions](../images/jigsaw-guide-en-1920.png)

## Troubleshooting

| Symptom | Check |
| --- | --- |
| No next piece | Pool exists, nonempty elements, A.target/B.name match |
| Names match but placement fails | Opposite fronts, aligned tops, collisions and depth |
| Jigsaws remain | Keep-jigsaws setting and valid final state |
| Empty pool browser | JSON resource availability and empty/feature elements |
| No structure preview | Procedural structure without a start pool |
| Template rejected as large | Size/non-air/entity-data bounds |
| Preview differs from generation | Random weight, processors, terrain and entities are not simulated |
