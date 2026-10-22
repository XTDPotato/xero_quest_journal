# 0.2.4 发布验证 / Release Verification

验证日期：2026-10-23。此版本基于已发布的 0.2.3，使用新版本号和新 tag，不覆盖现有发布资产。
可安装目标为 Forge 1.20.1、Fabric 1.20.1、NeoForge 1.21.1 和 Fabric 1.21.1。
26.1–26.3 仍为部分迁移，不属于本次安装包范围。

## 功能与客户端回归

- `runClient -PtaskWorkflowTest`：Java 21、NeoForge 1.21.1 真实客户端与集成服务端通过。
- 此专项覆盖任务工作流、编辑模式切换、分页/筛选交互、任务奖励领取后邻近任务选择、
  解锁图双击导航、任务属性预览、新任务提示清理。
- 奖励领取回执专项在本次奖励属性实现中校验部分领取、库存满确认、溢出处理与回执幂等。
- 截图为原生 1920×1080 捕获，见 [`docs/images`](images/README.md) 和双语图鉴。

## 四平台构建

| Minecraft | 加载器 | Java | 构建入口 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.233 | 21 | `.\gradlew.bat build` |
| 1.21.1 | Fabric | 21 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"` |
| 1.20.1 | Forge 47.3.0 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"` |
| 1.20.1 | Fabric | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"` |

1.20.1 两个构建应顺序执行。完成构建后运行：

```powershell
.\tools\package-supported-release.ps1 -VersionRelease
```

发布包生成至 `build/releases/0.2.4/`，包含四个独立 JAR、按加载器/版本归类的合集 ZIP、
双语文档和 `SHA256SUMS.txt`。打包器拒绝错误版本/加载器元数据、缺失功能资源、
泄漏测试类或超过 5 MB 的核心 JAR。

## 校验和

生产 JAR 与合集 ZIP 的 SHA-256：

```text
30d7e95d2b657de244b99f4bf0acc0abbc23248df9876b87391aab7cb782b888  xero_quest_journal-neoforge-1.21.1-0.2.4.jar
af3c2ccca7bd38f3492c8ef755f14c2e0737773522206a9b5369e3381a081c96  xero_quest_journal-forge-1.20.1-0.2.4.jar
3157d75583a87a4b2e7a8ab4909a8c99521b4f553291bf712c45c4b4e48a0387  xero_quest_journal-fabric-1.20.1-0.2.4.jar
774da02113efe8530994edca0868bc130537f5e124c41dd5a911e5c14d64ee51  xero_quest_journal-fabric-1.21.1-0.2.4.jar
```

合集 ZIP 哈希与四个 JAR 的副本哈希列在 GitHub Release 附加的 `SHA256SUMS.txt` 中。

## 范围说明

`compileJava`、`compileSmokeJava` 与任务客户端回归覆盖本次主构建和关键流程；
发布过程会分别编译四个可安装目标。历史功能专项的通过结论沿用各自的验证日期，
不表示本次重新运行了所有历史测试。26.x 不生成或上传伪装成可安装版本的 JAR。
