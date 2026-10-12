# 版本与加载器矩阵

本仓库保持一个模组 ID（`xero_quest_journal`）和一个资源命名空间。构建时通过
`-Pmc_target` 与 `-Ploader` 选择目标组合；每个组合最终仍然是一个独立 JAR，不能把
NeoForge 和 Fabric 的运行时代码塞进同一个安装包。

| Minecraft | NeoForge | Fabric | 备注 |
| --- | --- | --- | --- |
| 1.20.1 | 不提供官方 NeoForge 目标 | Fabric 未迁移；**Forge 共享功能迁移已完成构建与客户端启动验证** | Forge 47.3.0。服务端命令测试仍待接受 EULA，payload 联机往返和逐屏交互回归未完成。 |
| 1.21.1 | **完整基线构建通过** | **独立骨架构建通过** | NeoForge 是完整功能基线；Fabric 目前验证 Loom、入口和平台服务，尚未接入完整任务事件/网络/UI。 |
| 26.1 | 占位目录 | 占位目录 | 尚未接入构建工程；目标使用 Java 25 和新版本映射。 |
| 26.2 | 占位目录 | 占位目录 | 尚未接入构建工程；需独立配置该版本 API 和插件。 |
| 26.3 | 占位目录 | 占位目录 | 尚未接入构建工程；需独立配置该版本 API 和插件。 |

## 当前可用命令

默认构建保持当前基线：

```powershell
$env:JAVA_HOME = "C:\Users\26343\.jdks\openjdk-21.0.6\jdk-21.0.2"
.\gradlew.bat build
```

查看矩阵：

```powershell
.\gradlew.bat printPortMatrix
```

构建当前选择的配置：

```powershell
.\gradlew.bat buildSelected
.\gradlew.bat buildSelected '-Pmc_target=1.21.1' '-Ploader=fabric'
.\gradlew.bat buildSelected '-Pmc_target=1.20.1' '-Ploader=forge'
```

Forge 1.20.1 使用 Java 17；NeoForge 1.21.1 和 Fabric 1.21.1 使用 Java 21。
完整矩阵验证可使用：

```powershell
.\tools\verify-version-matrix.ps1 `
  -Java21 "C:\Users\26343\.jdks\openjdk-21.0.6\jdk-21.0.2" `
  -Java17 "C:\Program Files\Java\jdk-17.0.16+8" `
  -IncludeIncomplete
```

尝试尚未完成的组合会立即失败并指出原因，例如：

```powershell
.\gradlew.bat build -Pmc_target=1.21.1 -Ploader=fabric
```

## 架构约定

- `src/main/java`：共享任务模型、运行时、网络载荷和 UI 逻辑。
- `platforms/neoforge/1.21.1`：当前 NeoForge 平台实现。
- `platforms/fabric/1.21.1`：可构建的 Fabric 入口骨架，尚未补齐事件、网络注册和客户端 UI。
- `platforms/forge/1.20.1`：共享服务端和客户端源码已完成 Forge API 转换及构建；客户端启动通过，
  还需 EULA 许可后的服务器命令测试、网络往返和交互回归。
- `platforms/{neoforge,fabric}/26.x`：26.1、26.2、26.3 分版本适配目录。
- `gradle/versions/*.properties`：版本线的 Java/加载器依赖声明，不代表该组合已经通过编译。

当前共享业务树仍以 NeoForge 1.21.1 为 API 基线，Forge 1.20.1 通过生成源码转换适配；
Fabric 1.21.1 子工程目前只打包平台服务和入口骨架，仍需迁移网络、事件和渲染钩子，
才能达到完整功能 parity。

1.20.1 的 NeoForge 组合不会被伪造为可用目标；要做 1.20.1，应增加 Forge 适配器并在产物
元数据中标注 Forge，而不是写成 NeoForge。
