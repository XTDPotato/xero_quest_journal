# 项目截图索引

[项目首页](../../README.md)

这里保存从本项目开发客户端挑选的截图，使用 Minecraft 测试世界和演示任务。图片不是其他游戏的 UI 设计参考，不包含完整测试存档。

部分截图来自不同日期的开发版本，标题和数量使用测试数据。下面先列出 2026-10-13
作者工具专项生成的中文截图，**每张原始分辨率均为 1920×1080**，未将小图放大。
截图测试通过后由 `tools/update-authoring-documentation.ps1` 同步。

## 当前中文全高清截图

| 文件 | 场景 |
| --- | --- |
| [settings-general-zh-1920.png](settings-general-zh-1920.png) | 常规：视图、入口、HUD 与偏移 |
| [settings-motion-zh-1920.png](settings-motion-zh-1920.png) | 动画：减弱动态、弹出时长、编辑入口 |
| [settings-notifications-zh-1920.png](settings-notifications-zh-1920.png) | 通知：整体与图标大小、预览 |
| [settings-appearance-zh-1920.png](settings-appearance-zh-1920.png) | 外观：主题、背景与重置 |
| [settings-tasks-zh-1920.png](settings-tasks-zh-1920.png) | 任务：服务端分页与奖励来源 |
| [settings-tools-zh-1920.png](settings-tools-zh-1920.png) | 工具：按键、文件夹、折叠组重置 |
| [task-js-example-zh-1920.png](task-js-example-zh-1920.png) | JS 教学演示任务 |
| [js-predicate-graph-zh-1920.png](js-predicate-graph-zh-1920.png) | JavaScript 模式、真/假端口、独立代码入口 |
| [visual-predicate-graph-zh-1920.png](visual-predicate-graph-zh-1920.png) | 可视化判断：字段、比较符与值 |
| [js-editor-reference-zh-1920.png](js-editor-reference-zh-1920.png) | 代码、行号和字段参考 |
| [js-editor-examples-zh-1920.png](js-editor-examples-zh-1920.png) | 示例选择与追加 |
| [js-editor-syntax-error-zh-1920.png](js-editor-syntax-error-zh-1920.png) | 无效顶层 return 的诊断 |
| [js-condition-file-zh-1920.png](js-condition-file-zh-1920.png) | 文件条件及对应字段类型 |
| [js-reward-file-zh-1920.png](js-reward-file-zh-1920.png) | 奖励源码及动作 API 参考 |
| [mailbox-authoring-zh-1920.png](mailbox-authoring-zh-1920.png) | 当前邮箱界面 |
| [overview-authoring-zh-1920.png](overview-authoring-zh-1920.png) | 当前总览界面 |

## 历史功能截图

较早的图片可能展示旧版布局或卡片颜色；品质示例以 `reward-detail.png`、
`reward-receipt.png` 为准。下表用于展示其他功能，不作为本轮设置或脚本布局的依据。

| 文件 | 场景 | 原始测试截图 |
| --- | --- | --- |
| [task-details.png](task-details.png) | 打开新任务后的详情 | `new-task-read.png` |
| [overview.png](overview.png) | 推荐任务和分类入口 | `hot-events-overview-feature.png` |
| [journey.png](journey.png) | 章节旅行历程 | `journey-first.png` |
| [new-task.png](new-task.png) | 未打开任务的右上角红点 | `new-task-red-dot.png` |
| [clear-dots.png](clear-dots.png) | 领取全部奖励左侧的一键清红点按钮 | `task-mark-all-read-button.png` |
| [clear-dots-compact.png](clear-dots-compact.png) | 窄窗口按钮与分类栏布局 | `task-mark-all-read-compact.png` |
| [reward-detail.png](reward-detail.png) | 紫色经验瓶、红色钻石与自定义绿色背景 | `reward-quality-receipt-detail.png` |
| [reward-editor.png](reward-editor.png) | 品质与背景覆盖编辑器 | `reward-quality-editor.png` |
| [reward-receipt.png](reward-receipt.png) | 物品获得弹窗 | `reward-quality-receipt.png` |
| [reward-palettes.png](reward-palettes.png) | 六档品质和自定义颜色的像素检查场景 | `reward-six-quality-cards.png` |
| [event-graph.png](event-graph.png) | 控件事件图与条件/动作节点 | `control-graph-editor.png` |
| [unlock-graph.png](unlock-graph.png) | 任务前置关系编辑 | `unlock-graph-canvas.png` |
| [mailbox.png](mailbox.png) | 邮件和附件，较早开发布局 | `mailbox-reference-main.png` |
| [item-tags.png](item-tags.png) | 运行时标签选择 | `item-tag-picker.png` |
| [large-task-list.png](large-task-list.png) | 1,200 任务列表与编辑画布 | `journal-large-top.png` |
| [async-search.png](async-search.png) | 大目录异步检索后的筛选结果 | `journal-async-search.png` |

原始文件在开发环境的 `build/verification/` 中生成。仓库只保留与文档对应的图像；README 使用相对路径，克隆仓库后和 GitHub 页面上均可显示。

截图中的 Minecraft 世界、物品和第三方资源保留各自权利，不因项目源码采用 MIT 而改变许可。
