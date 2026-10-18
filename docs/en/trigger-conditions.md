# Predicate Nodes And Branches

[简体中文](../trigger-conditions.md) | **English** · [JavaScript](javascript.md) · [Documentation](README.md)

The event graph connects an event to conditions and actions.
A task's `script` file condition and a graph's inline JavaScript predicate are different entry points.

## Connect A Predicate

1. Add an event node and choose its real event, such as click or item pickup.
2. Add a predicate and choose Visual or JavaScript mode.
3. Connect the event's `next` output to the predicate.
4. Connect `true` to the success action and optionally `false` to an alternative.
5. Save the graph and its owning task/control.

The graph must be acyclic. Event/delay continuation uses `next`; predicates use `true`/`false`.
Commands, completion and reward actions are terminal rather than generic chainable sequence steps.
Do not rely on multiple terminal branches running in a specific order.

![Predicate selected in the graph](../images/event-graph-current-en-1920.png)

## Visual Mode

Left and right operands can use supported context fields or accessible custom variables;
the right operand can also be a constant.

| Operation | Evaluation |
| --- | --- |
| Equal / not equal | Exact string comparison; `10` differs from `10.0` |
| Greater/less and inclusive variants | Both operands must parse as finite numbers |
| Contains / starts with / ends with | Case-sensitive string operation |

Logical modes: `single` evaluates the first condition; `not` negates it;
`and` requires both groups; `or` requires either.
Switching the displayed condition group preserves the other group's draft.
Custom variables must be global (`globe`) or belong to the current task.

```json
{
  "id": "check_level",
  "type": "js",
  "x": 220,
  "y": 40,
  "data": {
    "mode": "visual",
    "field": "experienceLevel",
    "operator": "gte",
    "expected": "10",
    "logic": "and",
    "field2": "dimension",
    "operator2": "eq",
    "expected2": "minecraft:overworld",
    "once": "false"
  }
}
```

## JavaScript Mode

Write source, not a `.js` path or function name. Source is stored in `data.script`.
Every injected graph context field is a string:

```javascript
eventType === "click" && Number(experienceLevel) >= 10;
```

For multiline code:

```javascript
(function () {
    const level = Number(experienceLevel);
    return eventType === "click" && level >= 10;
})();
```

Only Boolean `true` selects true. Strings, numbers and other normal non-true values select false.
An exception stops execution; it is not a normal false result.
Do not write top-level `return true;`.

## Complete Example

```json
{
  "nodes": [
    {"id": "clicked", "type": "event", "x": 20, "y": 40,
     "data": {"event": "click", "cooldown": "20", "once": "false"}},
    {"id": "check", "type": "js", "x": 230, "y": 40,
     "data": {"mode": "script", "script": "Number(experienceLevel) >= 10;", "once": "false"}},
    {"id": "finish", "type": "complete", "x": 450, "y": 20, "data": {}},
    {"id": "stop", "type": "end", "x": 450, "y": 150, "data": {}}
  ],
  "edges": [
    {"from": "clicked", "to": "check", "port": "next"},
    {"from": "check", "to": "finish", "port": "true"},
    {"from": "check", "to": "stop", "port": "false"}
  ]
}
```

Attach this graph to an actual control. Task unlock state, trigger enablement, hidden controls,
completion policy and scene eligibility can prevent the graph from being entered.

## Once Flags And Cooldown

| Flag | Actual scope |
| --- | --- |
| Event once | Records a normally finished invocation; a false predicate can still count as the event's one use |
| JS predicate once | After the first true result, later evaluations are treated as false |
| Action once | Action-specific execution state; separate from the predicate |

For “not enough levels now, try again after leveling,” keep the event repeatable.
Once state is separated by player, task, control and node.
Trusted task reset can clear relevant runtime state.

Event cooldown is 1-72,000 game ticks, default 20.
Errors can defer the binding for about 200 ticks.
Actual elapsed time grows when server TPS is low.

## Diagnose A Graph

| Symptom | First checks |
| --- | --- |
| Click does nothing | Correct event, saved edges, visible control and eligible task |
| Always false | Boolean result, string/numeric conversion, comparison values |
| Neither branch executes | Server errors, valid ports and event cooldown |
| Works once | Event, predicate and action once flags independently |
| Variable is missing | ID, scope and use of a supported variable slot |
| Locate result is always 0 | Check `locationFound` before converting empty strings |
| KubeJS code fails here | Use the dedicated KubeJS action environment |

[Full JS fields and examples](javascript.md) ·
[Graph validation source](../../src/main/java/xtdpotato/xero_quest_journal/core/model/TriggerGraph.java)
