# Fabric 26.1

Java 25 / Loom 工程已建立。原生 SavedData 磁盘专项通过；完整模组编译仍失败，
正在迁移渲染、输入、物品和权限 API。此目录不提供已完成的可安装 JAR。

从仓库根目录运行：

```powershell
.\platforms\shared\26\gradlew.bat -p platforms/fabric/26.1 verifyModernSavedData -PuseDownloadMirror
```
