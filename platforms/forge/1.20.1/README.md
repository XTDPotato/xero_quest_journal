# Forge 1.20.1

1.20.1 的 Forge 独立平台工程。默认构建包含共享任务运行时、网络协议、事件、命令、
自动任务生成器和客户端 UI，并内置 jsoup/Rhino 运行库。

在仓库根目录执行：

```powershell
.\gradlew.bat -p platforms/forge/1.20.1 build --no-daemon
```

客户端与集成服务器已通过 33 项共享功能回归。TaCZ 联合测试跳过；
专用服务器世界与远端联机未验证，`run-server/eula.txt` 保持 `false`。
仅构建平台启动骨架可使用 `-PbootstrapOnly`。

迁移差异和验证记录见
[`docs/forge-1.20.1-port-status.md`](../../../docs/forge-1.20.1-port-status.md)。
