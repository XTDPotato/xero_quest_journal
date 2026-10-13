# Forge 1.20.1 功能回归

验证日期：2026-10-13。Minecraft 1.20.1、Forge 47.3.0、Java 17、Windows 11。
真实开发客户端与集成服务器使用隔离配置目录和新测试世界。

本轮执行了 34 个专项入口，修复后逐项复测。最新完成结果为 **33 项通过、1 项跳过**。
结果来自多次隔离运行，不是一次同时运行；普通 `build` 不能代替这些专项。

| 范围 | 通过的入口 |
| --- | --- |
| 基础日志、权限、分类、创建、复制与保存 | `smokeTest`、`creationTest` |
| 顺序、专注、追踪、规模与搜索 | `journalOrderTest`、`journalFocusTest`、`trackingHudTest`、`spotlightUiTest` |
| 总览与历程 | `overviewTest`、`overviewAuthoringTest`、`journeyTest`、`journeyEditingTest`、`hotEventsTest` |
| 邮箱、正文、管理、珍藏与通知 | `mailboxTest`、`mailBodyEditorTest`、`mailAdminTest`、`mailReferenceTest` |
| 奖励、弹窗、收据、品质、战利品 | `rewardsTest`、`rewardPopupTest`、`claimReceiptTest`、`rewardQualityTest`、`lootTableTest` |
| 运行时、物品数据、结构世界 | `runtimeTest`、`locationItemTest`、`structureSceneTest` |
| 控件、模板、布局、图片、动画 | `controlGraphTest`、`unlockGraphTest`、`canvasEditorTest`、`templateTest`、`buttonImageTest`、`animationTest`、`popupAnimationTest` |
| 生成与删除 | `generateTasksTest`、`generatedDeletionTest` |
| 媒体导入与基础音频 | `mediaTest` |
| TaCZ 实际联调 | `tacticalCompatibilityTest` 跳过：隔离客户端未安装 TaCZ |

额外通过：50,001 对象视口几何、跨视口连线与绘制顺序检查；50,000 文档异步搜索、
中文/拼音/首字母、多词匹配、取消与目录替换检查。

## 本轮修复

- Forge 的客户端、服务器、玩家、世界 tick 只处理 `END` 阶段，保持原 Post 事件语义。
- 滚轮三参数/四参数桥调用父类时避免递归；属性窗口滚轮不再栈溢出。
- 1.20.1 数据资源使用 `loot_tables`、`functions`，自动任务来源和函数奖励正确加载。
- 奖励命令解析失败向收据流程传播，缺失函数不会被假记为已发放。
- 批量粘贴到自动章节时保留自动成员语义；显式章节仍追加引用。
- 搜索框 Ctrl+V 按收到的修饰键处理；测试等待真实快照、动画和剪贴板可用状态。

旧测试中不符合当前交互的断言也已修正：分类右键排序、已完成分类、34×38 奖励卡、
按下/释放行为、自动章节、背景色和选择器菜单。没有用删除结果校验制造 PASS。

## 未覆盖

- 专用服务器及远端客户端连接、重连。`run-server/eula.txt` 保持 `false`。
- TaCZ、KubeJS、JEI/REI/EMI 与可选原生媒体后端的实际联调；基础套件只验证缺席时的降级或开关。
- Minecraft 1.20.1 不支持 1.21 菜单模糊设置，任务/邮箱/收据背景模糊像素比较跳过。
- 任意资源包、GPU、系统、分辨率、特殊库存实现以及整合包性能。
- 所有崩溃点的世界保存与奖励收据一致性。

## 复现

```powershell
.\tools\test-forge-functions.ps1
.\tools\summarize-forge-tests.ps1
```

每项报告、截图、游戏日志及 Gradle 日志位于
`build/verification/forge-functions-<timestamp>/<suite>/`。
汇总包含具体证据目录，生成于 `build/verification/forge-functions-latest/`。
原始测试配置、世界和缓存不提交至 GitHub；Release 附测试结果文本。
