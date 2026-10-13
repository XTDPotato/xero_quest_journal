# Fabric 1.20.1

共享任务、邮件、编辑器、网络、持久化和 UI 已接入，33 项功能专项通过。
使用 Java 17、Fabric Loader 0.16.10、Fabric API 0.92.12+1.20.1。
复用 Forge 1.20.1 的 Minecraft API 源码转换，运行 JAR 不依赖 Forge。

```powershell
.\gradlew.bat -p platforms/fabric/1.20.1 build -PuseDownloadMirror
```

旧协议、容器输入和维度签名差异在本目录；其余 Fabric 适配器来自 1.21.1。
最终 JAR 位于 `build/libs`。验证边界见 [报告](../../../docs/fabric-verification.md)。
