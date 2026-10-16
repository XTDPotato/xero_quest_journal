# 多版本预览 5：实时可视化编辑与中文 1080p 图鉴

发布日期：2026-10-16。模组显示版本为 `0.1.0`，客户端与服务器请使用同一预览构建。
本次更新四个已完成的平台：

| Minecraft | 加载器 | Java | 附件 |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.3.0 | 17 | `xero_quest_journal-forge-1.20.1-0.1.0.jar` |
| 1.20.1 | Fabric | 17 | `xero_quest_journal-fabric-1.20.1-0.1.0.jar` |
| 1.21.1 | NeoForge 21.1.233 | 21 | `xero_quest_journal-neoforge-1.21.1-0.1.0.jar` |
| 1.21.1 | Fabric | 21 | `xero_quest_journal-fabric-1.21.1-0.1.0.jar` |

每个实例只安装一个匹配的核心 JAR；Fabric 另需对应 Minecraft 的 Fabric API。
可选媒体后端与 JEI/REI/EMI 均按游戏版本和加载器匹配。
26.1–26.3 完整迁移尚未完成，本 Release 不提供这些版本的可安装包。

## 本次功能

- 旅行历程外观独立保存到当前章节，左侧实时预览、右侧属性；支持多选、全选、反选、
  拖动、单独改宽高和等比例缩放，其他章节不受影响。
- 章节底部标记为白色中心菱形、淡蓝外框与透明光晕。点击任务先打开详情弹窗，
  明确选择“前往任务界面”才跳转；新增章节切换前/后节点事件。
- 热点事件、传说人物和章节背景共用实时编辑器，支持铺满/自适应/拉伸/原尺寸适配、
  缩放、X/Y 偏移、素材和背景音乐；热点背景节点可打开可视化预览并回填草稿。
- 节点字段可搜索（含拼音）、长按拖出带玩家/选择器归属的变量，长文本双击独立编辑；
  默认完整展示源码，可折叠显示。变量增加数值/字符串、队伍共享与科学计算。
- 增加搜索范围和奖励/完成条件筛选、候选回收站、全屏图标选择/排序/确认与缩放预览。
- 完善任务/邮件底部奖励、物品品质、战利品、发信和富文本编辑。
  默认弹出动画使用缩放与淡化，不包含旋转。
- 物品栏入口使用专属 16×16 任务/邮件图标，适配 JEI/REI/EMI，避免重复按钮。
- 修复 Forge 重映射依赖过滤与新增队伍状态的 1.20.1 存档 API 转换。

## 图文文档

中文截图均为实际客户端原生 **1920×1080**，涵盖常见浏览和编辑界面。
图鉴正文展示 50 张图片，完整目录提供 56 张中文全高清截图。

- [完整界面图鉴：16 类操作与截图](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/interface-gallery.md)
- [背景与当前章节可视化编辑](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/background-and-journey-editor.md)
- [全部命令和权限](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/commands.md)
- [JavaScript 字段、类型、API 与示例](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/javascript.md)
- [可视化判断、JS 判断与真假分支](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/trigger-conditions.md)
- [节点字段、变量归属与背包入口](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/visual-graph-and-inventory-shortcuts.md)
- [本次构建、回归与验证边界](https://github.com/XTDPotato/xero_quest_journal/blob/v0.1.0-multiversion-preview.5/docs/release-verification-2026-10-16.md)

![当前章节外观实时预览](https://raw.githubusercontent.com/XTDPotato/xero_quest_journal/v0.1.0-multiversion-preview.5/docs/images/journey-chapter-multiselect-zh-1920.png)

![奖励与品质编辑](https://raw.githubusercontent.com/XTDPotato/xero_quest_journal/v0.1.0-multiversion-preview.5/docs/images/rewards-editor-zh-1920.png)

![邮件编辑与发送](https://raw.githubusercontent.com/XTDPotato/xero_quest_journal/v0.1.0-multiversion-preview.5/docs/images/mail-compose-zh-1920.png)

## 下载与限制

附件包含四个生产 JAR、`supported-builds` 合集和 `SHA256SUMS.txt`。
合集按加载器/版本分类，并包含全部公开 Markdown 与截图。
验证资料单独提供；源码使用 Release 自动生成的 Source code。
测试代码、世界存档、个人配置和缓存不进入核心安装包。

本次验证基于 Windows 客户端和集成服务器。专用服务器初始化检查停在 EULA 拒绝处，
保持 `eula=false`，不表示远端多人游戏过程通过。
第三方组合和视频解码平台不在本次全面覆盖范围内。此版本仍为预览版。
