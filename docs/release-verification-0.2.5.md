# 0.2.5 发布验证 / Release Verification

验证日期：2026-10-23。此次发布使用新版本号 `0.2.5` 和 tag `v0.2.5`，不覆盖 0.2.4 的 JAR 或资产。

## 客户端与截图回归

- `.\gradlew.bat compileJava compileSmokeJava`：通过。
- `.\gradlew.bat runClient -PgenerationTest`：NeoForge 1.21.1 实际客户端与集成服务器通过。
- 每日逸闻断言：每日生成 1–4 条结构逸闻；首条为可追踪抵达任务；下一游戏日为同一逸闻添加续篇；村庄续篇敲钟三次，其他续篇获取物品；检查任务前置、重复调用幂等和调度器 NBT 保存/恢复。
- 窗口保持 1920×1080；窄布局压力检查使用 GUI 缩放模拟，并不把渲染窗口降为低分辨率。
- 所有回归截图均由测试代码断言尺寸为 1920×1080。公开截图见 [0.2.5 发布说明](releases/0.2.5.md)。

## 四平台生产构建

| Minecraft | 加载器 | Java | 构建入口 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.233 | 21 | `.\gradlew.bat build` |
| 1.21.1 | Fabric | 21 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"` |
| 1.20.1 | Forge 47.3.0 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"` |
| 1.20.1 | Fabric | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"` |

1.20.1 Forge 与 Fabric 构建需顺序执行。所有构建完成后，运行：

```powershell
.\tools\package-supported-release.ps1 -VersionRelease
```

四个目标均已成功构建，根 NeoForge `build`、Fabric 1.21.1、Forge 1.20.1、
Fabric 1.20.1 的日志都以 `BUILD SUCCESSFUL` 结束。发布资产输出到
`build/releases/0.2.5-release/`，包含四个 JAR、分类合集 ZIP 和 `SHA256SUMS.txt`。
打包器会校验版本/加载器元数据、每日逸闻调度和核心功能类、界面资源、测试夹具排除及 5 MB 核心 JAR 上限。

## 产物哈希

SHA-256 与发布目录中的 `SHA256SUMS.txt` 一致：

```text
9ad8131fad090da6ee29b5ca4fb1d7f936525115b44b06e49789e7e39315578a  xero_quest_journal-neoforge-1.21.1-0.2.5.jar
a8aa66155bf9775449ccc6534721568278f5666c557e1ddedcd2f199593596d3  xero_quest_journal-forge-1.20.1-0.2.5.jar
d7eb572666f5789a2896cdcd9329c163f4e9112a2d71926c7b47179fa9fad7bf  xero_quest_journal-fabric-1.20.1-0.2.5.jar
463503dc48f9e0ed1daa10c19b05a1c5afca5d0165f0fdb4ab65cf4adf35dd5c  xero_quest_journal-fabric-1.21.1-0.2.5.jar
```

合集 ZIP 的哈希由 `SHA256SUMS.txt` 单独记录，避免在 ZIP 内嵌入自身哈希。

在线 GitHub Release 资产在发布后再次通过 API 核对名称、大小和摘要。

## 范围

本次重新运行了每日生成客户端回归和主构建编译；四个平台生产构建会由发布流程逐一执行。
历史专项结果保留在各自日期的验证文档中，不代表本次重跑。26.1–26.3 尚未完整迁移，不构建、不发布。
