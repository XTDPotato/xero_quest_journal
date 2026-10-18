# Undo And Redo Scope

[简体中文](../task-edit-undo.md) | **English** · [Guide](guide.md) · [Verification](testing-and-compatibility.md)

Saved task authoring operations have per-editor server history.
Toolbar buttons expose undo/redo; common shortcuts are `Ctrl+Z`, `Ctrl+Y` and `Ctrl+Shift+Z`.
Focused input fields keep their own text-editing behavior.
Finish or cancel a task form before requesting task-level history.

## Supported Definition Changes

- Saved task properties, text, icons, conditions and reward definitions.
- Source, controls, event graphs and canvas size saves.
- Individual/batch task deletion and group deletion.
- Related prerequisite and chapter references changed by the same operation.
- New tasks, copies, paste batches, supported chapters, ordering and templates.

The operation restores related files together.
New successful edits discard the redo branch; rejected edits do not create history.
Local layout/editor drafts have their own history rules.

## Persistence And Conflicts

History belongs to the editor in the current server session.
Closing/reopening a task screen does not clear it.
The stack is bounded to 64 steps and approximately 32 MiB of text; older entries can be evicted.
Leaving the world/restarting does not preserve the session undo stack.
Supported disk backups remain under `config/xero_quest/history/`.

Undo/redo requires author permission and compares the relevant file fingerprints.
If another editor or external tool changed a file, restoration rejects the operation as a whole,
avoiding partial or stale writes.

## Progress And Rewards

History changes task definitions. It does not reverse player counters, completion,
frozen reward snapshots, awarded items, experience, mail or external command effects.
Runtime variable mutations and trusted progress reset are not definition undo.

## Evidence And Known Gap

Historical creation/history checks and focused canvas checks exist for the four targets.
Later broad creation testing has an unresolved `context-copy_id` click failure.
The continuous journey regression also has an unresolved appearance-draft undo assertion.
Do not read task-level history support as proof that every appearance/editor undo path passed.
Back up definitions and the world independently of the UI history.
