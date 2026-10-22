# 版本与加载器矩阵

**简体中文** | [English](en/testing-and-compatibility.md) · [0.2.3 验证](release-verification-0.2.3.md)

当前发布版本为 **0.2.3**，四个已完成目标同步构建。
26.1–26.3 工程的声明版本号也已更新，但不代表完整迁移完成，本次不发布这些安装包。

仓库共享一个模组 ID（`xero_quest_journal`）、业务源码和资源目录。
各 Minecraft/加载器组合生成独立 JAR；不能把不同 Minecraft API 或加载器的代码放进一个通用安装包。

历史完整迁移验证日期：2026-10-13。本次共享功能更新与生产包复验日期：2026-10-18，
详见 [当日多版本发布验证](release-verification-2026-10-18.md)。

| Minecraft | NeoForge / Forge | Fabric | Java |
| --- | --- | --- | --- |
| 1.20.1 | **Forge 47.3.0：共享功能迁移，33 项回归通过**；此版本不提供 NeoForge | **完整共享功能接入，33 项回归通过** | 17 |
| 1.21.1 | **NeoForge 21.1.233：现有完整基线，生产构建复验通过** | **完整共享功能接入，33 项回归通过** | 21 |
| 26.1 | NeoForge 26.1.0.19-beta 工程已建立，NeoForm 依赖下载受阻；未完成迁移 | Java 25 / Loom 工程已建立，原生存档层测试通过；完整编译失败 | 25 |
| 26.2 | NeoForge 26.2.0.90 工程已建立，NeoForm/工具链依赖下载受阻；未完成迁移 | 原生存档层测试通过；完整编译失败 | 25 |
| 26.3 | NeoForge 26.3.0.76 工程已建立，插件/依赖解析受网络影响；未完成迁移 | 原生存档层测试通过；完整编译失败 | 25 |

Fabric 1.20.1：Loader 0.16.10、API 0.92.12+1.20.1。
Fabric 1.21.1：Loader 0.16.9、API 0.108.0+1.21.1。
两个版本都实际启动了最终重映射 JAR 的物理 SERVER 环境，验证公共入口、Mixin 和嵌入库；
服务器停在 EULA 拒绝处，保持 `eula=false`，不代表专用服务器游戏过程或远端联机已通过。
具体回归、补测与限制见 [Fabric 验证报告](fabric-verification.md)
及 [Forge 验证报告](forge-1.20.1-verification.md)。

2026-10-17 的四个生产目标同步共享源码，包括逸闻纪事、作者操作撤销、战利品外显图标、
邮件自选奖励礼盒显示及紧凑窗口滚动布局。上表中的“33 项”是历史完整回归，
本次重点客户端回归及最终附件校验单独记录，不混用验证日期。

2026-10-18 预览 17 同步候选来源选择、世界结构模板预览、
拼图连接点检查和操作讲解，以及任务完成聊天跳转。
本轮为候选/拼图专项客户端验证与四目标生产构建，不代表再次通过全部历史回归。

同日预览 18 修复战利品表顶部 ID/显示名称标签，补充小窗口截图与战利品专项，
四个已完成目标重新构建。

同日预览 19 修复具体分类及已完成标签中的左侧列表小标题，
补充分类切换、任务组折叠与行布局检查，四个已完成目标同步构建。

同日预览 20 修复旅行历程重复 tooltip，新增退出编辑与章节任务选择图标；
完整旅行历程编辑专项及四目标生产构建通过，发行包另校验两个新图标资源。

同日预览 21 恢复选中章节 5px 黄色底条，四目标构建及截图像素检查通过。
完整连续浏览回归仍有未通过项，详见当日验证记录。

## 构建入口

默认 `.\gradlew.bat build` 构建 NeoForge 1.21.1。其他已完成组合：

```powershell
# Java 17
.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=forge"
.\gradlew.bat buildSelected "-Pmc_target=1.20.1" "-Ploader=fabric"

# Java 21
.\gradlew.bat buildSelected "-Pmc_target=1.21.1" "-Ploader=fabric"
```

也可直接运行子工程：

```powershell
.\gradlew.bat -p platforms/fabric/1.20.1 build -PuseDownloadMirror
.\gradlew.bat -p platforms/fabric/1.21.1 build -PuseDownloadMirror
```

尚未完成的 26.x 组合不会通过根工程路由。其独立工程可用于继续开发，
使用集中在 `platforms/shared/26` 的 Gradle 9.2.1 Wrapper 和 Java 25：

```powershell
.\platforms\shared\26\gradlew.bat -p platforms/fabric/26.1 verifyModernSavedData -PuseDownloadMirror
.\platforms\shared\26\gradlew.bat -p platforms/fabric/26.2 verifyModernSavedData -PuseDownloadMirror
.\platforms\shared\26\gradlew.bat -p platforms/fabric/26.3 verifyModernSavedData -PuseDownloadMirror
```

`verifyModernSavedData` 仅测试独立适配层的实际磁盘存取，不需要客户端或接受 EULA，
**不代表整个模组通过编译**。26.x 全量编译仍需迁移新的 GUI 提取渲染、输入记录、
物品/配方、权限和实体存取 API；不能发布这些工程作为已完成版本。
NeoForge 官方仓库在本机存在 DNS/连接异常，BMCLAPI 未缓存部分 NeoForm 新版本，
不会用其他 Minecraft 的依赖伪装成对应目标。

## 目录约定

- `src/main`：共享任务、邮件、作者编辑、网络载荷、场景和 UI。
- `platforms/fabric/1.21.1/java`：Fabric 公共适配器与针对原版入口的 Mixin。
- `platforms/fabric/1.20.1/java`：旧协议、旧容器输入等差异；构建时复用 Forge 的旧 Minecraft API 转换。
- `platforms/fabric/shared-build.gradle`：Fabric 共享构建；源码副本输出到目标 `build/generated`。
- `platforms/fabric/smoke`：独立测试辅助代码，不进入发布 JAR。
- `platforms/shared/26`：26.x 共享转换、原生存档适配和唯一 Gradle 9 Wrapper。
- `build/verification`：集中保存测试日志、断言结果与截图；不提交游戏世界。
- `platforms/fabric/<版本>/build/smoke-runs`：新批次的隔离测试世界。

旧批次的 184 个自动测试目录已保留并归档到各目标的 `build/smoke-runs/archive`，
目标根目录不再铺满带时间戳的世界。它们属于忽略的本地验证数据，不会打包或上传。
源码生成结果不手工修改，应修正原始共享源码、适配器或构建转换。
