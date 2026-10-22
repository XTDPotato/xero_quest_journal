# Anecdotes, Records And Stickers

[简体中文](../anecdotes.md) | **English** · [Guide](guide.md) · [Commands](commands.md)

The Anecdote Chronicle is an independent two-page book.
Open it from the overview's anecdote entry or `/xero_quest open anecdotes`.
`/xero_anecdote @s` only plays a banner; it does not open the book or complete a record.

![Anecdote book](../images/anecdotes-current-en-1920.png)

## Read And Author

Select a left-page card to read its records on the right.
Hover enlarges a card; holding shrinks it. Selection occurs only after release in the same card.
Cards and records have independent smooth scrolling.
An undiscovered entry may remain visible as locked or be hidden according to its definition.

Author mode exposes entry/record editing through context actions.
An entry supports multiple records, and completed records remain in the book.
The reward page always displays four columns and ten slots. New stamps enter oldest first.
A full page shrinks away and resets to zero before the next records enter; completion history is retained.
The right page always shows progress, instructions and rewards, including when there are no stamps.
Each batch of ten uses the rewards configured on the tenth record's entry (50 diamonds by default).
Claiming requires release inside the pressed gift button; server receipts prevent duplicate claims.
Insufficient inventory space rejects the claim.

![20% gray popup scrim, four-column stickers and bottom map watermark (Chinese client, 1920×1080)](../images/anecdote-rewards-layout-zh-1920.png)

In author mode, choose **Edit** on the reward page and select the ticket, reward-item group,
or claim button. The right page exposes exact X/Y/scale fields with a live preview.
Dragging snaps to 8 canvas units; hold Shift to temporarily bypass snapping.
Wheel resizing, numeric changes, and drags support undo/redo. Saving persists the selected
entry's `rewardLayout`.

![Precise reward-control coordinates, scale input and snapping controls (English client, 1920×1080)](../images/anecdote-reward-layout-editor-en-1920.png)

## Independent Files

Save each entry in `config/xero_quest/anecdotes/<id>.json`.

| Field | Purpose |
| --- | --- |
| `id` | Stable entry ID |
| `title` | Entry title |
| `cardText` | Card-bottom label; long content is fitted |
| `icon` | Card image/item reference |
| `heading` | Right-page heading |
| `records` | Up to 32 records; IDs must be unique within the entry |
| `defaultDiscovered` | Whether the entry starts discovered |
| `hideWhenLocked` | Whether an undiscovered entry is hidden |
| `order` | Display ordering value |
| `rewards` | Reward definitions |
| `cardLayout` | Per-entry background/image/text geometry, colors and font size; legacy files inherit defaults |

## Live Appearance Editing

The left preview and the actual chronicle share one renderer. The right pane has Content and Appearance tabs.
Select an image, background or caption in the preview. Drag its body to move it,
the right midpoint to resize width, the bottom midpoint to resize height, and the corner to scale proportionally.
Numeric position/size percentages, scale and font size update the preview immediately.
Background images and colors are supported; colors use `#RRGGBB` or `#RRGGBBAA`.
The form scrolls smoothly within its viewport. Resizing preserves the draft.
Toolbar undo/redo and `Ctrl+Z`/`Ctrl+Y` cover field edits and whole drag operations.
Closing or cancelling discards unsaved edits. Record/reward edits preserve the card's appearance.

PNG assets are in [the anecdote directory](../../src/main/resources/assets/xero_quest_journal/textures/gui/anecdote)
and [the navigation directory](../../src/main/resources/assets/xero_quest_journal/textures/gui/navigation).
The book is 1800×1000, the title note 1024×240, sticker base 192×192, seal 512×256 and ticket 768×160.
Category, refresh, fullscreen and sort icons are 64×64.

Run `./gradlew.bat runClient -PanecdoteAuthoringTest` with Java 21 for the focused editor,
PNG visibility, persistence, close/translation, page-reset and reward-receipt checks.

Each record has `id`, `title`, `body` and optional `completedIcon`.
An omitted/empty reward list inherits the model's default 50 legendary-quality diamonds.
Authors should explicitly review and configure the intended rewards.

```json
{
  "id": "mountain_letter",
  "title": "A mountain letter",
  "cardText": "Letters",
  "icon": "minecraft:book",
  "heading": "Greetings from afar",
  "defaultDiscovered": false,
  "hideWhenLocked": false,
  "order": 10,
  "records": [
    {
      "id": "meeting",
      "title": "First meeting",
      "body": "You met a traveler on the mountain path.",
      "completedIcon": "minecraft:compass"
    }
  ],
  "rewards": [
    {"type": "item", "item": "minecraft:diamond", "count": 2, "quality": "rare"}
  ]
}
```

## Graph Actions

Discover an entry:

```json
{"id":"discover_letter","type":"discover_anecdote","x":220,"y":40,
 "data":{"target":"mountain_letter"}}
```

Complete a record. This action also discovers the entry if it is still locked:

```json
{"id":"finish_meeting","type":"complete_anecdote","x":220,"y":140,
 "data":{"entry":"mountain_letter","record":"meeting"}}
```

The selectors show anecdote and record titles, with their stable IDs shown alongside for verification.
Attach the action to a real event/predicate branch and save the owning graph/task.
These are terminal actions, not generic sequential chaining steps. A completion action validates its entry and record,
discovers the entry if needed, records completion once, and syncs the page.
Use explicit discovery when the story should unlock before completion. Do not depend on parallel branch order.

Discovery, completion timestamps and unread completion state belong to player world progress.
Editing a definition does not automatically clear that state.
Reward delivery still follows the existing permissions, claim and receipt rules.

In edit mode, change rewards from **Edit entry rewards** in the entry properties; the separate toolbar plus is removed.
The reward sticker page is a scaled book popup over the chronicle, with a book-closing transition.
The claim button stays inside the ticket's right section, whose center span is cropped by 30% to fit the page.
Reward items in its ticket can be hovered and clicked to open the shared item-details view.
In edit mode, click **Edit** on the reward page to adjust the ticket, item group or claim button.
Select a control, drag to move it, and use the wheel to resize. Undo/redo and Ctrl+Z/Ctrl+Y manage the draft.
Saving stores the layout on the current reward-source anecdote entry; controls stay within the ticket zones.

![Anecdote reward layout editor, English client at native 1920×1080](../images/anecdote-reward-layout-editor-en-1920.png)
