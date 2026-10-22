# Interface Gallery And Operations

[简体中文](../interface-gallery.md) | **English** · [Documentation](README.md) · [Verification](testing-and-compatibility.md)

These are actual **English, native 1920×1080** client screenshots of 0.2.1.
The paired Chinese gallery uses the same demonstration scenario.
Sample tasks, mail and anecdote records illustrate authoring; they do not promise automatic rewards.
IDs, code and user-authored resource names can remain untranslated.

## Journal And Overview

Open `J` or `/xero_quest open journal`. The left list contains category headings, groups and tasks.
The right pane shows the selected task; rewards remain at the bottom.
Complete/unclaimed tasks stay in progress until claiming completes.

![Journal and bottom rewards](../images/journal-current-en-1920.png)

Open `/xero_quest open overview` for recommendations and category navigation.
The overview's journey and anecdote entries open their corresponding screens.

![Overview](../images/overview-current-en-1920.png)

In author mode, right-click a task for grouped operations such as properties, conditions,
rewards and relationships. Menu icons identify actions.

![Task context menu](../images/task-context-menu-en-1920.png)

Properties edit the task draft. Save after changing its title, icon or content.
Changing a definition is separate from resetting player progress.

![Task properties](../images/task-properties-en-1920.png)

## Search And Filters

The development build combines search scopes and filters in one vertically scrolling panel.
Title, description, subtitle and internal-content scopes can be combined. Close the persistent menu explicitly.

![Combined search and filters, development build, native English 1920×1080](../images/combined-search-filters-en-1920.png)

Filters in the same panel narrow supported category, reward and condition metadata.
Selections work with the search query rather than changing definitions.

See [Search and unlock graph](task-search-and-unlock-graph.md) for the new icons, matching logic and minimap.

## Conditions And Rewards

The condition tree chooses type, target, count and supported logical children.
Item conditions, location conditions and scripts have different target formats.

![Conditions](../images/conditions-editor-en-1920.png)

The reward editor configures type, target, count and presentation quality/background.
An item's display quality does not change the real item's attributes.

![Reward editor](../images/rewards-editor-en-1920.png)

Claim receipts present received rewards separately from the authoring screen.
A screenshot of a receipt is not a guarantee about every claim/failure path.

![Reward receipt](../images/reward-receipt-en-1920.png)

## Loot Tables

Top-left is the stable **ID**, top-right is **display name**.
The value beside random/choice mode is roll count.
Each entry separately configures its item/action, quantity, weight, condition and appearance.
The external icon defaults to a gift.
Each entry can replace the generic choice hint with custom detail text.
Above 10 original entries, contents and selection lists expose a search field.
The detail button inspects without selecting or claiming.

![Loot editor](../images/loot-editor-en-1920.png)

![Searchable loot contents](../images/loot-search-en-1920.png)

![Searchable choice list](../images/choice-loot-search-en-1920.png)

Claimed item cards remain inspectable. XP/level detail panels include a representative icon,
the reward amount and an explanation.

![XP reward detail](../images/experience-reward-details-en-1920.png)

## Mail

Use `F8` or `/xero_quest open mailbox`. Select mail on the left to read the body and bottom attachments.
Claim/delete rules preserve protected unclaimed attachments and favorites where applicable.

![Mailbox](../images/mailbox-current-en-1920.png)

The compose editor sets recipients, sender, subject, body, expiry and attachments.
Send/save/export actions have different effects; inspect the action label before committing.

![Mail composition](../images/mail-compose-en-1920.png)

Mail attachments use the shared reward editor and preserve their quality/background presentation.

![Mail attachments](../images/mail-attachments-en-1920.png)

Rich text provides source editing with preview.
Toolbar formatting wraps selected text in tags rather than appending an unrelated tag after it.

![Rich text](../images/rich-text-editor-en-1920.png)

## Journey

Use the overview's journey entry or a task's “View in journey” action.
Tabs select chapters; drag the canvas to browse.
The selected-tab underline defaults to 3px, configurable from 1 to 5 in Appearance settings.

![Journey timeline at default 3px](../images/journey-current-en-1920.png)

The pencil enters edit mode, the pencil with X exits it, and the checklist selects chapter tasks.
Right-click a chapter/task for supported author actions. Undo/redo have dedicated toolbar entries.

![Journey edit mode](../images/journey-editor-en-1920.png)

Appearance changes from this entry are chapter-local.
Select one or multiple elements, drag to move, and use width/height/proportional handles.
The left preview reflects the right inspector's draft.

![Chapter appearance](../images/journey-appearance-en-1920.png)

Click a task card to open its details in a popup occupying about 75% of the viewport.
The footer shows the task rewards only; it does not add a redundant “Open task screen” button.
Close returns to the journey.

![Journey task popup](../images/journey-task-details-popup-en-1920.png)

## Anecdotes

`/xero_quest open anecdotes` opens a separate book.
Select an entry on the left and read its multiple records on the right.
Locked/undiscovered entries and their visibility are authored per entry.

![Anecdote book](../images/anecdotes-current-en-1920.png)

Completed records remain available as stickers. The reward page displays completion history;
new stickers use an entry animation and are acknowledged after presentation.
See [anecdote configuration](anecdotes.md).

![Anecdote stickers](../images/anecdotes-rewards-en-1920.png)

## Resource Selection

The item picker searches the full result set and renders the visible portion.
Choosing a tile stages selection; Apply commits it. Sorting, metadata and smooth scrolling
remain available. Apply sits immediately left of Cancel at the bottom right; both have
equal compact widths and text padding. The right scrollbar supports thumb dragging and
smooth track navigation.

