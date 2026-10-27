# 0.2.7 发布验证 / Release Verification

发布日期：2026-10-27。对应版本 `0.2.7`、Git tag `v0.2.7`。
四个平台使用独立 JAR；本报告只把实际运行的构建和回归记为通过。

## 客户端回归与 1080p 截图

| 回归 | 命令 | 状态 | 分辨率/范围 |
| --- | --- | --- | --- |
| 追踪 HUD、普通地形结构定位、坐标投影与动画 | `.\gradlew.bat runClient -PtrackingHudTest` | 通过 | 1920×1080，并含紧凑布局检查 |
| 自动生成故事、每日逸闻、维度门槛和奖励修复 | `.\gradlew.bat runClient -PgenerationTest` | 通过 | 1920×1080 |
| 奖励编辑器品质、列表/表格和内嵌战利品表详情 | `.\gradlew.bat runClient -PrewardQualityTest` | 未通过完整流程 | 编辑器/回执截图生成；后续批量已读步骤挂起，未产出 PASS |
| NeoForge 1.21.1 生产构建 | `.\gradlew.bat build` | 通过 | Java 21 |
| Fabric 1.21.1 生产构建 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"` | 通过 | Java 21 |
| Fabric 1.20.1 生产构建 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"` | 通过 | Java 17 |
| Forge 1.20.1 生产构建 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"` | 通过 | Java 17 |

Xaero/JourneyMap 插件集成随对应平台生产构建编译验证。发布回归运行环境没有安装这两个地图模组，
所以地图实际打开、航点图标渲染和不同地图版本的按键映射不在本报告的实测范围内。
奖励品质 smoke 三次均生成了 1920×1080 奖励编辑器/回执截图，但未完成最后的批量已读阶段，
没有产出 PASS 结果文件；该专项应视为未通过完整验证，不将截图当作通过证据。

本版本截图清单：

- [`generation-scope-0.2.7-zh-1920.png`](images/generation-scope-0.2.7-zh-1920.png)
- [`tracking-list-0.2.7-en-1920.png`](images/tracking-list-0.2.7-en-1920.png)

截图源自实际客户端，像素尺寸为 1920×1080。

## 四平台生产构建与安装包

| Minecraft | 加载器 | Java | 构建入口 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.233 | 21 | `.\gradlew.bat build` |
| 1.21.1 | Fabric | 21 | `.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"` |
| 1.20.1 | Forge 47.3.0 | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"` |
| 1.20.1 | Fabric | 17 | `.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"` |

构建完成后，运行：

```powershell
.\tools\package-supported-release.ps1 -VersionRelease
```

打包器检查版本/加载器元数据、关键类/贴图、测试夹具排除与核心 JAR 5 MB 上限，并生成分类合集与 `SHA256SUMS.txt`。
打包输出位于 `build/releases/`，含四平台独立安装包、分类合集 ZIP 与校验清单。

## 产物 SHA-256

```text
8917485a362161e57b1ff0d7945283b1453ae2e62696789aaf93a2d56f0bff5c  xero_quest_journal-neoforge-1.21.1-0.2.7.jar
f1df25cd3c89b3b8e6ac42171683f1fafe0ade3d10704ca9490ab1398256edb9  xero_quest_journal-forge-1.20.1-0.2.7.jar
bf2dc89e5fcad137665f52a1aca8baddc71c3c00f0f9af8ee58265a223faef02  xero_quest_journal-fabric-1.20.1-0.2.7.jar
ebc7999396e5e9393baf25adc7fecc11e481f0800f9628494467e3ced674eac7  xero_quest_journal-fabric-1.21.1-0.2.7.jar
```

合集 ZIP 的 SHA-256 记录在同目录 `SHA256SUMS.txt` 中。
