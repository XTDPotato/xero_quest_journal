# Command Reference

[简体中文](../commands.md) | **English** · [Documentation](README.md) · [User guide](guide.md)

The four command roots are `/xero_quest`, `/xero_toast`, `/xero_structure` and `/xero_anecdote`.
This reference covers the four released 1.20.1/1.21.1 targets.
The complete exported command tree and permission levels appear at the end.

## Arguments And Permissions

| Notation | Meaning |
| --- | --- |
| `<argument>` | Required; omit the angle brackets when typing |
| `[argument]` | Optional; omit the square brackets |
| `<targets>` | Online player selector, such as a name, `@s` or `@a` |
| `<target>` / `<player>` | Exactly one player |
| `<id>` | Task or mail ID for that command, rather than its display name |
| `<reference>` | Structure-library relative path |
| Permission `0` | Player-accessible branch; some still require a player command source |
| Permission `2` | Administrator branch |

Use `/` in chat. Server consoles normally omit it.
`@s` requires a player source; from a console use a player name or `@a`.
Quote sender/subject strings containing spaces. A final body/content argument consumes the remaining
text. A command returning or printing a status is not by itself proof that an item was delivered.

## Open Screens

```text
/xero_quest open journal [targets]
/xero_quest open overview [targets]
/xero_quest open anecdotes [targets]
/xero_quest open mailbox [targets]
/xero_quest open settings [targets]
/xero_quest open templates [targets]
/xero_quest open task <id> [targets]
```

Opening your own screen requires permission 0; supplying a target selector requires permission 2.
Opening a screen does not grant authoring permission.
The task completion chat link executes `/xero_quest open task <id>` for the reader.

```text
/xero_quest open task first_logs
/xero_quest open anecdotes
/xero_quest open mailbox @a
```

## Reload And Author Mode

```text
/xero_quest reload
/xero_quest list
/xero_quest edit_mode <targets> <enabled>
```

All require permission 2. `enabled` is `true` or `false`.
Reload refreshes configuration and definitions; parsing/validation errors cause a failure.
Author mode sets the targeted player's author flag and synchronizes it to the client.

```text
/xero_quest edit_mode @s true
/xero_quest reload
```

Forge 1.20.1 additionally exposes `/xero_quest status` at permission 0, including console use.
It confirms that the platform entry point loaded; it is not a full functional test.

## Task Runtime Actions

```text
/xero_quest trigger complete <targets> <id>
/xero_quest trigger evaluate <targets> <id>
/xero_quest trigger advance_step <targets> <id>
/xero_quest trigger unlock <targets> <id>
/xero_quest trigger lock <targets> <id>
/xero_quest trigger reset <targets> <id>
```

All require permission 2.

| Action | Behavior |
| --- | --- |
| `complete` | Request trusted completion, subject to the runtime's eligibility rules |
| `evaluate` | Re-evaluate current conditions |
| `advance_step` | Advance the current step of a step task |
| `unlock` / `lock` | Unlock/lock a task; these actions also accept group IDs |
| `reset` | Trusted reset of task runtime and associated control/graph state |

Resetting task state does not reverse external commands, scripts or inventory side effects.

## Event And Stage Counters

```text
/xero_quest event event <targets> <name> <amount>
/xero_quest event stage <targets> <name> <amount>
```

Permission 2; amount is an integer from 1 to 1,000,000.
These add condition counters and evaluate tasks. They do not simulate every vanilla graph event.

```text
/xero_quest event event @s dungeon:room_cleared 1
/xero_quest event stage @a story.chapter_1 1
```

## Claims And Reward Receipts

```text
/xero_quest reward claim_all
/xero_quest reward claim <id>
/xero_quest reward claim_drop <id>
/xero_quest reward receipts <target> <id>
/xero_quest reward acknowledge <target> <id> <index>
```

The first three are self-service player operations. `claim_drop` enables the applicable
overflow/drop path. Eligibility, previous claims and scene-inventory rules still apply.

`receipts` and `acknowledge` require permission 2.
Receipt indexes start at 0. Acknowledgment accepts an existing failed/running entry at indexes
0-127, marks it done, and **does not execute or reissue the reward**.
Inspect the real result before acknowledging a non-idempotent command or script.

## Daily Commissions

```text
/xero_quest daily claim
/xero_quest daily status <player>
/xero_quest daily reroll <targets>
```

`claim` requests your daily all-complete bonus. `status` and `reroll` require permission 2.
Daily count, reset hour and bonus are configured through the existing global settings;
there is no `daily set_count` command.

## Generate And Delete Generated Tasks

```text
/xero_quest generate [target] [rewards] [multiplier]
/xero_quest delete_generated
/xero_quest delete_generated <target>
/xero_quest delete_generated <target> confirm
```

Permission 2. Optional arguments are positional:

