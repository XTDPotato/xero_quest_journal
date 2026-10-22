# 全部命令参考

**简体中文** | [English](en/commands.md) · [文档中心](README.md)

[项目首页](../README.md) · [使用指南](guide.md) · [JavaScript](javascript.md) · [判断节点](trigger-conditions.md)

本模组注册四个根命令：`/xero_quest`、`/xero_toast`、`/xero_structure`、`/xero_anecdote`。
聊天中包含 `/`，服务器控制台通常省略 `/`。
本文适用于已经完成的 1.20.1 Forge/Fabric 和 1.21.1 NeoForge/Fabric 目标；
不代表 26.x 已经可运行。

## 参数和权限

| 写法 | 含义 |
| --- | --- |
| `<参数>` | 必填，不把尖括号输入游戏 |
| `[参数]` | 可选，不把方括号输入游戏 |
| `<targets>` | 一个或多个在线玩家，可用玩家名、`@s`、`@a`、玩家选择器 |
| `<player>` / `<target>` | 恰好一个在线玩家 |
| `<id>` | 任务 ID 或对应功能的邮件 ID，不是显示名称 |
| `<reference>` | 结构相对路径；不同于任务 ID |
| 管理员 | 命令权限等级至少 2 |
| 玩家自身 | 命令源必须是玩家；控制台不能提供 `@s` 玩家实体 |

带空格的发件人与标题使用双引号。最后的正文/提示内容是贪婪字符串，接收余下全部文本，
通常不需要再包引号。资源 ID 使用 `minecraft:diamond` 这类命名空间形式。
成功返回值依具体分支而定；0 通常表示未成功或未发生更改，不应仅凭消息出现就认定奖励到账。

## 加载与查看

| 命令 | 权限 | 行为 |
| --- | --- | --- |
| `/xero_quest reload` | 管理员 | 重载全局配置与任务仓库；配置或任务解析错误会失败 |
| `/xero_quest list` | 管理员 | 报告当前已加载的任务数量 |

```text
/xero_quest reload
/xero_quest list
```

### Forge 1.20.1 平台状态

```text
/xero_quest status
```

仅 Forge 1.20.1 额外提供，最低权限 0，控制台也可执行。
返回 `Xero Quest Journal Forge 1.20.1 platform active`，用于确认平台入口已加载，
不代表完成了全部功能检查。NeoForge/Fabric 没有这个平台专用分支。

## 打开游戏界面

```text
/xero_quest open journal [targets]
/xero_quest open overview [targets]
/xero_quest open anecdotes [targets]
/xero_quest open mailbox [targets]
/xero_quest open settings [targets]
/xero_quest open templates [targets]
/xero_quest open task <id> [targets]
```

不填写目标时打开自己的界面，不要求管理员。填写目标时要求权限 2。
`task` 要求任务存在。打开作者界面不等于取得修改权限，服务器仍校验具体编辑请求。
个人设置不应因为没有开启作者模式而无法进入；服务器分页和作者主题操作仍有限制。

任务完成消息会在下一行显示金色 `[打开任务]`，点击执行
`/xero_quest open task <该任务ID>`，普通玩家可以打开自己的任务。
自动生成的获取/制作/击杀任务保留物品或实体名称参数，不应显示未替换的 `%s`。
拼图方块的原版取得、模板池准备和生成操作见
[拼图方块教程](candidate-selectors-and-jigsaw.md)；结构预览本身不执行生成。

```text
/xero_quest open task welcome
/xero_quest open anecdotes
/xero_quest open mailbox @a
/xero_quest open settings @s
```

## 作者模式

```text
/xero_quest edit_mode <targets> <enabled>
```

要求管理员，`enabled` 为 `true` 或 `false`。设置目标玩家的作者标记并同步客户端，
与临时打开一个界面不同。

```text
/xero_quest edit_mode @s true
/xero_quest edit_mode @a false
```

## 任务运行与受信重置

以下全部要求管理员：

```text
/xero_quest trigger complete <targets> <id>
/xero_quest trigger evaluate <targets> <id>
/xero_quest trigger advance_step <targets> <id>
/xero_quest trigger unlock <targets> <id>
/xero_quest trigger lock <targets> <id>
/xero_quest trigger reset <targets> <id>
```

| 动作 | 含义 |
| --- | --- |
| `complete` | 管理员触发任务完成，仍受相应运行时资格限制 |
| `evaluate` | 请求重新评估当前条件 |
| `advance_step` | 推进步骤任务的当前步骤 |
| `unlock` / `lock` | 解锁/锁定任务；这两个动作还支持分组 ID |
| `reset` | 受信重置对应玩家的任务运行状态，包括相关控件/图运行状态 |

