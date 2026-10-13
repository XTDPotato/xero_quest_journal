# Fabric 1.21.1

已接入共享任务、邮件、编辑器、网络、事件与客户端 UI，33 项功能专项通过。
最终重映射 JAR 另经过生产客户端与 SERVER 初始化检查。
服务端保持 `eula=false`，未验证远端联机。Java 21、Fabric Loader 0.16.9、API 0.108.0+1.21.1。

在仓库根目录执行：

```powershell
.\gradlew.bat -p platforms/fabric/1.21.1 build --no-daemon
```

运行 JAR 位于本目录 `build/libs`，可读 Mojang 名称源码 ZIP 位于 `build/distributions`。
下载缓慢时加 `-PuseDownloadMirror`。详情见 [验证报告](../../../docs/fabric-verification.md)。
