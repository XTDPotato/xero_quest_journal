# 0.2.1 发布与双语文档验证

**简体中文** | [English summary](en/testing-and-compatibility.md) · [项目首页](../README.md)

日期：2026-10-18。发布目标为 Forge/Fabric 1.20.1、NeoForge/Fabric 1.21.1。
26.1–26.3 版本号同步到 0.2.1，但完整移植尚未完成，本次不提供其安装包。

## 选中章节底条

默认由 5px 改为 **3 GUI 像素**。
“设置 → 外观 → 旅行历程选中标签底条高度”支持 1–5px，
可用加减按钮或输入数值；立即应用并保存于 `config/xero_quest/ui/client.json`。
旧偏好文件缺少字段时继承默认 3px。

文档截图任务实际检查默认值、旧配置默认值、设置加减/数值输入和落盘值，
并分别截取 1px、3px、5px 的画面，测量其真实像素高度。

## 中英文文档与截图

英文首页、双语文档中心、指南、命令、JS、判断节点、变量、奖励、
章节背景、逸闻、结构拼图、撤销及媒体说明均有对应入口。
英文命令表和 15 个 JS 示例从现有结构化参考同步，保留实际源码。

```powershell
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=zh_cn --offline
.\gradlew.bat runClient -PinterfaceDocsTest -PdocsLanguage=en_us --offline
pwsh -File tools/sync-bilingual-documentation.ps1
pwsh -File tools/verify-authoring-documentation.ps1
```

两种语言使用同一组场景，逐一等待资源加载和界面过渡完成。
每种语言 56 张，共 112 张本轮配对截图；两个完整截图任务均为 `BUILD SUCCESSFUL`。
文档校验额外保留历史专项图，当前全尺寸图片总数另由工具输出。
截图为原生 1920×1080，不通过放大、裁剪或替换图片中文字伪造语言版本。
清单记录场景、语言、尺寸、实际 Screen 类、时间和 SHA256：
[截图清单](reference/interface-captures.json)。
示例任务、章节、邮件和分类文本也分别采用对应语言。

截图任务验证界面可渲染及特定设置操作，不等于全部历史功能回归。
早期截屏夹具修正了缺失章节 group、未初始化父界面和输出结果路径，
最终结果以两种语言的完整成功记录为准。

## 生产构建与发布校验

四个生产目标分别构建。1.20.1 使用 Java 17、1.21.1 使用 Java 21。
Forge 1.20.1 与 Fabric 1.20.1 顺序执行，避免源适配目录冲突。
四目标最终生产构建均为 `BUILD SUCCESSFUL`。
发行校验检查版本号、加载器元数据、必要类、新图标资源、
测试类未混入、核心体积及远端附件 SHA256。

日志位于本地 `build/verification/release021-*-build.log`。
双语截图日志为 `documentation-zh-run.log` 和 `documentation-en-run.log`。

## 奖励详情、搜索与自定义说明

`runClient -PrewardInspectionTest -PneoSmokeRunDir=run-reward-inspection-search --offline` 通过。
真实领取任务/邮件奖励后，点开已领取物品检查，其图标与物品身份保留；
经验点数、等级详情使用对应图标并显示数量。
普通战利品和冻结自选的表头图标、条目列表、继续检查及返回均完成专项断言，
查看前后钻石数量保持不变，未重复发奖。

新增条目 `detailText` 最长 512 字符，可视化编辑器提供独立字段；
保存/重载与冻结自选元数据保留说明，旧文件缺字段默认为空。
正好 10 条不显示搜索，11 条显示；详情文字搜索、无结果、
清空恢复和过滤后选择原始候选索引（第 11 项，索引 10）均通过。
独立查看候选按钮返回后不增加选择。

任务普通底栏的已领取物品点击在初始专项中本来可打开；
本轮对自定义奖励布局增加优先卡片命中，防止其他控件遮挡，
并修复邮件仅少数附件类型拥有检查入口的问题。
不把未经单独复现的第三方覆盖或所有自定义布局都列为已验证。
日志与结果：`reward-inspection-run.log`、`reward-inspection-result.txt`。

另运行战利品完整专项 `runClient -PlootTableTest -PneoSmokeRunDir=run-loot021-details --offline`，
完成自定义说明的真实输入框编辑/保存、旧有图标应用/重置/撤销/重做、
奖励冻结、邮件发放、桌面/小窗口非重叠布局与末项滚动等断言。
结果为通过，日志为 `release021-loot-run.log`。

最终截图检查还修正物品选择器网格的视口裁剪和鼠标命中范围，
防止最底行图标盖住手动 ID 输入框。
截图任务等待异步列表有内容且实际渲染三帧后才截图，
并断言底部输入框所在位置可以直接命中该输入框。
物品/贴图选择器的“应用、取消”相邻等宽，按钮文字左右至少各留 12 GUI 像素。
右侧复用 `ScrollRail`，支持拖动至末行和点击轨道平滑定位。
截图任务在当前尺寸、480×270 及 320×240 GUI 尺寸检查动作顺序、等宽、
留白、底部控件非重叠、滑块拖动、轨道缓动和不改变资源选择。

## 已知边界

- 本次没有重新运行全部历史回归。
- 宽泛创建流程仍有 `context-copy_id` 点击检查失败；
  连续旅行历程套件存在旧断言和外观撤销检查未通过项，详见
  [此前记录](release-verification-2026-10-18.md)。
- 完整作者工具主要针对本地集成服务器，远端协同编辑和全部第三方组合未完整验收。
- 构建通过或截图可见不能替代真实奖励发放、异常恢复和多人流程验证。
- 历史发布记录中的版本号和日期保留，当前首页与指南对应 0.2.1。