| Argument | Values | Default |
| --- | --- | --- |
| `target` | `all` or an installed namespace | All |
| `rewards` | `tier`, `recipe`, `output`, `none` | `tier` |
| `multiplier` | Integer 1-64 | `1` |

Generation runs in the background and preserves existing task IDs.
`tier` uses difficulty-based rewards; `recipe` tries ingredients; `output` rewards the target item;
`none` creates no rewards. Installed content determines which tasks can be generated.
Deletion executes only with the final `confirm`; the shorter routes display a prompt.

```text
/xero_quest generate minecraft output 4
/xero_quest generate all none
/xero_quest delete_generated minecraft confirm
```

## Global Configuration

```text
/xero_quest config show
/xero_quest config set max_tracked <value>
/xero_quest config set manual_rewards <value>
/xero_quest config set default_item_count <value>
/xero_quest config set animation_ms <value>
/xero_quest config set task_pagination <value>
```

Permission 2. Values: `max_tracked` 1-3; `default_item_count` 1-1,000,000;
`animation_ms` 0-2000; the other two are Boolean.
Global page animation duration and the client's popup duration are separate settings.
Popup defaults are 100 ms; global disable/reduced motion can suppress animation.

## Send Mail

```text
/xero_quest mail list <target>
/xero_quest mail send <targets> <id> <sender> <subject> <expires_seconds> <body...>
/xero_quest mail send_item <targets> <id> <sender> <subject> <expires_seconds> <item> <count> <body...>
/xero_quest mail send_encoded <targets> <id> <sender> <subject> <encoded_body>
/xero_quest mail send_item_encoded <targets> <id> <sender> <subject> <item> <count> <encoded_body>
/xero_quest mail send_saved <targets> <template>
/xero_quest mail send_bundle <targets> <bundle>
```

All require permission 2.
Expiry is 0-31,536,000 seconds; 0 means no real-time expiry.
Item IDs must exist, and count is 1-1,000,000.
Encoded bodies use URL-safe Base64 of UTF-8, decoded limit 8192 bytes.
Encoded send variants use expiry 0.

`send_saved` resolves a server-side template ID.
`send_bundle` accepts a URL-safe Base64 `MailDraft` JSON payload, up to 32,000 encoded characters.
Use the editor's exported bundle command for complex attachments.
Saved/bundle delivery checks existing IDs; repeated IDs are not a mechanism for duplicating attachments.

```text
/xero_quest mail send @s welcome_mail "Explorers Guild" "Welcome" 0 Open the journal to begin.
/xero_quest mail send_item @s supplies "Quartermaster" "Starting supplies" 0 minecraft:bread 8 Food for the road.
/xero_quest mail send_saved @a weekly_supply
```

## Mail Operations And Toasts

```text
/xero_quest mail read <targets> <id>
/xero_quest mail star <targets> <id>
/xero_quest mail delete <targets> <id>
/xero_quest mail claim <targets> <id>
/xero_quest mail claim_all <targets>
/xero_quest mail delete_read <targets>
/xero_toast <targets> <content...>
```

All these administrative routes require permission 2. `star` toggles the favorite state.
Mail attachment, expiry, favorite protection and claim rules still apply.
Personal mailbox controls provide the ordinary player's own actions.
Toast content must be nonempty and at most 2048 characters.

## Structure Scenes

```text
/xero_structure leave
/xero_structure reset
/xero_structure edit <reference...>
/xero_structure import <template...>
/xero_structure draft
/xero_structure save
```

`leave` and `reset` are player operations. The remaining branches require a player source
with permission 2. Reference/template consume the rest of the command.
Normal leave restores the player's formal state; reset resets the scene instance.
Import copies a vanilla/datapack template. Draft/save create independent scene copies.
Use normal scene entry/return paths so inventory and recovery checkpoints remain consistent.

## Anecdote Notification

```text
/xero_anecdote <targets> [x] [y] [scale] [position]
/xero_anecdote @s
/xero_anecdote @s 16 -8 0.7 top,left
/xero_anecdote @a 0 0 1.1 bottom,right
```

Permission 2. Arguments are positional. Defaults: `x=0`, `y=0`, `scale=0.7`, `position=top`.
Scale is 0.1-2. Position combines `top`/`bottom` with `left`/`start` or `right`/`end`;
`center` centers an unspecified axis. Opposing anchors on the same axis are rejected.
Top anchoring leaves a 4 GUI-pixel margin.

Each command enqueues one approximately 3.2-second notification. Queued notifications wait for
the previous full animation. Reconnecting clears the queue.
This banner is separate from opening the [anecdote book](anecdotes.md).

## Complete Exported Command Tree

The shared routes below come from the checked-in runtime export.
Optional arguments appear as separate executable leaf paths. Forge 1.20.1 adds
`/xero_quest status` at permission 0.

<!-- COMMAND_ROUTES_START -->