![Item selection](../images/item-picker-en-1920.png)

Texture selection uses the same workflow with resource/image references.

![Texture selection](../images/texture-picker-en-1920.png)

The full-screen preview provides zoom, pan and double-click reset/zoom interaction.

![Full-screen item preview](../images/icon-fullscreen-preview-en-1920.png)

## Graphs And Scripts

The node library groups event, condition and action nodes.
Selecting a node type inserts a graph draft; it does not immediately execute an action.

![Node library](../images/node-library-current-en-1920.png)

Graph property fields can open typed selectors.
Editable text/script regions support full editor access.

![Event graph](../images/event-graph-current-en-1920.png)

The script editor identifies its execution purpose and offers the matching field reference.
Graph context values are strings; file conditions expose numeric level/coordinates.

![Script reference](../images/js-editor-reference-en-1920.png)

Examples are purpose-filtered and append to the existing draft.
Saving checks syntax and does not run rewards. See [all shared examples](javascript.md).

![Script examples](../images/js-editor-examples-en-1920.png)

The unlock graph is for task prerequisites, rather than event flow.
Cycles and self-dependencies are invalid.

![Unlock graph](../images/unlock-graph-current-en-1920.png)

## Variables

The variable catalog identifies values and scopes.
Select task-local/global values deliberately, including sharing and ownership.

![Variable catalog](../images/variable-catalog-en-1920.png)

The variable editor declares type and sharing.
Use numeric types for visual calculations; string values are not implicit numbers.

![Variable editor](../images/variable-editor-en-1920.png)

## Templates And Locations

Templates start a task draft with reusable content.
Applying a template should be followed by editing and validating the resulting task.

![Task templates](../images/task-templates-en-1920.png)

A location may be coordinates, a world structure or a biome.
Keep dimension and coordinate identity together.

![Location editor](../images/location-editor-en-1920.png)

## Structure And Jigsaw Inspection

World structure preview reads actual referenced templates when available.
Left-drag rotates, wheel zooms and middle-drag pans.

![Structure template](../images/structure-template-en-1920.png)

The jigsaw panel lists connectors, highlights selection in gold and displays front/top directions.
Open a connector's pool to inspect candidate templates.

![Jigsaw properties](../images/jigsaw-inspector-en-1920.png)

Instructions explain acquisition, connector matching, pool preparation and actual generation.
This viewer does not simulate complete random assembly.

![Jigsaw instructions](../images/jigsaw-guide-en-1920.png)

## Background Views

Legendary quests use their own presentation and configured media.

![Legendary quests](../images/legendary-current-en-1920.png)

Hot events display linked cards on a movable/scalable canvas.
Backgrounds and music can be authored separately from card layout.

![Hot events](../images/hot-events-current-en-1920.png)

## Candidates And Recycling

Candidate lists are searchable; Add opens the relevant selector.
Adding a value and committing the original field are separate operations.

![Candidate list](../images/value-picker-current-en-1920.png)

The recycle bin manages custom candidate removals and restoration.
It does not delete Minecraft registry content.

![Candidate recycling](../images/suggestion-recycle-bin-en-1920.png)

## Settings And Animation

Open `/xero_quest open settings`. Personal settings work without author mode;
server-owned actions retain authorization checks.

![General settings](../images/settings-general-en-1920.png)

Motion options include reduced motion, popup duration and animation editor entries.

![Motion settings](../images/settings-motion-en-1920.png)

Notification controls adjust overall/icon size and offer a preview.
The pickup notice size setting scales the whole group from 50% to 200%. X/Y position the
scaled group within the usable screen area: X runs left-to-right and Y top-to-bottom.
Zero and 100 align to each axis's ends; defaults are size=100%, X=0%, Y=40%.
Values can be typed or stepped, take effect immediately and are saved locally.
Automatic minimap avoidance reserves a selected corner when Xaero, JourneyMap, VoxelMap or
FTB Chunks is detected. Task tracking and notice groups are placed in priority order and try
other free corners on collision. The corner is a selectable estimate, not a live read of
third-party private HUD settings; disable the reservation if the minimap uses another layout.

![Notification settings](../images/settings-notifications-en-1920.png)

When a tracked item objective gains an item, the HUD shows its icon, localized name, picked
count and objective progress. Up to four notices are visible, newest first; rows slide in,
remain briefly, then fade out. Timers pause while a screen is open.

![Tracked item pickup notices](../images/item-pickup-toast-en-1920.png)

Appearance includes the new selected-tab underline height, tracking marker size,
theme and background controls.

![Appearance settings](../images/settings-appearance-en-1920.png)

Task settings include pagination, reward sources and context-menu placement.

![Task settings](../images/settings-tasks-en-1920.png)

Tools provide key-binding/configuration entries and group-layout reset options.

![Tools settings](../images/settings-tools-en-1920.png)

The animation editor exposes keyframes, timeline and property fields.
Preview runs once unless repeat is enabled; default motion uses scale/fade.

![Animation editor](../images/animation-editor-current-en-1920.png)

## Inventory Placement

Task/mail entries have independent visibility and relative anchor offsets.
Drag entries in the preview or input values; the top-left anchor uses `(0,0)` at the screen origin.

![Inventory placement](../images/inventory-placement-en-1920.png)

## Capture And Coverage

Images are captured in the NeoForge 1.21.1 client with shared UI source.
Capture manifests identify scene, locale, dimensions, timestamp, screen class and SHA256.
The [screenshot index](../images/README.md) records reproduction commands.
Screenshots show visible states, not proof that every interaction or platform combination passed.