重置不应被当作奖励撤回或无限重领工具。已经执行的外部命令、脚本、玩家物品变动
不因重置自动回滚。

```text
/xero_quest trigger evaluate @s welcome
/xero_quest trigger unlock @s chapter_one
/xero_quest trigger reset @s js_demo
```

## 自定义事件和阶段计数

```text
/xero_quest event event <targets> <name> <amount>
/xero_quest event stage <targets> <name> <amount>
```

要求管理员；`amount` 为 1–1000000。增加 `event` 或 `stage` 条件计数并评估目标玩家的任务。
这里记录条件计数，不等同于直接模拟事件图中的所有原版事件。

```text
/xero_quest event event @s dungeon:room_cleared 1
/xero_quest event stage @a story.chapter_1 1
```

## 奖励领取和回执

```text
/xero_quest reward claim_all
/xero_quest reward claim <id>
/xero_quest reward claim_drop <id>
/xero_quest reward receipts <target> <id>
/xero_quest reward acknowledge <target> <id> <index>
```

前三项针对玩家自身。`claim_drop` 允许相应领取流程使用掉落溢出选项。
任务未完成、已领取、场景临时背包限制或其他运行时条件可能使领取失败。
`receipts`、`acknowledge` 要求管理员。

- `receipts`：查看冻结奖励各条目的执行状态，索引从 0 开始。
- `acknowledge`：只接受索引 0–127 且状态为 `failed` / `running` 的现有条目；
  将其人工确认成 `done`，**不重新执行、不补发奖励**。
- 对命令/脚本等非幂等动作，先核实副作用，再确认回执。不要盲目重试。

```text
/xero_quest reward claim welcome
/xero_quest reward receipts @s welcome
```

## 每日委托

```text
/xero_quest daily claim
/xero_quest daily status <player>
/xero_quest daily reroll <targets>
```

`claim` 领取自己的每日全完成奖励；其余两项要求管理员。
`status` 会刷新/查询目标当天的委托列表、完成数和额外奖励状态。
`reroll` 重新抽取目标当天的委托。数量、重置小时与额外奖励在全局配置中管理，
没有未实现的 `daily set_count` 命令。

## 自动生成和删除

`/xero_quest start_generate_gui` 打开生成方式选择界面。查看界面无需管理员权限；
实际生成需要 OP 2 或单人世界所有者身份。首次进入世界时提供选择，选择或取消后按玩家保存。
可选模组进度链、结构探索、两者结合或传统目录模式，支持命名空间和关联配方开关。
详见[生成与模板库](generation-and-template-library.md)。原有 `generate` 命令保留传统目录行为。

`/xero_quest locate <id>` 为自己的可追踪结构任务请求坐标，权限等级 0；服务端检查任务资格。
坐标缓存共享给队伍，失败后可重试；不会给予管理权限或绕过锁定条件。

```text
/xero_quest generate [target] [rewards] [multiplier]
/xero_quest delete_generated
/xero_quest delete_generated <target>
/xero_quest delete_generated <target> confirm
```

全部要求管理员。生成参数必须按顺序提供：

| 参数 | 可选值/限制 | 默认 |
| --- | --- | --- |
| `target` | `all` 或已安装模组的命名空间 | 全部 |
| `rewards` | `tier`、`recipe`、`output`、`none` | `tier` |
| `multiplier` | 1–64 的整数 | 1 |

`tier` 按难度生成奖励，`recipe` 优先给予一轮有效配方的材料（相同材料合并数量），
找不到有效配方的物品任务给予目标物品；进度/击杀任务仍使用难度奖励。`output` 给予目标物品，
`none` 不生成奖励。生成在后台进行，命令返回开始并不表示所有文件已完成。
已有任务保留 ID、进度身份、手工文本和布局。重复执行可更新未被手工修改的生成奖励，
例如先生成 `tier`，再运行 `recipe 2` 会将仍受生成器管理的奖励切换为配方材料的两倍数量。
新任务记录生成奖励基线；手工修改过奖励的任务不覆盖。
旧文件缺少基线时，只迁移与历史默认难度奖励完全一致的条目；其他旧奖励保留。
已完成任务的奖励在完成时冻结，重新生成不会替换已冻结的奖励或再次发放。
`recipe` 分析不受物品同时可以掉落的影响，选中的有效配方稳定按 ID 排序。
任务前置只引用可达配方中严格更浅的材料任务；方块自身掉落不会抹掉已可达的合成链。
旧自动任务的自身前置会修复并保存；未修改的生成前置和旧默认空前置可补齐材料路径，
手工前置保留，更新前检查不会经手工关系形成循环。
删除必须带末尾 `confirm`；缺少确认的分支只提示，不执行删除。

