# Fabric 移植与验证

日期：2026-10-13。目标为 Fabric 1.20.1 / 1.21.1 的共享功能迁移。
生产包包含任务、邮件、编辑器、网络、持久化与 UI，不再是平台入口骨架。

## 实际结果

| 检查 | Fabric 1.20.1 | Fabric 1.21.1 |
| --- | --- | --- |
| 共享功能专项 | **33/33 PASS**（首轮 31，通过定位和补测完成剩余项） | **33/33 PASS**（首轮 31，邮箱用户名和空结构测试夹具修正后补测通过） |
| 网络拆分后的完整基础/邮件补测 | PASS | PASS |
| 原版维度和命令传送场景限制 | PASS | PASS |
| 解锁图帧同步修正后的补测 | PASS | PASS |
| 生产构建与重映射 JAR | PASS | PASS |
| 开发物理 SERVER 初始化 | PASS，保持 EULA 拒绝 | PASS，保持 EULA 拒绝 |
| 最终 JAR + 原始 Fabric API 的生产 SERVER 初始化 | PASS，保持 EULA 拒绝 | PASS，保持 EULA 拒绝 |
| 生产 CLIENT 与四个紧凑设置标签 | PASS，末行滚动可达、底部点击独立 | PASS，末行滚动可达、底部点击独立 |

服务端检查直接使用 intermediary 游戏与最终 JAR，`fabric.development=false`，
加载 JAR 中的 Mixin/refmap 和嵌入的 jsoup/Rhino，避免只在命名开发类路径上验证。
EULA 文件保持 `false`；专用服务器的世界、远端客户端握手与长期游戏过程未验证。

33 项包含基础任务流程、邮件、邮件展示与正文编辑、管理员发信、热事件、
重点引导、历程与历程编辑、总览与总览作者编辑、日志焦点与排序、
自动生成与生成项删除、HUD 追踪、战利品、画布、地点/物品、
基础媒体、事件图、解锁图、奖励弹出、领取回执、品质、创建、
动画、运行时、结构场景、按钮图片、奖励与模板。

## 补齐与修正

- Fabric 回调和原版 Mixin 接入拾取、合成、烧炼、跳跃、起床、进度、维度、交互与场景限制。
- 1.20.1 使用原始通道协议，解码后调度到对应线程；客户端接收器放入客户端专用类，
  修复专用服务器加载 `LocalPlayer` 的启动错误。
- 玩家专属标记经 Entity NBT 保存，任务和邮件使用世界级 SavedData。
- 1.20.1 补齐维度切换、命令传送拦截和维度图事件；测试直接调用原版方法，
  验证未进入场景的玩家/实体不能闯入，场景中的玩家不能直接跨维度离开。
- 旧容器释放事件不会自动转发自定义入口按钮，增加针对入口控件的释放转发。
- 恢复 1.20.1 分类图标 shader 连接；资源采用对应版本的函数和战利品目录。
- 邮箱夹具使用固定 `Dev` 用户名；画布夹具选择有方块的结构。
- 弹窗动画测试等待实际渲染帧，并显式设置弹窗时长，保留进出动画和阻塞检查。
- 新测试世界放入各目标 `build/smoke-runs`，截图/结果使用显式绝对输出目录。
- 生产截图发现设置页在高 GUI 缩放下正文与底部重叠：改用现有平滑滚动和输入/绘制裁剪，
  窄窗口开关按单列排列，四个标签均检查末行可达和底部按钮点击。
- Java 21 源码触发 Mercury/JDT 的源码重映射异常，因此可读源码提供 Mojang 名称 ZIP，
  不发布不完整的重映射 sources JAR；运行 JAR 仍正常重映射。

## 复现

先设置对应 Java（1.20.1 为 Java 17，1.21.1 为 Java 21）：

```powershell
.\tools\test-fabric-functions.ps1 -Minecraft 1.20.1 -UseDownloadMirror
.\tools\test-fabric-functions.ps1 -Minecraft 1.21.1 -UseDownloadMirror
.\tools\test-fabric-server-bootstrap.ps1 -Minecraft 1.20.1
.\tools\test-fabric-production-server.ps1 -Minecraft 1.20.1
.\tools\test-fabric-production-client.ps1 -Minecraft 1.20.1
```

后两项会构建生产 JAR。生产客户端辅助模组只进入
`build/production-check`，不打包进正式 JAR。
任一服务器检查发现已有 `eula=true` 会拒绝执行，不修改该文件。

本机证据位于根目录 `build/verification`：

- `fabric-1.21.1-functions-20261013-164201`：首轮 33 项。
- `fabric-1.21.1-functions-20261013-170322`：邮箱与画布修正补测。
- `fabric-1.21.1-functions-20261013-173349`：原版维度守卫场景补测。
- `fabric-1.21.1-functions-20261013-175646`：网络拆分后基础、邮箱、场景、解锁图均通过。
- `fabric-1.20.1-functions-20261013-172357`：首轮 33 项，31 PASS。
- `fabric-1.20.1-functions-20261013-175232`：旅程悬停独立补测通过。
- `fabric-1.20.1-functions-20261013-180341`：解锁图修正补测通过；两个路径故障已保留记录。
- `fabric-1.20.1-functions-20261013-180846`：整理目录后基础与邮箱重新通过。
- `fabric-1.20.1-production-server-20261013-181533`：最终 JAR 生产 SERVER 启动通过。
- `fabric-1.21.1-production-server-20261013-181533`：最终 JAR 生产 SERVER 启动通过。
- `fabric-1.20.1-production-client-20261013-185125`：四个紧凑设置标签渲染、滚动和点击通过。
- `fabric-1.21.1-production-client-20261013-185654`：最终设置布局生产 CLIENT 复测通过。
- `fabric-1.21.1-functions-20261013-185738`、`fabric-1.20.1-functions-20261013-185927`：
  设置页修复后，两个版本的完整基础流程再次通过。
- `fabric-1.21.1-production-server-20261013-190915`、
  `fabric-1.20.1-production-server-20261013-190921`：包含许可文件的最终发行包 SERVER 复验。
- `fabric-1.21.1-production-client-20261013-190927`、
  `fabric-1.20.1-production-client-20261013-190958`：最终发行包四标签 CLIENT 复验；
  SHA-256 与对应 SERVER 受测 JAR 一致。Java 字节码、依赖、Mixin/refmap、体积与测试代码隔离检查通过。

## 边界

- 1.20.1 没有 1.21 的原生菜单背景模糊选项，对应模糊像素比较跳过，不能计为支持。
- JEI/REI、KubeJS、TaCZ、MediaPlayer 和独立媒体后端未安装进行联合实测。
  基础图片/音频测试通过不能代表所有视频编码或外部媒体集成通过。
- Fabric 设置页面可打开，未宣称提供 NeoForge 的设置工厂或 Mod Menu 集成。
- 26.1 / 26.2 / 26.3 仅原生存档适配层通过测试，完整模组编译仍失败，不包含在发行资产中。
