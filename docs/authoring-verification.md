# 作者工具与多版本发布验证

[项目首页](../README.md) · [版本矩阵](porting-matrix.md) · [全部命令](commands.md) · [JavaScript](javascript.md)

验证日期：2026-10-13。发布目标是多版本预览 4，显示版本仍为 `0.1.0`。
这是历史专项记录。2026-10-16 的后续功能和多版本预览 5 结果见
[本次发布验证](release-verification-2026-10-16.md)，不能把本页旧结果当作新功能全部复验。
这份报告记录本次全屏设置、脚本编辑器、命令参考和截图的专项复验；
上一轮完整迁移的 33 项回归详见 [Fabric 报告](fabric-verification.md) 和
[Forge 报告](forge-1.20.1-verification.md)。

## 改动范围

- 全屏顶部六标签设置；重复入口合并；个人设置与服务器作者设置分别校验权限。
- 标签内容切换 100ms，动画中阻止内容区点击，顶部和底部保留独立布局。
- 图内判断、条件文件和奖励文件共用 JS 编辑器，按用途提供参考、示例、字段类型。
- 保存前只编译检查，不执行源码；顶层 `return` 被拒绝，草稿调整窗口后保留。
- 修复 JS 模式属性标签遮挡；追加示例超过字符上限时拒绝操作，保留原稿。
- 三个命令根、64 条共享执行路径与最低权限；Forge 另有 `/xero_quest status`，共 65 条。
  15 个游戏内共用示例。
- 16 张中文原始 1920×1080 截图，覆盖设置全部标签、判断模式与编辑器状态。

## 专项断言

四个目标的七项相关回归最终全部通过，共 28 项：

| 目标 | 生产构建 | 相关客户端/集成服务器回归 | 最终 Fabric JAR 检查 |
| --- | --- | --- | --- |
| NeoForge 1.21.1 / Java 21 | 通过 | 7/7 | 不适用 |
| Forge 1.20.1 / Java 17 | 通过 | 7/7 | 不适用 |
| Fabric 1.20.1 / Java 17 | 通过 | 7/7 | 元数据、隔离、生产客户端、SERVER 初始化通过 |
| Fabric 1.21.1 / Java 21 | 通过 | 7/7 | 元数据、隔离、生产客户端、SERVER 初始化通过 |

七项为 `authoringDocsTest`、`smokeTest`、`controlGraphTest`、`animationTest`、
`popupAnimationTest`、`rewardsTest`、`runtimeTest`。全部核心 JAR 小于 5,000,000 字节，
发布包检查不包含 smoke 或 productioncheck 测试类。

`authoringDocsTest` 在实际集成服务器/客户端中验证：

| 范围 | 断言 |
| --- | --- |
| 脚本类型 | 文件的数值绑定、图的字符串覆盖、严格布尔结果 |
| 示例 | 内置示例真实引擎求值；KubeJS 示例保留为独立环境参考 |
| 图执行 | 真/假分支、非布尔结果走假、异常中断、一次成功后走假 |
| 语法检查 | 顶层 return 拒绝；无限循环只编译不运行 |
| 编辑器 | 草稿调整窗口/切换参考不丢失；追加与上限；图草稿保存 |
| 文件编辑 | 条件文件保存；从奖励列表打开对应参考并保存奖励文件 |
| 设置 | 非作者访问、六标签、100ms 时长、窄窗口滚动至最后一项、底部点击隔离 |
| 文档导出 | 实际 Brigadier 路径/权限、共用示例、中文与截图尺寸断言 |

此外复跑综合操作、事件图、动画、弹出层动画、奖励编辑和运行时回归。
综合测试里的旧悬停像素断言改为检查双像素选中边框的内侧，
外侧仍由悬停描边占用；没有因此修改实际选中逻辑。
NeoForge 首轮综合测试复用了旧布局目录；隔离目录复验后通过。
生产客户端检查还修正了 GUI 比例刷新和视口外控件的断言。

## 复现

1.20.1 使用 Java 17，1.21.1 使用 Java 21。以下工具示例使用 PowerShell 7；
文档校验器要求 PowerShell 7 的结构化 JSON API。每次只运行一个客户端，避免互相抢夺输入焦点。

```powershell
# 任一 Fabric 目标；替换为 1.20.1 可测旧版本
.\tools\test-fabric-functions.ps1 -Minecraft 1.21.1 -Modes authoringDocsTest,smokeTest,controlGraphTest,animationTest,popupAnimationTest,rewardsTest,runtimeTest

# Forge 1.20.1
.\tools\test-forge-functions.ps1 -Modes authoringDocsTest,smokeTest,controlGraphTest,animationTest,popupAnimationTest,rewardsTest,runtimeTest

# NeoForge 1.21.1，每次只指定一种模式
.\tools\test-neoforge-functions.ps1

# 可选 REI 收藏专项：把匹配游戏版本的 REI、Cloth Config、Architectury JAR 作为隔离 Mods 加入
.\tools\test-neoforge-functions.ps1 -Modes authoringDocsTest -ExtraMods @(<REI JAR>, <Cloth Config JAR>, <Architectury JAR>)

# 或单独调用某个模式
.\gradlew.bat runClient -PauthoringDocsTest
.\gradlew.bat runClient -PsmokeTest
.\gradlew.bat runClient -PcontrolGraphTest
.\gradlew.bat runClient -PanimationTest
.\gradlew.bat runClient -PpopupAnimationTest
.\gradlew.bat runClient -PrewardsTest
.\gradlew.bat runClient -PruntimeTest

# 通过的专项报告可再生成命令/示例文档与图片
.\tools\update-authoring-documentation.ps1 -ReportDirectory <专项通过的报告目录>
.\tools\verify-authoring-documentation.ps1

# 最终 Fabric 重映射 JAR：发布客户端和物理 SERVER 启动
.\tools\test-fabric-production-client.ps1 -Minecraft 1.21.1
.\tools\test-fabric-production-server.ps1 -Minecraft 1.21.1
```

Fabric/Forge 批量工具在 `build/verification` 保存每项独立结果与日志。
Release 的验证 ZIP 包含本轮结果、命令导出和中文截图；不会包含游戏存档或私人配置。

原始本地批次：

| 目标 | 报告目录（相对于 `build/verification/`） |
| --- | --- |
| Fabric 1.20.1 | `fabric-1.20.1-functions-20261013-214709`；修正断言后 `...-215640` 补跑作者工具与综合测试 |
| Fabric 1.21.1 | `fabric-1.21.1-functions-20261013-220029` |
| Forge 1.20.1 | `forge-functions-20261013-220905` |
| NeoForge 1.21.1 | `neoforge-functions-20261013-221657`；隔离目录 `...-222639` 补跑综合测试与作者工具 |

发布验证包只汇总各专项的最后结果，原始日志和初次失败截图仍保留在本地批次中。

## 覆盖边界

- Fabric 生产客户端检查使用最终重映射 JAR，检查六个设置标签、中文全高清与输入隔离；
  世界内完整流程由开发客户端专项覆盖。
- 专用服务器仅检查生产 JAR 初始化，停在 EULA 拒绝处。保持 `eula=false`，
  未验证专用服务器游戏过程或远端多人联机。
- KubeJS、战术物品与其他可选第三方联合运行不包含在本轮覆盖中。
- 26.1–26.3 仍未完成全量迁移，没有可安装 Release JAR。
- 不承诺所有整合包组合、所有 Minecraft 设置比例和任意性能负载都已验收。
