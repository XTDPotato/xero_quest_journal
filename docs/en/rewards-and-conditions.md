# Rewards And Completion Conditions

[简体中文](../奖励与条件配置.md) | **English** · [Guide](guide.md) · [Commands](commands.md)

Conditions determine eligibility/completion. Rewards are separate actions delivered through
the server's claim flow. Visible progress, completion and receipt state are not interchangeable.

## Common Conditions

| Type | Purpose | Typical target |
| --- | --- | --- |
| `confirm` | Explicit confirmation | Empty |
| `item_hold` | Current inventory count | Item ID, tag or supported encoded stack |
| `item_acquire` | New acquisition events | Item ID/tag/stack |
| `item_submit` | Submission that consumes items | Item ID/tag/stack |
| `kill` | Defeat an entity | Entity ID |
| `block_break` / `block_interact` | Block actions | Block ID |
| `dimension` | Enter/be in a dimension | Dimension ID |
| `location` | Reach coordinates, structure or biome target | Encoded location value |
| `advancement` | Advancement completion | Advancement resource ID |
| `event` / `stage` | Named counters | Author-defined event/stage name |
| `script` | Built-in JS file predicate | Relative `.js` path |
| `quest` | Referenced task completion | Task ID |

The editor also exposes supported logical group conditions such as `all`, `any`, `count`
and `ratio`. Use the visual condition tree for child structure rather than inventing nested syntax.
Clicking a condition in a player detail view opens inspection; item requirements provide item detail.

![Condition tree](../images/conditions-editor-en-1920.png)

## Matching Items

| Form | Meaning |
| --- | --- |
| `minecraft:oak_log` | Registry item identity, regardless of components |
| `#minecraft:logs` | Any item in the currently loaded tag |
| `xqj-stack:` value | Exact supported item/component identity, ignoring count |
| `xqj-model:` value | Model identity for supported integrations |

Tags come from current registries. A mod's log counts only if it belongs to the selected tag.
Tags are condition matchers, not a fixed reward item.
Exact stacks can differ when durability, enchantments, ammunition or custom data changes.
Use the shared picker to author an encoded value.

Acquisition counting is not an economy-proof transaction log: dropping and picking up an item
can count again. Use holding/submission or another condition when that matters.

## Reward Types

The editor supports item, experience/levels, command, datapack function, script, effects,
loot and supported graph-based actions. Choose the type before editing its target.
Reward source/command/script validation is specific to that type.

```json
{
  "rewardMode": "manual",
  "rewards": [
    {"type": "item", "item": "minecraft:diamond", "count": 2, "quality": "rare"},
    {"type": "experience", "item": "", "count": 120}
  ]
}
```

Completed rewards are frozen for the completion record. Editing a task later does not
rewrite the already frozen reward. Counts and item data are validated by the server.

![Rewards](../images/rewards-editor-en-1920.png)

## Quality And Background

| `quality` | Card color |
| --- | --- |
| Empty / `auto` | Automatic |
| `common` | Gray |
| `uncommon` | Green |
| `rare` | Blue |
| `epic` | Purple |
| `legendary` | Gold |
| `mythic` | Red |

An optional `background` uses `#RRGGBB`.
Explicit background overrides explicit quality, which overrides the item's native rarity.
Changing quality in the editor clears an old background override to avoid misleading colors.
These options affect presentation, not item damage, enchantments or server attributes.

## Loot Tables

| Top field | Meaning |
| --- | --- |
| ID | Stable reference used by rewards and mail |
| Display name | Reader-facing table name |
| Mode | Weighted random selection or supported choice flow |
| Roll count | Number of selections in random mode |
| Claim display text | Text for the choice presentation |
| External display icon | Icon shown outside and inside the table's detail popup |

**Roll count is different from entry count.** Two rolls can select an entry whose count is eight,
awarding that entry's eight items per applicable selection.
Entry weight determines relative random selection weight; disabled/condition-failing entries
do not behave like ordinary eligible entries. The popup displays the table contents and
available probability/weight information; it does not guarantee an outcome.

![Loot editor](../images/loot-editor-en-1920.png)

The default external icon is a gift. Item entries support quality/background styling.
Each entry can set `detailText` (up to 512 characters), edited in the Display Detail Text
field below its presentation controls. Blank uses the default hint.
This text appears beneath the reward name instead of the generic choice hint.
It is carried in safe catalog data and frozen choice snapshots.
Contents and choice lists show search only above 10 original entries; filtering matches
localized name, resource ID, type and detail text while preserving original choice indexes.
Inspecting a candidate through its separate detail button does not select it.
Save the table before referring to its ID from a reward or mail attachment.
The editor keeps a fixed save footer and smooth-scrolling entry list.

![Searchable choice contents](../images/loot-search-en-1920.png)

![Choice selection with detail buttons](../images/choice-loot-search-en-1920.png)

## Inspect After Claiming

Claimed task/mail item cards retain their inspection action.
XP points and levels open a detail view with their own icon, amount and explanation;
they do not display a nonfunctional item-recipe control.
Loot contents display their external icon and each candidate's icon.
Viewing any of these panels does not deliver or claim the reward again.

![Experience reward](../images/experience-reward-details-en-1920.png)

## Claims, Failures And Receipts

Completion can leave rewards unclaimed. A claim may be partial or blocked by inventory capacity.
Review the UI result and choose the applicable overflow confirmation when offered.

Commands/scripts can create side effects before a later failure.
The system retains receipts; do not blindly execute the same action again.
Administrator `receipts` inspects entries; `acknowledge` marks an existing failed/running receipt
done without executing the reward. See [commands](commands.md#claims-and-reward-receipts).

Definition undo does not reverse delivered rewards or player progress.
Exceptional process interruption is not a cross-file atomic transaction across inventory,
progress and external mod effects; administrators may need to reconcile an interrupted action.