| Command | Minimum permission |
| --- | --- |
| `/xero_quest config set animation_ms <value>` | 2 |
| `/xero_quest config set default_item_count <value>` | 2 |
| `/xero_quest config set manual_rewards <value>` | 2 |
| `/xero_quest config set max_tracked <value>` | 2 |
| `/xero_quest config set task_pagination <value>` | 2 |
| `/xero_quest config show` | 2 |
| `/xero_quest daily claim` | 0 |
| `/xero_quest daily reroll <targets>` | 2 |
| `/xero_quest daily status <player>` | 2 |
| `/xero_quest delete_generated` | 2 |
| `/xero_quest delete_generated <target>` | 2 |
| `/xero_quest delete_generated <target> confirm` | 2 |
| `/xero_quest edit_mode <targets> <enabled>` | 2 |
| `/xero_quest event event <targets> <name> <amount>` | 2 |
| `/xero_quest event stage <targets> <name> <amount>` | 2 |
| `/xero_quest generate` | 2 |
| `/xero_quest generate <target>` | 2 |
| `/xero_quest generate <target> <rewards>` | 2 |
| `/xero_quest generate <target> <rewards> <multiplier>` | 2 |
| `/xero_quest list` | 2 |
| `/xero_quest mail claim <targets> <id>` | 2 |
| `/xero_quest mail claim_all <targets>` | 2 |
| `/xero_quest mail delete <targets> <id>` | 2 |
| `/xero_quest mail delete_read <targets>` | 2 |
| `/xero_quest mail list <target>` | 2 |
| `/xero_quest mail read <targets> <id>` | 2 |
| `/xero_quest mail send <targets> <id> <sender> <subject> <expires_seconds> <body>` | 2 |
| `/xero_quest mail send_bundle <targets> <bundle>` | 2 |
| `/xero_quest mail send_encoded <targets> <id> <sender> <subject> <encoded_body>` | 2 |
| `/xero_quest mail send_item <targets> <id> <sender> <subject> <expires_seconds> <item> <count> <body>` | 2 |
| `/xero_quest mail send_item_encoded <targets> <id> <sender> <subject> <item> <count> <encoded_body>` | 2 |
| `/xero_quest mail send_saved <targets> <template>` | 2 |
| `/xero_quest mail star <targets> <id>` | 2 |
| `/xero_quest open journal` | 0 |
| `/xero_quest open journal <targets>` | 2 |
| `/xero_quest open anecdotes` | 0 |
| `/xero_quest open anecdotes <targets>` | 2 |
| `/xero_quest open mailbox` | 0 |
| `/xero_quest open mailbox <targets>` | 2 |
| `/xero_quest open overview` | 0 |
| `/xero_quest open overview <targets>` | 2 |
| `/xero_quest open settings` | 0 |
| `/xero_quest open settings <targets>` | 2 |
| `/xero_quest open task <id>` | 0 |
| `/xero_quest open task <id> <targets>` | 2 |
| `/xero_quest open templates` | 0 |
| `/xero_quest open templates <targets>` | 2 |
| `/xero_quest reload` | 2 |
| `/xero_quest reward acknowledge <target> <id> <index>` | 2 |
| `/xero_quest reward claim <id>` | 0 |
| `/xero_quest reward claim_all` | 0 |
| `/xero_quest reward claim_drop <id>` | 0 |
| `/xero_quest reward receipts <target> <id>` | 2 |
| `/xero_quest trigger advance_step <targets> <id>` | 2 |
| `/xero_quest trigger complete <targets> <id>` | 2 |
| `/xero_quest trigger evaluate <targets> <id>` | 2 |
| `/xero_quest trigger lock <targets> <id>` | 2 |
| `/xero_quest trigger reset <targets> <id>` | 2 |
| `/xero_quest trigger unlock <targets> <id>` | 2 |
| `/xero_structure draft` | 2 |
| `/xero_structure edit <reference>` | 2 |
| `/xero_structure import <template>` | 2 |
| `/xero_structure leave` | 0 |
| `/xero_structure reset` | 0 |
| `/xero_structure save` | 2 |
| `/xero_toast <targets> <content>` | 2 |
| `/xero_anecdote <targets>` | 2 |
| `/xero_anecdote <targets> <x>` | 2 |
| `/xero_anecdote <targets> <x> <y>` | 2 |
| `/xero_anecdote <targets> <x> <y> <scale>` | 2 |
| `/xero_anecdote <targets> <x> <y> <scale> <position>` | 2 |

<!-- COMMAND_ROUTES_END -->

## Source And Scope

[Shared command export](../reference/commands-runtime.json) ·
[Forge export](../reference/commands-forge-runtime.json) ·
[Command registration](../../src/main/java/xtdpotato/xero_quest_journal/core/QuestCommand.java)

Historical exports and current validation scope are described in
[testing and compatibility](testing-and-compatibility.md).
