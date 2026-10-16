# 多版本预览 5 发布验证

[项目首页](../README.md) · [版本矩阵](porting-matrix.md) · [中文界面图鉴](interface-gallery.md) · [更新说明](releases/preview.5.md)

验证日期：2026-10-16，Windows 11。本次使用当前共享源码重新构建四个生产目标，
并重点验证作者工具、章节可视化和运行时。模组显示版本仍为 `0.1.0`；
不同预览包不能只凭同名文件判断兼容，客户端/服务器应保持同一构建。

## 目标与结果

| Minecraft / 加载器 | Java | 本次生产构建 | 作者工具 | 章节可视化 | 奖励/条件运行时 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 Forge 47.3.0 | 17 | 通过 | 通过 | 通过 | 通过 |
| 1.20.1 Fabric | 17 | 通过 | 通过 | 通过 | 通过 |
| 1.21.1 NeoForge 21.1.233 | 21 | 通过 | 通过 | 通过 | 通过 |
| 1.21.1 Fabric | 21 | 通过 | 通过 | 通过 | 通过 |
| 26.1–26.3 NeoForge / Fabric | 25 | 完整迁移未完成 | 不适用 | 不适用 | 不适用 |

四个目标本次三项重点回归共 **12 项通过**。历史 33 项完整回归见
[Fabric 迁移报告](fabric-verification.md) 和 [Forge 迁移报告](forge-1.20.1-verification.md)，
历史记录不代替这次新增功能的验证。
各专项最终结果、实际断言摘要与本地批次编号见 [结构化结果](reference/release-preview.5-results.json)。

两个 Fabric 的最终重映射 JAR 还检查物理生产客户端与生产服务器初始化。
客户端覆盖六个设置标签和中文 1080p；服务器保持 EULA 未接受，
只验证加载器入口、Mixin 和嵌入库初始化。

## 本次专项内容

| 专项 | 实际检查 |
| --- | --- |
| `authoringDocsTest` | JS 示例实际求值、严格布尔结果、真假/异常/一次执行、语法检查、草稿保留、六标签设置、100ms 切换、完整命令导出 |
| 作者交互 | 双击脚本文字块、默认完整源码、折叠不改源码、拼音字段选择、长按拖变量、玩家归属、背包锚点和开关 |
| 界面图鉴 | 实际打开常见界面，等待进入动画结束，检查中文与原生 1920×1080，并拍摄全屏 |
| `journeyEditingTest` | 当前章节保存、其他章节隔离、多选拖动、宽高/比例句柄、背景/音乐、节点执行与预览回填、章节切换事件、任务详情弹窗 |
| `runtimeTest` | 物品/经验/等级/命令/函数/脚本/效果/解锁奖励，冻结奖励、重复保护、嵌套条件、提交原子性、计数、权限、NBT 持久化 |
| 生产包 | 正确加载器元数据、Java 字节码级别、Fabric Mixin/refmap、嵌入依赖、测试代码隔离、核心 5 MB 限制、SHA-256 |

另有本日 NeoForge 的 `journeyTest` 与 `hotEventsTest` 通过记录，分别覆盖历程浏览、
返回任务、资源选择、外观继承，以及热点画布缩放/拖动/连线等交互。

## 修复的跨版本问题

Forge 的 REI/Architectury 依赖原先使用排他仓库过滤，连 ForgeGradle 生成的
`_mapped_official_1.20.1` 构件也被限制到远端仓库。改为普通仓库内容过滤后，
仍按组限制第三方仓库，同时允许 ForgeGradle 自己解析重映射构件。

文档校验还发现中英文各有一个重复的 `graph.property.displayText` 语言键。
已删除被覆盖的旧项，保留游戏中实际生效的值；最终安装包重新构建。
检查截图时修正了物品/图标选择器底部预览与“空白”按钮的重叠，
预览改为独立一行，随后重新运行作者工具和截图专项并重建全部目标。

新增队伍变量后 `QuestControlData` 的读取函数变成多行，旧版精确文本转换没有匹配。
现在保留完整玩家和队伍读取代码，将 SavedData 工厂转换为 1.20.1 的函数/构造器形式。
回归源码也补齐旧版保存方法及队伍类型差异。

作者工具复验曾因按帧数等待不足而在进入动画中点击、或过早检查 100ms 标签切换，
最终重跑改为等待真实输入解锁，并对标签切换按真实经过时间检查完成。
测试不会通过关闭动画掩盖这项时序问题。

## 截图与文档检查

- [界面图鉴](interface-gallery.md) 按 16 类操作展示常见页面，每张图附入口或使用说明。
- 所有图鉴图片均为中文实际客户端原图，1920×1080，无拉伸或局部图冒充全屏。
- `verify-interface-gallery.ps1` 检查尺寸、导出 SHA-256 清单并生成缩略总览用于人工检查。
- `verify-authoring-documentation.ps1` 检查本地链接、代码围栏、命令路径/权限、共享 JS 示例、
  重复语言键及所有 `*-zh-1920.png` 图片尺寸。
- 文档包含完整命令、JS 用途差异、判断节点、变量归属、实时预览及保存/取消行为。
- 图鉴正文使用 50 张全高清图片，完整截图目录包含额外状态与历史可选兼容示例。

## 复现方式

1.20.1 先设置 Java 17，1.21.1 设置 Java 21。客户端专项串行运行，避免争抢游戏窗口。

```powershell
# 四个生产构建
.\gradlew.bat build
.\gradlew.bat -p platforms/forge/1.20.1 build
.\gradlew.bat -p platforms/fabric/1.20.1 build -PuseDownloadMirror
.\gradlew.bat -p platforms/fabric/1.21.1 build -PuseDownloadMirror

# 各平台的三项本次重点回归
.\tools\test-forge-functions.ps1 -Modes authoringDocsTest,journeyEditingTest,runtimeTest
.\tools\test-fabric-functions.ps1 -Minecraft 1.20.1 -Modes authoringDocsTest,journeyEditingTest,runtimeTest -UseDownloadMirror
.\tools\test-fabric-functions.ps1 -Minecraft 1.21.1 -Modes authoringDocsTest,journeyEditingTest,runtimeTest -UseDownloadMirror
.\tools\test-neoforge-functions.ps1 -Modes authoringDocsTest,journeyEditingTest,runtimeTest

# 文档和图鉴
.\tools\verify-authoring-documentation.ps1
.\tools\verify-interface-gallery.ps1

# 最终包仅从无 smoke 参数的生产构建收集
.\tools\package-supported-release.ps1 -Preview preview.5
```

本地日志位于 `build/verification/`。公开验证附件只收集本次结果、
校验清单和必要日志；游戏世界、个人配置和本机认证信息不上传。

## 已知边界

- 本次三项重点回归不等于重新执行所有历史专项；各平台结果单独记录。
- 专用服务器生产初始化检查保持 `eula=false`，停在 EULA 拒绝处；
  不能解释为远端多人游戏过程通过。
- 26.1–26.3 仍需 GUI、输入和运行时 API 的完整迁移，不发布占位安装包。
- 视频需要可选媒体后端；本次没有重新覆盖所有解码器和操作系统。
- JEI/REI/EMI 等第三方组合的历史专项与本次核心回归分开，不能推广为所有整合包兼容。
- Release 是预览版。旧版任务、世界进度和客户端偏好应分别备份。
