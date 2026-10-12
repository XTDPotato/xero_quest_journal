# Forge 1.20.1

1.20.1 的 Forge 独立平台工程。默认构建包含共享任务运行时、网络协议、事件、命令、
自动任务生成器和客户端 UI，并内置 jsoup/Rhino 运行库。

在仓库根目录执行：

```powershell
.\gradlew.bat -p platforms/forge/1.20.1 build --no-daemon
```

`runClient` 已验证 Forge 客户端能加载 mod 并进入游戏启动界面。专用服务器的完整启动和
命令测试需先由用户同意 `run-server/eula.txt` 中的 Minecraft EULA。仅构建平台启动骨架
可使用 `-PbootstrapOnly`。

迁移差异和验证记录见
[`docs/forge-1.20.1-port-status.md`](../../../docs/forge-1.20.1-port-status.md)。