```text
/xero_quest generate
/xero_quest generate minecraft recipe 2
/xero_quest generate minecraft output 4
/xero_quest generate all none
/xero_quest delete_generated minecraft confirm
```

## 全局配置

```text
/xero_quest config show
/xero_quest config set max_tracked <value>
/xero_quest config set manual_rewards <value>
/xero_quest config set default_item_count <value>
/xero_quest config set animation_ms <value>
/xero_quest config set task_pagination <value>
```

全部要求管理员，并保存服务端全局配置。

| 配置 | 值 |
| --- | --- |
| `max_tracked` | 1–3 |
| `manual_rewards` | `true` / `false` |
| `default_item_count` | 1–1000000 |
| `animation_ms` | 0–2000；0 关闭相应全局界面动画 |
| `task_pagination` | `true` / `false` |

`animation_ms` 与客户端弹出动画时长不是同一个字段。弹出层默认 100ms，个人设置中可调整；
普通页面切换仍受全局页面时长影响。全局关闭或减弱动态时不强行动画。

```text
/xero_quest config show
/xero_quest config set max_tracked 3
/xero_quest config set task_pagination true
```

## 邮件发送

以下全部要求管理员：

```text
/xero_quest mail list <target>
/xero_quest mail send <targets> <id> <sender> <subject> <expires_seconds> <body...>
/xero_quest mail send_item <targets> <id> <sender> <subject> <expires_seconds> <item> <count> <body...>
/xero_quest mail send_encoded <targets> <id> <sender> <subject> <encoded_body>
/xero_quest mail send_item_encoded <targets> <id> <sender> <subject> <item> <count> <encoded_body>
/xero_quest mail send_saved <targets> <template>
/xero_quest mail send_bundle <targets> <bundle>
```

- `expires_seconds`：0–31536000，0 表示不设置实时时间到期。
- `item`：实际存在的物品资源 ID；`count` 为 1–1000000。
- `sender`、`subject`：需要时使用双引号。
- 普通正文：命令余下全文。编码正文：UTF-8 字节的 URL-safe Base64，解码上限 8192 字节。
- 编码发送分支不带到期秒数参数，使用 0。
- `send_saved` 使用服务端保存的邮件模板 ID，找不到模板会失败。
- `send_bundle` 使用 URL-safe Base64 编码的 `MailDraft` JSON，编码参数最多 32000 字符；
  编辑器导出的 bundle 命令最适合复杂附件，不要把 JSON 直接粘进该参数。
- 模板/bundle 发送使用已有 ID 检查；不要依赖重复发送同一 ID 来重发附件。

```text
/xero_quest mail send @s welcome_mail "系统" "欢迎来到世界" 0 请打开任务日志开始冒险。
/xero_quest mail send_item @s supplies "补给员" "初始补给" 0 minecraft:bread 8 这是你的初始食物。
/xero_quest mail send_saved @a weekly_supply
```

## 邮件操作

以下全部要求管理员：

```text
/xero_quest mail read <targets> <id>
/xero_quest mail star <targets> <id>
/xero_quest mail delete <targets> <id>
/xero_quest mail claim <targets> <id>
/xero_quest mail claim_all <targets>
/xero_quest mail delete_read <targets>
```

`star` 是切换收藏状态，不是固定设为收藏。
删除、批量删除和领取仍受邮件附件/收藏保护/到期及相关运行时规则约束。
界面中的个人领取操作不要求用这些管理员命令替代。

## 系统提示

```text
/xero_toast <targets> <content...>
```

要求管理员。提示文本不能为空，最多 2048 字符，进入本模组的提示队列。

```text
/xero_toast @a 新章节已经开放，请查看任务日志。
```

## 结构场景

```text
/xero_structure leave
/xero_structure reset
/xero_structure edit <reference...>
/xero_structure import <template...>
/xero_structure draft
/xero_structure save
```

`leave`、`reset` 针对玩家自身；其余要求管理员且需要玩家实体。
`reference` / `template` 是余下全文参数。

| 命令 | 行为 |
| --- | --- |
| `leave` | 正常离开当前场景，恢复正式玩家状态 |
| `reset` | 重置场景实例；不等于清空正式任务完成记录 |
| `edit` | 进入某个结构引用的编辑实例 |
| `import` | 导入指定原版/数据包结构模板，并返回可用引用 |
| `draft` | 保存当前编辑为草稿副本 |
| `save` | 保存当前编辑为版本副本，不直接覆盖原模板 |

