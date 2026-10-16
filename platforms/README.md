# 平台目录

每个加载器和 Minecraft 版本使用独立目录，最终产物也必须独立构建和安装。

```text
platforms/
  fabric/
    1.20.1/
    1.21.1/
    26.1/
    26.2/
    26.3/
  forge/
    1.20.1/
  neoforge/
    1.21.1/
    26.1/
    26.2/
    26.3/
```

共享任务模型、存档格式、资源和 UI 逻辑保留在 `src/main`。平台目录只放入口点、
事件桥接、网络注册、注册表和加载器元数据，避免多个 JAR 互相覆盖。

当前可构建目标（2026-10-16）：

- `neoforge/1.21.1`：完整 NeoForge 基线，由根工程构建。
- `forge/1.20.1`：Forge 47.3.0、Java 17，共享功能与旧 API 转换。
- `fabric/1.20.1`：Fabric、Java 17，复用旧 Minecraft API 转换。
- `fabric/1.21.1`：Fabric、Java 21，共享功能与 Fabric 事件/网络桥接。

26.1–26.3 目录仍是未完成的迁移工程，不发布安装包。
本次构建和实测状态见 [发布验证](../docs/release-verification-2026-10-16.md)。
