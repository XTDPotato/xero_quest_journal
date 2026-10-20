# 项目截图索引

[中文图鉴](../interface-gallery.md) | [English gallery](../en/interface-gallery.md) · [双语文档中心](../README.md)

## 0.2.1 双语截图

本次常见界面分别在 `zh_cn` 和 `en_us` 客户端以原生 **1920×1080** 截取，
使用等价的本地化演示内容。文件 `*-zh-1920.png` 为中文，
`*-en-1920.png` 为英文；用户内容、代码和资源 ID 不由界面语言自动翻译。
新截图包括任务、菜单、搜索、奖励、邮件、章节、逸闻、设置、脚本、变量、选择器和结构。

[逐图来源清单](../reference/interface-captures.json) 记录语言、场景、
实际 Screen 类、分辨率、时间与 SHA256。通过
`tools/sync-bilingual-documentation.ps1` 核验清单后同步，不调整图像尺寸或替换图片文字。
旧图仍用于日期明确的历史报告；当前常见界面优先使用本次更新。

```powershell
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=zh_cn
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=en_us
pwsh -File tools/sync-bilingual-documentation.ps1
pwsh -File tools/verify-authoring-documentation.ps1
pwsh -File tools/verify-interface-gallery.ps1
pwsh -File tools/verify-interface-gallery.ps1 -Document docs/en/interface-gallery.md
```

| 场景 / Scene | 中文 | English |
| --- | --- | --- |
| 任务日志 / Journal | [查看](journal-current-zh-1920.png) | [View](journal-current-en-1920.png) |
| 旅行历程 / Journey | [查看](journey-current-zh-1920.png) | [View](journey-current-en-1920.png) |
| 章节外观 / Chapter appearance | [查看](journey-appearance-zh-1920.png) | [View](journey-appearance-en-1920.png) |
| 逸闻 / Anecdotes | [查看](anecdotes-current-zh-1920.png) | [View](anecdotes-current-en-1920.png) |
| 逸闻贴纸 / Stickers | [查看](anecdotes-rewards-zh-1920.png) | [View](anecdotes-rewards-en-1920.png) |
| 奖励详情 / Reward details | [查看](experience-reward-details-zh-1920.png) | [View](experience-reward-details-en-1920.png) |
| 自选奖励 / Choice loot | [查看](choice-loot-selection-zh-1920.png) | [View](choice-loot-selection-en-1920.png) |
| 邮件 / Mail | [查看](mailbox-current-zh-1920.png) | [View](mailbox-current-en-1920.png) |
| JS 参考 / Script reference | [查看](js-editor-reference-zh-1920.png) | [View](js-editor-reference-en-1920.png) |
| 拼图连接 / Jigsaw | [查看](jigsaw-inspector-zh-1920.png) | [View](jigsaw-inspector-en-1920.png) |
| 外观设置 / Appearance settings | [查看](settings-appearance-zh-1920.png) | [View](settings-appearance-en-1920.png) |
| 追踪拾取提示 / Tracked pickup notices | [查看](item-pickup-toast-zh-1920.png) | [View](item-pickup-toast-en-1920.png) |
| 拾取通知位置/缩放 / Pickup notice placement/scale | [查看](settings-notifications-zh-1920.png) | [View](settings-notifications-en-1920.png) |

[项目首页](../../README.md) · [完整界面图鉴与操作](../interface-gallery.md)

这里保存从本项目开发客户端挑选的截图，使用 Minecraft 测试世界和演示任务。图片不是其他游戏的 UI 设计参考，不包含完整测试存档。

部分截图来自不同日期的开发版本，标题和数量使用测试数据。下面先列出截至 2026-10-18
作者工具和界面图鉴专项生成的中文截图，**每张原始分辨率均为 1920×1080**，未将小图放大。
截图测试通过后由 `tools/update-authoring-documentation.ps1` 同步。

## 当前中文全高清截图

