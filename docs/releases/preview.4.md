# 多版本预览 4：全屏设置、JS 参考与中文文档

本次同时更新四个已完成目标：

| Minecraft | 加载器 | Java |
| --- | --- | --- |
| 1.20.1 | Forge 47.3.0 | 17 |
| 1.20.1 | Fabric | 17 |
| 1.21.1 | NeoForge 21.1.233 | 21 |
| 1.21.1 | Fabric | 21 |

每个实例只安装与游戏版本/加载器匹配的一个核心 JAR。Fabric 还需对应版本的 Fabric API。
客户端与服务器应使用同一预览构建；模组显示版本仍为 `0.1.0`。

## 本次更新

- 全屏设置采用顶部六标签：常规、动画、通知、外观、任务、工具；内容切换 100ms。
- 合并重复设置入口，普通玩家可修改个人偏好，服务器配置保留管理员校验。
- 游戏内 JS 编辑器加入用途参考、字段类型、15 个示例、行号、语法错误提示与草稿保留。
- 条件文件和奖励文件分别展示对应 API；奖励列表直接打开新的奖励脚本参考。
- 修复判断节点的 JS 标签被模式控件遮挡，追加示例超限时保留原稿。
- 补齐全部命令、判断节点语义、JS 返回值/类型/异常/一次执行规则。
- 新增 16 张中文原始 1920×1080 截图。

## 文档

- [项目首页与安装](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/README.md)
- [全部命令：64 条共享分支及 Forge 状态命令](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/docs/commands.md)
- [JavaScript 编写与游戏内编辑器参考](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/docs/javascript.md)
- [判断节点与真假分支](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/docs/trigger-conditions.md)
- [本轮验证报告](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/docs/authoring-verification.md)
- [中文截图索引](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.4/docs/images/README.md)

## 下载与验证

附件提供四个独立 JAR、按加载器/版本分目录的合集、验证资料 ZIP、Fabric 1.21.1
可读源码 ZIP 和 `SHA256SUMS.txt`。合集包含公开 Markdown 与截图；
源码查看请使用本页自动生成的 Source code 或仓库链接。

四个目标均完成生产构建和七项相关客户端/集成服务器回归，合计 28 项最终通过。
两个 Fabric 最终重映射 JAR 另通过六标签生产客户端和物理 SERVER 初始化检查。
所有核心 JAR 均小于 5 MB，未包含测试类。

## 当前边界

26.1–26.3 完整迁移仍未完成，没有发布可安装包。
专用服务器检查保持 `eula=false`，没有验证远端多人游戏过程。
KubeJS 等可选第三方模组联合运行未在本轮验收。此 Release 为预览版。
