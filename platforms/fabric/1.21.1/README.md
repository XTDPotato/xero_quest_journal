# Fabric 1.21.1

已放入 Fabric 平台服务和 `fabric.mod.json` 入口。当前独立 Loom 工程可以编译平台服务与入口骨架；
完整事件、网络和客户端生命周期适配仍在迁移中。

在仓库根目录执行：

```powershell
.\gradlew.bat -p platforms/fabric/1.21.1 build --no-daemon
```
