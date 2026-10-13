# 判断节点与 JS 分支

[项目首页](../README.md) · [JavaScript 参考](javascript.md) · [全部命令](commands.md)

事件图用于把“什么时候发生”与“满足什么条件后执行什么动作”连接起来。
任务条件编辑器中的 `script` 文件条件，与事件图的 JS 判断节点是不同入口。

## 从开始事件到判断

1. 添加“开始事件”，选择点击、进入世界、击杀、物品拾取等实际事件。
2. 添加“判断”节点，选择可视化或 JavaScript 模式。
3. 将事件的“继续”端口连到判断输入。
4. 将判断的“真”端口连到满足条件的动作；“假”端口可连接其他动作或不连。
5. 保存事件图，再保存所属任务/控件。未连接节点可作为编辑草稿保留，不会自行执行。

图必须是有向无环图。判断节点输出使用 `true` / `false`，普通事件/延迟输出使用 `next`。
命令、完成、奖励等动作是终点，不可把“动作 → 下一个动作”当作普通流程线。
需要多个动作时从适当的分支端口分开连线；避免依赖多个终点动作的先后顺序。

## 可视化模式

左值可选事件/玩家/世界/控件字段，也可选择允许访问的自定义变量。
右值可选常量、另一上下文字段或自定义变量。

| 运算 | 行为 |
| --- | --- |
| 等于、不等于 | 精确字符串比较；`10` 和 `10.0` 不相等 |
| 大于、大于等于、小于、小于等于 | 将两边解析为有限数值后比较；解析失败为假 |
| 包含、开头、结尾 | 字符串操作，区分大小写 |

组合逻辑：

| 选项 | 含义 |
| --- | --- |
| 单条件 `single` | 只判断第一组 |
| 取反 `not` | 第一组取反 |
| 并且 `and` | 两组都满足 |
| 或者 `or` | 至少一组满足 |

第二组通过“第一组/第二组”按钮切换编辑，不会因为切换而删除另一组条件。
自定义变量必须属于当前任务或全局作用域；存储值中的全局作用域标记为 `globe`。
禁止读取其他任务私有变量。

示例：玩家等级至少 10，且处于主世界。

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

## JavaScript 模式

选择“JavaScript”后打开脚本编辑器，源码保存在节点的 `data.script` 中。
这里填写源码，**不是 `.js` 文件路径，也不是函数名**。

```javascript
eventType === "click" && Number(experienceLevel) >= 10;
```

只有布尔 `true` 走“真”。布尔 `false`、数字、字符串或没有结果都走“假”。
没有 `player` / `event` 对象，所有图上下文字段都是直接使用的全局字符串。
需要数值时使用 `Number(...)`，布尔字符串要明确与 `"true"` 比较。

多行写法：

```javascript
(function () {
    const enoughLevel = Number(experienceLevel) >= 10;
    const correctWorld = dimension === "minecraft:overworld";
    return enoughLevel && correctWorld;
})();
```

不要在最外层写 `return true`，不要用 `1` 或 `"true"` 代替布尔值。
完整字段、文件条件差异、限制与错误示例见 [JavaScript 编写参考](javascript.md)。

## 完整连线示例

下面是有效的“点击 → JS 判断 → 完成/结束”图。真分支完成当前任务，
假分支结束此次流程；`event` 默认不是一次触发，下一次点击还可以重试。

```json
{
  "nodes": [
    {
      "id": "clicked",
      "type": "event",
      "x": 20,
      "y": 40,
      "data": {"event": "click", "cooldown": "20", "once": "false"}
    },
    {
      "id": "check",
      "type": "js",
      "x": 230,
      "y": 40,
      "data": {
        "mode": "script",
        "script": "eventType === \"click\" && Number(experienceLevel) >= 10;",
        "once": "false"
      }
    },
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

该图需附在实际任务控件上，任务本身还受解锁、触发器开关、完成后的事件策略和结构场景
资格控制。一个有效表达式并不等于所有任务层级的前置限制都已满足。

## 两种“一次执行”不能混淆

| 位置 | 实际行为 |
| --- | --- |
| 开始事件节点的一次执行 | 一次流程正常结束后记录已触发；即使判断走假，也可能不再接受该事件 |
| JS 判断节点的一次执行 | 第一次结果为真后记录成功；以后该判断不再求值，结果按假处理 |
| 奖励/KubeJS 等动作的一次执行 | 各动作维护自己的执行状态，不等于判断节点的一次执行 |

因此，“等级不足时失败，以后升级再点”通常应让开始事件保持重复触发；
不要误勾选开始节点的一次执行。JS 判断的一次执行默认关闭。
这些状态按玩家、任务、控件和节点区分保存。
管理员受信重置任务可以清理对应运行状态，见 `/xero_quest trigger reset`。

## 冷却与错误

开始事件的冷却以游戏刻计，范围为 1–72000，默认 20 刻；常规 20 TPS 下约为 1 秒。
错误还会令该事件绑定延后约 200 刻再次处理。低 TPS 时实际等待时间会更长。

正常返回假与报错不同：

- 正常假：执行 `false` 连线。
- JS 语法错误、未定义变量或预算错误：中断当前绑定，记录服务器日志，不自动执行假分支。
- 任务未解锁、触发器禁用、控件隐藏、场景资格不符等：可能在进入图前就被过滤。

## 对照排查

| 问题 | 优先检查 |
| --- | --- |
| 点了控件没有动作 | 事件是否选点击、连线是否保存、控件是否可见、任务是否允许触发 |
| 一直走假 | 返回值类型、字段是否字符串、比较值、维度资源 ID |
| 真/假都没有动 | 服务端错误日志、连线端口、开始事件冷却 |
| 只第一次有效 | 分别检查事件一次执行、JS 一次执行、动作一次执行 |
| 自定义变量读不到 | 变量 ID、作用域、是否使用可视化/变量判断而非不存在的 JS 全局对象 |
| 地点坐标总是 0 | 先检查 `locationFound`，空字符串数值转换会得到 0 |
| KubeJS 代码放 JS 判断后报错 | 两种环境不同；JS 判断使用内置沙箱，KubeJS 使用专门动作节点 |

## 实现依据

- [图定义与连线校验](../src/main/java/xtdpotato/xero_quest_journal/core/model/TriggerGraph.java)
- [事件执行、真假分支与一次执行](../src/main/java/xtdpotato/xero_quest_journal/core/runtime/QuestEventGraphs.java)
- [可视化比较和变量作用域](../src/main/java/xtdpotato/xero_quest_journal/core/runtime/QuestVisualPredicate.java)
