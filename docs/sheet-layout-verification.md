# Sheet 布局独立复核与手动验收清单

记录日期：2026-11-28。最终复核任务：t12，执行者 sheet-verify。本记录对应 t11 头像修订后的工作区；包含既有和并行 WIP，不代表干净提交或任务前基线。

## 结论与证据边界

**审查通过，设备验收待执行。** 六项 finding 均在源码中闭合；本轮独立编译退出 0，全量 JVM 测试退出 0。XML 统计 1,192 项，其中 1,189 通过、3 项因外部示例卡夹具缺失或不兼容而跳过，0 失败、0 错误。业务源码只读；实际共享调用仍为 53，两个计数护栏保持 53，未修改断言。

没有操作设备、组装 APK、暂存、提交或推送。源码核对不等于实际视觉、触摸或预测性返回验证。t9 的暂停验证记录保留为历史；t12 使用独立持久目录记录串行验证，启动前检查真实 Java GradleWrapper，避免与并行 WIP 构建交叉。

## 覆盖范围

| 范围 | 数量与检查内容 | 主要位置 |
|---|---|---|
| 共享 PrimaryBottomSheet | 53 个实际调用，不含两个重载声明；覆盖模型、助手、MCP、设置、备份、聊天、工具、日志及文本编辑入口 | [共享壳](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt#L144)；[滚动扫描护栏](../app/src/test/java/heizige/kk/khatkit/app/core/ui/components/ui/BottomSheetScrollSourceGuardTest.kt#L334)；[图标扫描护栏](../app/src/test/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheetIconSourceGuardTest.kt#L292) |
| 直接 MochiSheet 实现 | app 9 处，包括共享壳、头像、二维码、市场详情、权限及四类消息面板 | [头像](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/AvatarEditSheet.kt#L80)；[二维码](../app/src/main/java/heizige/kk/khatkit/app/feature/settings/SettingDonatePage.kt#L186)；[市场详情](../app/src/main/java/heizige/kk/khatkit/app/feature/explore/ExploreMarketPage.kt#L1899) |
| 兼容壳 | 2 个直接实现；KkSheetShell 有密钥/设置两处业务调用；BasicBottomSheet 没有业务调用 | [兼容壳](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/KkCompat2.kt#L61)；[密钥入口](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitDialogs.kt#L46) |
| 媒体面板 | 1 个直接实现；与 app、兼容壳合计 12 个直接实现 | [MediaFilterSheet](../mediapicker/src/main/java/heizige/kk/khatkit/mediapicker/ui/components/MediaFilterSheet.kt#L56) |
| 库行为 | 实际依赖为 0.4.0-khatkit.6；检查发布 sources jar，核对原 API、非滚动重载、有限高度与 IME 消费 | [版本目录](../gradle/libs.versions.toml#L81)；[唯一补丁脚本](../.github/scripts/patch-mochi.py#L127) |
| 规范 | Header/Content 同心布局，多页先退子页再关面板 | [Mochi Sheet 文档](../../Mochi/docs/zh_CN/components/sheet.md#L39) |

## t4 四项 finding 的复核

| finding | 结果 | 源码证据 |
|---|---|---|
| F1：可达兼容壳无规范正文和滚动 | 源码闭合 | [KkSheetShell](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/mochi/KkCompat2.kt#L95) 的两种分支均使用有界 MochiSheetContent；[密钥正文](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitDialogs.kt#L51) 显式交由壳滚动。 |
| F2：lazy/自滚动正文固定 16dp 内缩 | 源码闭合 | [共享非滚动分支](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt#L191) 使用库级 scrollable=false；[库补丁](../.github/scripts/patch-mochi.py#L173) 复用同心几何、从有限视口扣除上下内缩，不增加外层滚动。两处 floatingHeader 表单保持边到边正文、标题先测量。 |
| F3：调用内比例或正文固定上限 | 源码闭合 | app 主源码扫描 fillMaxHeight(0.xf) 为 0；正文改用剩余高度。更新日志正文、搜索结果和服务商网格保留滚动及独立动作区域；[更新日志](../app/src/main/java/heizige/kk/khatkit/app/feature/chat/components/ui/UpdateCard.kt#L133)、[MCP 导入](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/mcp/McpToolsConfigure.kt#L323)。仍存在的图表、网页预览、消息折叠区和独立 Dialog 高度限制不据此误删。 |
| F4：重复 IME 补白 | 源码闭合 | 原 finding 涉及的面板内 imePadding 已清除；[库补丁](../.github/scripts/patch-mochi.py#L213) 在已扣键盘高度后消费底部 IME，不再补第二层 padding。页面级与独立 Dialog 的 IME 处理保留。 |

[文本全屏编辑入口](../app/src/main/java/heizige/kk/khatkit/app/feature/assistant/components/ui/TextArea.kt#L196) 采用仅 Large 档的 PrimaryBottomSheet、scrollable=false 和填满剩余高度的文本框；保存仍调用 setTextAndPlaceCursorAtEnd 后退出，未改业务保存语义。

## t11 头像导航与头部动作复核

1. **F5 源码闭合：独立导航。** [AvatarEditSheet](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/AvatarEditSheet.kt#L64) 的同一 navigator 同时传给 Sheet 与 NavHost；选项页 push URL，左返回执行 pop，根页左关闭才 hide。内部 NavHost 省略 corners，继承面板轮廓；库处理子页系统/预测返回并在退页转场期间抑制外层关闭。
2. **F6 源码闭合：头部动作。** [头像头部](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/AvatarEditSheet.kt#L104) 提供根页关闭、URL 页返回与确认；使用稳定 sheet.leading/sheet.trailing 与 close/chevron-back/checkmark、资源化可访问名称。确认在 trim 后非空且面板可交互时才启用；正文底部重复取消和确认已移除。

头像程序收起的 requestClose/closing 与 onDismiss/delivered 双守卫已静态核对：五类动作和 URL 提交仅从库完成 hide 后的 onDismiss 分发一次。手势/系统关闭没有主动设置应用 closing；库用 leaving 状态移交触摸，但已按下手势和无障碍点击的竞争行为未经设备验证，不能声称已验证无误触。本轮头部动作在 closing、不可见或内部转场时禁用；正文动作仍由 requestClose/closing 防重。实际触摸与无障碍竞态仍列入设备清单，不以静态守卫代替设备通过结论。

## 命令、退出码与结果保存

| 轮次 | 命令 | 退出码/结果 | 证据说明 |
|---|---|---|---|
| t12 | ./gradlew :app:compileDebugKotlin | **0** | 本轮独立执行；有效任务输出为 UP-TO-DATE，守卫、stdout、stderr、显式退出码已保存。 |
| t12 | ./gradlew :app:testDebugUnitTest | **0；1,192 项，0 失败、0 错误、3 跳过** | test task 实际执行；共 1,189 通过，完整 XML 和 JSON 摘要保存到持久目录。全量成功，未重复启动过滤测试。 |
| t4 | ./gradlew :app:compileDebugKotlin | 0 | 上一轮本会话已收集的完成回执。 |
| t4 | ./gradlew :app:testDebugUnitTest | 1；1,178 项、52 失败 | 上一轮本会话已收集；面板三组源码护栏 11/11 通过。不是任务前基线。 |
| t9 | ./gradlew :app:compileDebugKotlin | **unknown，不能判通过** | 已启动 job bash-cc99153e00bd456d；收集时工具返回 unknown job，随后日志和显式 exit 文件均不存在，没有可核实的完成退出码。未擅自重启。 |
| t9 | ./gradlew :app:testDebugUnitTest | **未执行，无退出码** | 尚未启动时 captain 要求已知头像修订后再统一验证，本轮据此停止新构建。 |
| t9 | git diff --check | 0 | 创建本文前的源码差异空白检查通过；文档另作空白检查。 |

t9 发生过证据丢失：当时开始前可读的 21:17 既有 XML 有 5 组、8 个失败，随后临时日志和测试 XML 消失。下表保留那轮已经读取的签名，作为历史记录，不是 t12 的结果，也不是真正任务前基线。

| 既有 XML 中的失败组 | 失败数 | 与 t4 的关系 / 分类 |
|---|---:|---|
| BrowserExampleCardsTest | 3 | t4 已出现：两个示例脚本缺失及 manifest 的 limits 字段解析不兼容。 |
| CardRunsSchemaTest | 1 | t4 已出现：DDL 与注册断言不一致。 |
| ConversationGroupCardsSchemaTest | 1 | t4 已出现：测试仍断言数据库版本 37。 |
| AIIconAssetsTest | 2 | t4 已出现：sensenova-color.svg 的多色和 -color 命名。 |
| PageBackInterceptorTest | 1 | t4 已出现：mostRecentlyRegisteredPredictiveOverlayWins；它涉及返回仲裁，后续不能只凭静态转发就宣布交互通过。 |

历史 8 个失败名称均在 t4 的 52 项中；t12 当前工作区不再出现任何失败。旧路径/DI 名称/设置索引/shell 类型与数据库、图标、返回仲裁测试当前均通过；BrowserExampleCardsTest 的 3 项通过 assumeTrue 明确跳过，原因是同级 KhatKitCards 的 main.js 缺失，或 card.json 的 limits 字段与当前模型不兼容，**这 3 项没有通过业务断言**。并行 WIP 修正了测试路径及其他历史问题，本轮未修改这些测试，不把失败减少归因于 Sheet 修复。没有任务前工作树/测试基线，不能声称任务前基线已经复现。

本轮持久证据位于 [t12-d946f7fb](/home/heizige/文档/khatkit-sheet-validation/t12-d946f7fb/)：compile.stdout.log / compile.stderr.log / compile.exitcode、all-tests.stdout.log / all-tests.stderr.log / all-tests.exitcode、all-tests.xml.zip、all-tests.summary.json、final-result.json，以及源码快照和启动守卫记录。

Sheet 三组源码护栏 **13/13** 通过（滚动 3、图标 3、布局 7）；加上返回仲裁 3、分享 8、扫码 16，共 **40/40** 相关现有测试通过。库 API/几何/IME/返回静态检查 **9/9** 通过；头像新导航与回调仅做源码核对，未新增伪装成设备验证的文本测试。每次构建前确认无其他实际 Java GradleWrapper；先等待 sponsor 与 lint 的两个 PID 退出，再串行执行本轮两条命令，未干预其他会话进程。

## 手动验收：改动点

| 改动点 | 关注内容 |
|---|---|
| [模型选择器](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ai/ModelList.kt#L285) | 搜索、模型列表、供应商条在同一有限正文内分配高度，无比例留白。 |
| [共享壳](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/PrimaryBottomSheet.kt#L182) | Header 固定；托管滚动与 lazy/自滚动二选一，正文与按钮同心对齐。 |
| [兼容密钥面板](../khatkit-ui/src/main/java/heizige/kk/khatkit/uikit/KhatKitDialogs.kt#L46) | 长密钥列表可滚至末尾，撤销仍对应当前密钥。 |
| [文本编辑器](../app/src/main/java/heizige/kk/khatkit/app/feature/assistant/components/ui/TextArea.kt#L196) | 仅 Large 档；长文本编辑、键盘避让、保存与取消。 |
| [头像面板](../app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/AvatarEditSheet.kt#L58) | 独立导航与头部动作已接入；验收 URL 子页先返回、根页关闭、非空确认及关闭后单次派发。 |

## 手动验收：复现路径

- 聊天页底部模型名称 → 模型选择：分别测试零个、少量和大量模型，输入搜索并切换供应商。
- 聊天页「+」→ 扩展/工作区/目录选择；聊天助手与 MCP 入口 → 筛选、选择、管理或配置。
- 设置 → 服务商 → 新增/编辑模型：切换三个表单页签，编辑末尾字段；语音、搜索、备份入口 → 打开列表面板。
- 市场 → 卡片详情 → 密钥：准备足够多的密钥后滚动并撤销最后一项；媒体选择器 → 排序/分组。
- 助手详情的多行文本区域 → 全屏编辑：输入长文本，分别保存和取消。
- 可编辑头像 → 五类动作逐个触发；再次打开 → 输入 URL → 返回选项页 → 再进入并确认，另测取消和空 URL。

## 手动验收：预期结果

- [ ] 半屏到全屏、下拉和回弹不出现正文额外空白、重叠或越界；短内容与长列表都能操作。
- [ ] Header 与正文同心对齐；表单标题不覆盖正文，最后一行及底栏动作可见。
- [ ] 向上先扩展面板再滚列表；列表到顶后向下拖面板，不出现双重纵向滚动异常。
- [ ] 键盘显示/隐藏时正文只让位一次，输入框可见，导航栏安全区不重复。
- [ ] 子页返回先退一级，根页才关闭；快速连续返回不会关闭尚在退页动画中的外层面板。
- [ ] 头像动作与 URL 回调均在收起完成后执行一次；双击、拖拽关闭、预测返回取消和无障碍点击不会误提交。
- [ ] 文本编辑保存更新值，取消不提交；选择、筛选、下载、管理、撤销等业务动作与修订前一致。

## 手动验收：边界

全部待设备验证：明/暗外观、玻璃开/关、系统减弱动画、常规与最大字号、窄屏/宽屏/横屏及侧边挖孔、手势/三键导航、IME 动画、普通/预测返回的完成与取消、快速开关与重复点击、空列表和大量内容。本文不包含真机通过结论。