| 文件 | 场景 |
| --- | --- |
| [structure-template-zh-1920.png](structure-template-zh-1920.png) | 真实村庄起始模板的三维预览 |
| [jigsaw-inspector-zh-1920.png](jigsaw-inspector-zh-1920.png) | 拼图连接点模型高亮、方向与字段说明 |
| [jigsaw-guide-zh-1920.png](jigsaw-guide-zh-1920.png) | 游戏内拼图方块六步操作讲解 |
| [settings-general-zh-1920.png](settings-general-zh-1920.png) | 常规：视图、入口、HUD 与偏移 |
| [settings-motion-zh-1920.png](settings-motion-zh-1920.png) | 动画：减弱动态、弹出时长、编辑入口 |
| [settings-notifications-zh-1920.png](settings-notifications-zh-1920.png) | 通知：整体与图标大小、预览 |
| [settings-appearance-zh-1920.png](settings-appearance-zh-1920.png) | 外观：主题、背景与重置 |
| [settings-tasks-zh-1920.png](settings-tasks-zh-1920.png) | 任务：服务端分页与奖励来源 |
| [settings-tools-zh-1920.png](settings-tools-zh-1920.png) | 工具：按键、文件夹、折叠组重置 |
| [graph-full-script-zh-1920.png](graph-full-script-zh-1920.png) | 默认展开多行脚本正文与全部显示勾选项 |
| [graph-inline-pinyin-zh-1920.png](graph-inline-pinyin-zh-1920.png) | 节点字段菜单使用中文拼音搜索 |
| [graph-variable-owner-zh-1920.png](graph-variable-owner-zh-1920.png) | 拖出经验等级字段并显示玩家/选择器归属 |
| [inventory-placement-zh-1920.png](inventory-placement-zh-1920.png) | 背包按钮锚点、偏移和屏幕预览 |
| [inventory-relative-anchor-zh-1920.png](inventory-relative-anchor-zh-1920.png) | 左上零偏移与独立显示验证 |
| [rei-inventory-shortcuts-zh-1920.png](rei-inventory-shortcuts-zh-1920.png) | REI 收藏中的 16×16 任务/邮件入口，无重复背包按钮 |
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
| [journey-chapter-multiselect-zh-1920.png](journey-chapter-multiselect-zh-1920.png) | 当前章节外观：左侧实时预览、多选拖动与右侧属性编辑 |
| [journey-chapter-marker-zh-1920.png](journey-chapter-marker-zh-1920.png) | 当前章节标记：白色中心菱形、淡蓝外框与透明光晕 |
| [journey-toolbar-edit-tooltip-zh-1920.png](journey-toolbar-edit-tooltip-zh-1920.png) | 进入时间线编辑，单一悬浮提示 |
| [journey-toolbar-exit-tooltip-zh-1920.png](journey-toolbar-exit-tooltip-zh-1920.png) | 铅笔加 X 退出编辑，单一悬浮提示 |
| [journey-toolbar-select-tooltip-zh-1920.png](journey-toolbar-select-tooltip-zh-1920.png) | 章节任务选择图标与提示 |
| [journey-selected-tab-5px-zh-1920.png](journey-selected-tab-5px-zh-1920.png) | 选中章节黄色底条恢复 5 GUI 像素；章节名称为测试文本 |
| [hot-background-inspector-zh-1920.png](hot-background-inspector-zh-1920.png) | 共用背景编辑器：实时预览、拖动、缩放模式与音乐 |
| [journal-current-zh-1920.png](journal-current-zh-1920.png) | 当前任务日志、目标与底部奖励 |
| [journal-main-tab-section-zh-1920.png](journal-main-tab-section-zh-1920.png) | 主线标签保留左侧分类图标和小标题 |
| [journal-world-tab-section-zh-1920.png](journal-world-tab-section-zh-1920.png) | 世界标签中的列表分类小标题 |
| [journal-completed-tab-section-zh-1920.png](journal-completed-tab-section-zh-1920.png) | 已完成标签中的列表小标题 |
| [task-context-menu-zh-1920.png](task-context-menu-zh-1920.png) | 任务右键菜单图标与分组 |
| [task-properties-zh-1920.png](task-properties-zh-1920.png) | 任务属性表单 |
| [search-scope-zh-1920.png](search-scope-zh-1920.png) | 搜索标题、描述、副标题和内部内容 |
| [search-filters-zh-1920.png](search-filters-zh-1920.png) | 分类、奖励、条件与目标筛选 |
| [conditions-editor-zh-1920.png](conditions-editor-zh-1920.png) | 完成条件树编辑 |
| [rewards-editor-zh-1920.png](rewards-editor-zh-1920.png) | 奖励类型、数量、品质与背景 |
| [reward-receipt-zh-1920.png](reward-receipt-zh-1920.png) | 奖励收据 |
| [loot-editor-zh-1920.png](loot-editor-zh-1920.png) | 战利品模式、权重与品质 |
| [loot-display-editor-zh-1920.png](loot-display-editor-zh-1920.png) | 战利品外显图标选择与编辑 |
| [loot-fields-compact-zh-1920.png](loot-fields-compact-zh-1920.png) | 紧凑布局的 ID 与显示名称标签 |
| [loot-display-compact-zh-1920.png](loot-display-compact-zh-1920.png) | 紧凑布局及平滑滚动末项 |
| [loot-display-mail-zh-1920.png](loot-display-mail-zh-1920.png) | 邮件战利品自定义图标及默认礼盒 |
| [loot-display-contents-zh-1920.png](loot-display-contents-zh-1920.png) | 战利品内容、条目图标与概率 |
| [mailbox-current-zh-1920.png](mailbox-current-zh-1920.png) | 当前邮箱正文和附件 |
| [mail-compose-zh-1920.png](mail-compose-zh-1920.png) | 编辑/发送邮件 |
| [mail-attachments-zh-1920.png](mail-attachments-zh-1920.png) | 邮件附件及品质编辑 |
| [rich-text-editor-zh-1920.png](rich-text-editor-zh-1920.png) | 富文本源码与预览 |
| [item-picker-zh-1920.png](item-picker-zh-1920.png) | 全屏物品选择与排序 |
| [texture-picker-zh-1920.png](texture-picker-zh-1920.png) | 全屏贴图选择 |
| [icon-fullscreen-preview-zh-1920.png](icon-fullscreen-preview-zh-1920.png) | 全屏缩放与平移预览 |
| [node-library-current-zh-1920.png](node-library-current-zh-1920.png) | 节点库分类 |
| [variable-catalog-zh-1920.png](variable-catalog-zh-1920.png) | 全局/任务变量目录 |
| [variable-editor-zh-1920.png](variable-editor-zh-1920.png) | 类型和共享范围 |
| [animation-editor-current-zh-1920.png](animation-editor-current-zh-1920.png) | 动画关键帧、时间线和预览 |
| [unlock-graph-current-zh-1920.png](unlock-graph-current-zh-1920.png) | 前置任务关系 |
| [task-templates-zh-1920.png](task-templates-zh-1920.png) | 模板选择和预览 |
| [location-editor-zh-1920.png](location-editor-zh-1920.png) | 维度、坐标与半径 |
| [legendary-current-zh-1920.png](legendary-current-zh-1920.png) | 传说人物任务 |
| [hot-events-current-zh-1920.png](hot-events-current-zh-1920.png) | 热点事件画布 |
| [value-picker-current-zh-1920.png](value-picker-current-zh-1920.png) | 可编辑候选列表 |
| [suggestion-recycle-bin-zh-1920.png](suggestion-recycle-bin-zh-1920.png) | 回收站多选、全选和反选 |
| [journey-chapter-identity-zh-1920.png](journey-chapter-identity-zh-1920.png) | 章节名称与图标 |
| [journey-task-details-popup-zh-1920.png](journey-task-details-popup-zh-1920.png) | 历程任务详情与显式导航 |
| [hot-background-node-zh-1920.png](hot-background-node-zh-1920.png) | 背景节点属性 |
| [legendary-background-inspector-zh-1920.png](legendary-background-inspector-zh-1920.png) | 传说人物背景预览 |

## 历史功能截图

较早的图片可能展示旧版布局或卡片颜色；当前样式以上面的全高清图片及界面图鉴为准。
下表用于展示其他功能，不作为本轮布局的依据。

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
