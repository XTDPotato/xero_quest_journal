# Forge 1.20.1 迁移状态

目标是让 Forge 1.20.1 与 NeoForge 1.21.1 共享任务定义、服务端运行时、网络协议和客户端 UI。
Forge 1.20.1 默认产物已包含转换后的完整共享服务端及客户端源码。

## 已完成

- ForgeGradle 6.x 工程、Java 17 工具链和 Forge 47.3.0 依赖。
- `mods.toml`、平台服务、Forge 入口和完整 `/xero_quest` 命令树。
- 根目录 `buildSelected -Pmc_target=1.20.1 -Ploader=forge` 路由。
- 默认构建包含共享服务端与客户端源码；`-PbootstrapOnly` 保留最小启动骨架构建。
- 1.20.1 缺失的网络类型已建立兼容层入口：
  `RegistryFriendlyByteBuf`、`StreamCodec`、`CustomPacketPayload`、
  `PayloadRegistrar`、`IPayloadContext` 和 `PacketDistributor`。
- 兼容类型位于 mod 自有 `forgecompat` 包，避免与 Forge/Minecraft 模块的包冲突。
- 服务端共享源码已包含运行时、网络、条件/图/结构事件、任务管理命令和自动任务生成器。
- Java 21 客户端 UI 已适配 1.20.1 的 widget、滚轮、checkbox、缓冲渲染、媒体线程、
  配方、成就、音频和资源定位 API。
- Forge 客户端配置屏幕扩展点和 shader 注册已接回；shader 加载失败时回退内置图标绘制。
- 中文/英文语言、GUI 纹理、方块模型、示例数据和 `pack.mcmeta` 已进入 JAR。
- jsoup 和 Rhino 同时进入开发运行模块路径，并将字节码打包进 Forge JAR。
- Forge dev client 已加载 mod、客户端/服务端事件订阅、场景方块注册、默认配置和示例任务，
  并到达 Minecraft 游戏启动界面，没有 mod 初始化致命错误。

## 剩余工作

1. **专用服务器验证**：Forge dev server 已运行到 EULA 检查，`run-server/eula.txt`
   保持 `eula=false`。用户同意 EULA 后还需测试世界启动、配置/任务加载和 `/xero_quest` 命令。
2. **联机网络验证**：payload 编解码已接入 Forge `SimpleChannel` 并通过完整编译，
   但尚未用客户端与集成/专用服务器完成请求/响应往返测试。
3. **交互回归**：客户端启动验证已到资源和菜单阶段，尚未逐屏操作任务、邮件、编辑器及场景流程。

## 迁移验证

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.16+8"
.\gradlew.bat -p platforms/forge/1.20.1 clean build --no-daemon
.\gradlew.bat buildSelected '-Pmc_target=1.20.1' '-Ploader=forge' --no-daemon
.\gradlew.bat -p platforms/forge/1.20.1 runClient --no-daemon
```

以上两个构建命令均已通过。`runClient` 已越过 Mod 构造、资源加载和默认数据初始化；
手动关闭客户端前未出现 mod 致命错误、缺失模型或 shader 回退警告。服务器命令行仍待
EULA 同意后验证。

```powershell
.\gradlew.bat -p platforms/forge/1.20.1 runServer --no-daemon
```

客户端启动日志位于仓库 `build/verification/forge-client-start.log`。
