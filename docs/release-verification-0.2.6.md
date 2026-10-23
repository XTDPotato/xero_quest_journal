# 0.2.6 发布验证 / Release Verification

验证日期：2026-10-23。本次发布使用新版本号 `0.2.6` 与 tag `v0.2.6`，不覆盖 0.2.5 及更早版本的 Release 资产。

## 客户端回归与截图

- `.\gradlew.bat compileJava compileSmokeJava --no-daemon`：通过。
- `.\gradlew.bat runClient -PrewardInspectionTest --no-daemon`：NeoForge 1.21.1 实际客户端与集成服务器通过。
- 回归覆盖奖励物品 tooltip、经验点数与经验等级的名称/数量/图标映射、详情查看与返回、邮件附件和战利品/自选奖励检查。
- 客户端窗口固定为 1920×1080；截图保存为 [`experience-reward-tooltip-zh-1920.png`](images/experience-reward-tooltip-zh-1920.png)，测试断言原始尺寸。
- `git diff --check`：通过。

`runClient -PgenerateTasksTest` 曾在进入 tooltip 检查前，于既有配方 `OUTPUT` 奖励可达性断言失败
（`Output mode did not reward the generated task item and guarantee experience`）。
该失败位于本次变更路径之外；本次不将该任务生成专项记为通过。

## 四平台生产构建

| Minecraft | 加载器 | Java | 构建入口 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.233 | 21 | `.\gradlew.bat build` |
| 1.21.1 | Fabric | 21 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"` |
| 1.20.1 | Forge 47.3.0 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"` |
| 1.20.1 | Fabric | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"` |

1.20.1 Forge 与 Fabric 构建需按顺序执行。全部构建成功后，运行：

```powershell
.\tools\package-supported-release.ps1 -VersionRelease
```

四个目标构建均已成功，发布目录位于 `build/releases/`，包含四个平台 JAR、分类合集和 `SHA256SUMS.txt`。
打包器会验证版本/加载器元数据、必需类与资源、测试夹具排除和核心 JAR 5 MB 上限。

## 产物 SHA-256

SHA-256 与发布目录的 `SHA256SUMS.txt` 一致：

```text
a0de74393a475b9124fd943dde8ad23c41c60e1ab06105a749c38ce8f0f61c85  xero_quest_journal-neoforge-1.21.1-0.2.6.jar
5beca6248ea86634338b2a504fff3152c2556274193efb764036bdfe07fdf15b  xero_quest_journal-forge-1.20.1-0.2.6.jar
e6716dc003fe76e51569069976f9f8f182258b41fdb6fcb2b49cb1c2d526d569  xero_quest_journal-fabric-1.20.1-0.2.6.jar
dd4d331face93e58174056b1b8a4a875896dc115edc08cfe7dcc51b6ba464374  xero_quest_journal-fabric-1.21.1-0.2.6.jar
```

合集 ZIP 与所有附件的摘要由发布目录 `SHA256SUMS.txt` 统一记录；上传后通过 GitHub Release API 核对在线名称、大小和摘要。

## 范围

本次重新运行经验奖励/tooltip 客户端回归与主构建；其他历史专项仍以各自验证文档的日期和范围为准。
26.1–26.3 尚未完成完整迁移，不构建、不发布。