场景不是一个任意传送的普通维度。结构资格、临时背包、返回路径和完成记录有独立保护，
不要把直接 `/tp` 跨维度当作正常进入/离开方式。

## 可执行命令树

下面列出 73 条共享叶子路径：原有运行时导出加上本次生成选择和定位入口，
包含可选参数对应的各个实际分支。Forge 1.20.1 另有专用 `/xero_quest status`。
这份树与上面的用途、权限和例子配合使用。

<!-- COMMAND_ROUTES_START -->
## 实际命令叶子路径

| 命令 | 最低权限等级 |
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
| `/xero_quest start_generate_gui` | 0 |
| `/xero_quest locate <id>` | 0 |
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

## 逸闻记录弹窗

`/xero_quest open anecdotes` 打开逸闻纪事双页书册。左侧卡片目录和右侧记录均支持
平滑滚动；左键必须在同一项目内释放才会选中，右键可在作者模式进入条目/记录编辑器。
条目定义独立存放在服务器 `config/xero_quest/anecdotes/<id>.json`，每条记录可以配置
完成贴纸图。任务图节点 `discover_anecdote` 可解锁条目，`complete_anecdote` 可登记
某条记录完成；完成记录不会从逸闻册或其详情列表消失。右上角奖励页按完成时间倒序
展示贴纸；未读贴纸依次缩小进入，动画播放完毕后才标记已阅。完成状态、时间戳和贴纸
未读状态写入世界任务进度存档。

```text
/xero_anecdote <targets>
/xero_anecdote @s
/xero_anecdote @a
/xero_anecdote @s 16 -8 0.7 top,left
/xero_anecdote @a 0 0 1.1 bottom,right
/xero_anecdote @s 0 0 0.7 center
/execute as 玩家名 run xero_anecdote @s
```

要求权限等级 2，可由玩家、命令方块、函数或服务器控制台执行。`@s` 要求命令源是玩家；
控制台使用玩家名或 `@a`。返回值为收到通知的在线玩家数量。
参数按 `x y scale position` 顺序提供；后续参数需要前面的参数占位。X/Y 是锚点之外的屏幕像素偏移，
缩放范围为 `0.1`–`2`。默认 `x=0`、`y=0`、`scale=0.7`、`position=top`。

位置可以用逗号组合：`top` / `bottom` 选择纵向锚点，`left` / `start` 与 `right` / `end`
选择横向锚点；`center` 是任一未指定轴的居中锚点。因此 `top,center` 表示顶部居中，
`left,center` 表示左侧垂直居中，`center` 则表示屏幕中心。没有指定横向锚点时默认居中，
没有指定纵向锚点时也默认居中。`top,bottom` 和 `left,right` 属于冲突组合，会被拒绝。
默认顶部锚点距屏幕上边 4px，横幅宽度按屏幕自适应。

显示透明书册徽章、金橙色半透明横幅和“逸闻已记录在册”。每次执行只播放一次，
文字会随整条通知缩放；整体默认缩放为 70%，标题不超过旧版尺寸，且会按横幅宽度自动适配。
完整一轮约 3.2 秒，包含淡入、轻微缩放、光点扫过与淡出；不打开界面，不拦截操作，
不叠加整屏黑色背景。连续执行按接收顺序排队，上一轮完全淡出后才开始下一轮，
相同命令不会被合并。退出服务器清空待播放通知，重新连接不会重播。
开启“减少动态效果”时关闭位移、缩放和光点，保留淡入淡出。
中文及英文显示跟随客户端语言，可通过资源包替换
`screen.xero_quest_journal.anecdote.recorded` 和
`assets/xero_quest_journal/textures/gui/anecdote_emblem.png`。

## 实现依据

- [主命令注册](../src/main/java/xtdpotato/xero_quest_journal/core/QuestCommand.java)
- [Forge 平台额外状态命令](../platforms/forge/1.20.1/java/xtdpotato/xero_quest_journal/XeroQuestJournal.java)
- [共享运行时命令导出](reference/commands-runtime.json) / [Forge 运行时命令导出](reference/commands-forge-runtime.json)
- [结构命令注册](../src/main/java/xtdpotato/xero_quest_journal/core/runtime/QuestStructureEvents.java)
- [奖励执行和回执](../src/main/java/xtdpotato/xero_quest_journal/core/runtime/QuestRewards.java)
