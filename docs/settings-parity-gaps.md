# MD3 ↔ Miuix 设置项差异清单（只读盘点）

> 目的：用户要求「把 MD3 和 Miuix 的设置各个选项都统一」，本文件是动手前的**差异清单**。
> 只读产出：**产出时**未改任何 Kotlin/资源/gradle、未跑 gradle；本文件本身随后随
> 「设置项两风格统一」批次入库（原句「本文件本身也不提交」只描述 t54 盘点当时的状态，已随入库失效）。
>
> **判定口径**：只列**用户可感知**的东西 —— 条目文案（`title`/`summary`）、开关/滑块的默认值与可选值、下拉可选值、
> 点击行为与导航目标。对话框按钮文案（`cancel`/`copy`/`delete` 等）与格式串**不算条目**，只在确实影响语义时单列。
> 纯排版、注释、实现集中度（行数差、`PreferenceArrow`/`OptionItem` 计数差）**不作为结论**。
>
> **取证方法（可复核）**：① 逐对列出 `R.string.*` 键集合差；② 对每条差把键**归属到所在 composable**，
> 再检查对面文件是否引用了该 composable（`grep` 函数名）——这是剔除「共享实现导致的假差异」的关键一步；
> ③ 对剩下的候选逐处读源码确认。凡「Miuix 有意不做」的，给出证据（注释/共享组件签名），不硬判为缺陷。

---

## 用户裁定（2026-10-14）

> 用户于 2026-10-14 就本清单的待裁定项给出裁定，本批次按此落地。裁定口径与逐条落地情况如下。

1. **并集补齐：两侧条目完全一致** —— 不做「哪边更合理」的取舍，缺的补上、两侧键集合对齐。
   落地：**t58** 补齐 5 条并集差异 —— 外观三选（`SettingPage.kt` ↔ `SettingPageMiuix.kt`）、主题 hub 入口、轨迹审计（`Screen.TraceAudit`）、通知确认键、设备行；资源上 **7 个 `res/values*/strings.xml` 各新增 2 个键**（`setting_page_trace_audit` / `setting_page_trace_audit_desc`，7 目录 × 2 行、删除 0 行）。
   注：`SettingPreferencesNotificationPage.kt`（MD3 侧）经复核**净改动为 0、逐字节等于 HEAD** ⇒ 本批不提交该文件。
2. **#6 模型条目副行取「说明文案」**。落地：**t59** —— `SettingModelPageMiuix.kt` 的模型条目副行统一取说明文案；配套 `SettingPreferencesUIPageMiuix.kt`（字体族 + 自定义字体进 Miuix）、`SettingPreferencesNetworkPageMiuix.kt`（代理非法文案）。
3. **#7 提供商保存取「即改即存」**。落地：**t59** —— `SettingProviderDetailPage.kt` / `SettingProviderDetailPageMiuix.kt` 改为逐字段即改即存。
   配套返工 **t67**：「即改即存」改造曾使 name 的 `trim()` 规范化丢失，现补回为**离开编辑面时一次性规范化**（`feature/settings/components/ProviderConfigure.kt`，+37/−3；三处 name 输入框全覆盖，`onValueChange` 逐字未动 ⇒ 不逐键 trim）。
4. **§20 的 4 页本轮一起做**（不再留给后续批次）。落地分工：
   - **t60**：新建 `SettingThemePageMiuix.kt`、`SettingWebPageMiuix.kt` + 两个 MD3 页的 `isMiuix` 早退块（`SettingThemePage.kt`、`SettingWebPage.kt`）。
   - **t61**：新建 `SettingProviderPageMiuix.kt`、`SettingModelPromptPageMiuix.kt` + 两个 MD3 页的早退块（`SettingProviderPage.kt`、`SettingModelPromptPage.kt`）。
     注：提示词页的早退放在**两页共用的漏斗 `PromptSettingsContent`**（`SettingModelPromptPage.kt`），而非最外层页面函数 —— 否则 Miuix 的提示词页签（`SettingModelPageMiuix.kt` 直接调该漏斗）会漏掉分流。
5. **另有按评审补做的一条**：**t69** 给通知页 Miuix 侧的确认 / 取消按钮补齐语义（`SettingPreferencesNotificationPageMiuix.kt`，+1/−1）—— 与 t58 的改动在**同一个文件**内，一并提交，不拆分。

> 上文 ④ 的「本轮」即本批；本清单其余「待用户裁定」条目已在 §22 与下面的「遗留与已知项」中逐条归档。

---

## 0. 结论摘要

- **真正需要用户拍板的条目级差异只有 7 条**（5 条 MD3 独有 / 2 条 Miuix 独有），另有 **5 条「两侧都有但语义/文案不一致」**。
- **大部分「行数差」不是缺项**：`SettingSpeechPage`(842 vs 182)、`SettingFilesPage`(388 vs 181)、
  `SettingSearchPage`(418 vs 142)、`SettingSearchDetailPage`(377 vs 107)、`SettingMcpPage`(448 vs 109)、
  `SettingPreferencesGeneralPage`(324 vs 204) 的差都来自「MD3 文件里同时住着**两风格共用的列表/条目 composable**」，
  这些键的属主函数被 Miuix 页直接调用 ⇒ **无条目差异**（逐条见 §1-§19）。
- **另有一类问题不是「选项集合」而是「风格覆盖」**：4 个页面**没有 Miuix 变体**（§20），Miuix 风格下走 MD3 实现。
- 用户可见的**本地化不一致**也一并列出（2 处整段硬编码中文）。

### 差异最大的前 10 项（按用户可感知影响排序）

| # | 差异 | 侧 | 位置 | 建议 |
|---|---|---|---|---|
| 1 | 聊天字体族选择（默认/衬线/等宽/自定义）整组缺失 | MD3 独有 | `SettingPreferencesUIPage.kt:243,526-529` vs `SettingPreferencesUIPageMiuix.kt:39`（注释：MD3 专属组件留在子组件里） | **待用户裁定**（Miuix 有意不做，有注释为证） |
| 2 | 自定义字体导入/移除 | MD3 独有 | `SettingPreferencesUIPage.kt:269-309` | **待用户裁定**（同上） |
| 3 | 外观三选：跟随系统/浅色/深色 | MD3 独有 | `SettingPage.kt:139-141,163`（卡片 `:143-167`） | **待用户裁定**（Miuix 用「主题」hub 替代，见 #4） |
| 4 | 「主题」hub 入口 | Miuix 独有 | `SettingPageMiuix.kt:81-85` → `Screen.SettingThemes` | **待用户裁定**（与 #3 二选一或都保留） |
| 5 | 轨迹审计入口（截图/目标/结果回看） | MD3 独有 | `SettingPage.kt:226-231`（文案硬编码） | **补到 Miuix** + 顺手换资源键 |
| 6 | 模型条目的副行语义：说明文案 vs 当前模型名 | 语义不一致 | MD3 `SettingModelPage.kt:119,128,149,158` vs Miuix `SettingModelPageMiuix.kt:104-132,176-177` | **待用户裁定** |
| 7 | 提供商详情的保存语义：显式「保存」按钮 vs 即改即存 | 语义不一致 | MD3 `SettingProviderDetailPage.kt:366` vs Miuix `SettingProviderDetailPageMiuix.kt:62,118` | **待用户裁定** |
| 8 | 通知页确认按钮文案：取消 vs 确定 | 文案不一致 | MD3 `SettingPreferencesNotificationPage.kt:214`（`cancel`）vs Miuix `SettingPreferencesNotificationPageMiuix.kt:148`（`common_confirm`） | **核对后统一**（疑似 MD3 用错键） |
| 9 | 套餐页「设备名 / 设备 ID」信息行 | Miuix 独有 | `SettingPackagePageMiuix.kt:92-97` | **补到 MD3**（或判定不需要，待裁定） |
| 10 | 代理地址非法时的反馈形式 | 语义不一致 | MD3 `SettingPreferencesNetworkPage.kt:195-205`（supportingText 换文案）vs Miuix `SettingPreferencesNetworkPageMiuix.kt:83`（仅 `isError` 红框） | **补到 Miuix**（Miuix 缺明确文案） |

附带（不计入前 10）：`SettingPage.kt:404-420` 的「配置 API」按钮在 Miuix 无对应（但 Miuix 主页已有「提供商」入口 ⇒ 建议**判定不该有**或待裁定）；
`SettingPage.kt` 的 3 个分组标题（`:172/:198/:266`）Miuix 由 `miuixGroup` 承担（呈现方式差异）；
`SettingPackagePage.kt` 整页文案硬编码中文（`:98-204`）、`SettingAboutPageMiuix.kt:248` 版本号串硬编码（非条目差异，属 i18n）。

**总量**：条目级真差异 **7**（MD3 独有 5、Miuix 独有 2）；语义/文案不一致 **5**；风格覆盖缺口 **4 个页面**；
经核实为「假差异（共享实现）」的候选 **30+ 条**（逐页见表）。

---

## 1. SettingPage (482) / SettingPageMiuix (241)

| 类别 | 条目 | MD3 位置 | Miuix 位置 | 建议 |
|---|---|---|---|---|
| MD3 独有 | 外观三选（跟随系统/浅色/深色） | `SettingPage.kt:139-141`（文案）、`:163`（标题 `setting_page_color_mode`）、卡片 `:143-167` | 无（Miuix 用主题入口，见下） | 待用户裁定 |
| MD3 独有 | 轨迹审计入口（`Screen.TraceAudit`） | `SettingPage.kt:226-231`（文案硬编码「轨迹审计」/副文案） | 无 | 补到 Miuix（并改资源键） |
| MD3 独有 | 「配置 API」按钮 → 提供商页 | `SettingPage.kt:410`（`setting_page_config_api_title`）、`:414-420` | 无 | 判定不该有（Miuix 主页 `:118-120` 已有「提供商」入口，重复）或待裁定 |
| MD3 独有 | 分组标题：通用设置 / 模型与服务 / 数据设置 | `:172`、`:198`、`:266` | Miuix 用 `miuixGroup` 分组（无同名标题） | 呈现方式差异，无需改 |
| Miuix 独有 | 「主题」hub 入口 → `Screen.SettingThemes` | 主页无（MD3 主题设置在 `SettingPreferencesThemePage`，入口未在主页） | `SettingPageMiuix.kt:81-85`（`setting_page_themes`/`_desc`） | 待用户裁定 |
| 两侧都有 | 偏好设置/助手/扩展/默认模型/提供商/搜索服务/TTS 服务/MCP/Web 服务/数据备份/聊天存储/关于/文档/请求日志/赞助/分享/套餐/触发器/市场 | `:170-382` 各区 | `:90-222` | 一致 |

## 2. SettingAboutPage (294) / SettingAboutPageMiuix (322)

- 差异（机制/本地化，非条目）：版本号显示 —— MD3 用资源键 `about_page_version_format`（`SettingAboutPage.kt:232-233`），
  Miuix 硬编码 `"${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"`（`SettingAboutPageMiuix.kt:248`）。
  信息等价 ⇒ **建议补到 Miuix**（改用资源键）。
- Miuix 文件更长是动画/占位实现，非设置项。

## 3. SettingDonatePage (55) / SettingDonatePageMiuix (34)

- `R.string.*` 键集合完全一致（差集为空）⇒ **无用户可感知差异**。

## 4. SettingFilesPage (388) / SettingFilesPageMiuix (181)

- MD3 独有键 5 个**全部落在被 Miuix 页引用的共享函数里**：
  `clean_range_description`(:255)、`clean_older_than_days`(:275)、`clean_all`(:276)、`folder_upload`(:310)、
  `delete_content_description`(:359)；Miuix 页在用同一个清理面板（`SettingFilesPageMiuix.kt:96-98`
  的 `clean_title`/`clean_action` 与 MD3 `SettingFilesPage.kt:136-138` 成对，且两侧都传
  `onRangeSelected`）。⇒ **无条目差异**（行数差 = 共享实现的位置不同）。

## 5. SettingMcpPage (448) / SettingMcpPageMiuix (109)

- MD3 独有 `copy`(:290)、`cancel`：**对话框按钮文案**，且 `copy` 所在函数被 Miuix 页引用 ⇒ **无条目差异**。

## 6. SettingModelPage (232) / SettingModelPageMiuix (182)

- 条目集合一致：chat / fast / translate / ocr / compress 五个模型选择两侧都有
  （MD3 `:118-160`；Miuix `:104-132`）。
- **语义不一致（前 10 #6）**：MD3 每个条目带说明文案（`setting_model_page_*_desc`：`:119/:128/:149/:158`）；
  Miuix 的 `summary` 显示**当前模型名**（`SettingModelPageMiuix.kt:176-177`）⇒ 说明文案在 Miuix 不可见。
  建议：待用户裁定（若统一，把说明放副行、当前值放尾部，或两侧都显示两者）。

## 7. SettingPackagePage (215) / SettingPackagePageMiuix (155)

- 条目集合基本一致：商城入口、激活状态、AI 额度、工具额度、到期时间、刷新、清除本机激活信息、提示文案
  （MD3 `:118-204`；Miuix `:71-147`）。
- **Miuix 独有（前 10 #9）**：设备名 / 设备 ID 行（`SettingPackagePageMiuix.kt:92-97`）⇒ 建议补到 MD3。
- Miuix 另有 `refreshing`(`:120`)、刷新/清除的说明文案(`:124/:132`)；MD3 有网页跳转说明(`:128`)。
- **本地化差异**：MD3 整页文案硬编码中文（`:98,120,127-128,144-146,165,169,173,182,194-195,203-204`）；
  Miuix 用 `setting_package_page_*` 资源键 ⇒ 建议把 MD3 也换成资源键（非条目差异）。

## 8. SettingPermissionsPage (72) / SettingPermissionsPageMiuix (33)

- 键集合差集为空 ⇒ **无用户可感知差异**。

## 9. SettingThemesPage (93) / SettingThemesPageMiuix (41)

- 键集合差集为空 ⇒ 无条目差异（该页在 Miuix 从主页 hub 进入，见 §1）。

## 10. SettingPreferencesPage (106) / SettingPreferencesPageMiuix (69)

- 键集合差集为空 ⇒ 无条目差异。
- 注：`SettingPreferencesPageMiuix.kt:23` 注释说明「主题原本在这里，现在移到设置主页的 hub 下」
  ⇒ Miuix 的组织结构变化，不是缺项。

## 11. SettingPreferencesThemePage (182) / SettingPreferencesThemePageMiuix (106)

- 键集合差集为空 ⇒ 无条目差异。

## 12. SettingPreferencesGeneralPage (324) / SettingPreferencesGeneralPageMiuix (204)

- MD3 独有 `setting_page_tts_settings`(:263) 所在函数被 Miuix 页引用 ⇒ **无条目差异**。

## 13. SettingPreferencesNetworkPage (430) / SettingPreferencesNetworkPageMiuix (205)

- **语义不一致（前 10 #10）**：代理地址非法时的反馈 —— MD3 在 `supportingText` 里换成
  `setting_page_preferences_network_proxy_invalid`(`:195-205`)；Miuix 只在输入框上给 `isError`
  （`SettingPreferencesNetworkPageMiuix.kt:83`，无文字说明）⇒ 建议补到 Miuix（明确文案）。

## 14. SettingPreferencesNotificationPage (219) / SettingPreferencesNotificationPageMiuix (153)

- **文案不一致（前 10 #8）**：确认按钮 —— MD3 `SettingPreferencesNotificationPage.kt:214`(`cancel`) vs
  Miuix `SettingPreferencesNotificationPageMiuix.kt:148`(`common_confirm`)。两侧同一个确认弹窗给了不同语义的
  按钮文案 ⇒ 建议核对后统一（疑似 MD3 处用错键）。
- 其余键差为对话框/提示文案，且 `setting_update_reminder_resume_now` 所在函数被 Miuix 页引用 ⇒ 无条目缺失。

## 15. SettingPreferencesUIPage (605) / SettingPreferencesUIPageMiuix (186)

- **MD3 独有（前 10 #1、#2；Miuix 有意不做，有注释为证）**
  - 聊天字体族选择：`setting_display_page_chat_font_family_title`(`SettingPreferencesUIPage.kt:243`)、
    可选值 `_default`/`_serif`/`_monospace`/`_custom`(`:526-529`)。
  - 自定义字体导入/移除：`custom_font_title`(`:269`)、`custom_font_not_imported`(`:275`)、
    `custom_font_import`(`:288`)、`custom_font_remove`(`:309`)、成功/失败提示(`:92-93`)。
  - 证据（有意为之）：`SettingPreferencesUIPageMiuix.kt:39` 注释「依赖 ChatFontFamily、MarkdownBlock 等
    MD3 专属组件的部分保留在各自子组件里」；Miuix 侧只取 `rememberChatFontFamily`(`:30,45,133`) 应用字体，
    不提供选择 UI ⇒ **建议待用户裁定**（要统一就得把字体族选择 + 导入做成两风格可用）。
- 其余 MD3 独有键**已被 Miuix 覆盖**：代码块设置 → `CodeDisplaySettingsGroupMiuix`(`Miuix:140`)；
  列表卡片 → `ListCardStyleGroupMiuix`(`Miuix:165`)；界面风格 → `UiStyleGroupMiuix`(`Miuix:157`)；
  模糊 → `BlurToggleGroupMiuix`(`Miuix:26`)；消息显示分组标题由 `miuixGroup` 承担 ⇒ 无条目缺失。

## 16. SettingProviderDetailPage (419) / SettingProviderDetailPageMiuix (150)

- **语义不一致（前 10 #7）**：MD3 有显式「保存」按钮（`SettingProviderDetailPage.kt:366`，成功提示 `:175`）；
  Miuix 无保存按钮，改为编辑即生效并弹 `setting_provider_page_save_success`（`SettingProviderDetailPageMiuix.kt:62,118`）
  ⇒ 待用户裁定（统一为「显式保存」或「即改即存」）。
- **非缺陷（已核实）**：删除功能两侧都有 —— Miuix 把删除放在共享组件里
  （`SettingProviderDetailPageMiuix.kt:122` 把 `onDelete` 传给 `SettingProviderConfigPage`），
  所以 MD3 侧的 `confirm_delete`/`delete_dialog_text`(`:385,:388`) 属**共享实现**，不是 Miuix 缺项。

## 17. SettingSearchDetailPage (377) / SettingSearchDetailPageMiuix (107)

- MD3 独有 4 键（`setting_page_search_test`(:279)、`_query_hint`(:291)、`_run`(:322)、
  `search_detail_unknown_error`(:369)）**全部落在被 Miuix 页引用的共享函数里** ⇒ 连接测试面板两风格都有 ⇒ **无条目差异**。

## 18. SettingSearchPage (418) / SettingSearchPageMiuix (142)

- MD3 独有键（`setting_page_search_common_options`(:398)、`setting_page_search_result_size`(:404)、
  `search_ability_search`(:366)/`search_ability_scrape`(:372)）均属共享实现：能力标签在
  `SearchProviderCard`(`SettingSearchPage.kt:278`，调用点 `:313`) 内，而 Miuix 页**直接调用**
  `SearchProviderCard`（`SettingSearchPageMiuix.kt:90`）⇒ **无条目差异**。

## 19. SettingSpeechPage (842) / SettingSpeechPageMiuix (182)

- MD3 独有 14 键**全部**位于两风格共用、但物理上写在 MD3 文件里的 composable：
  `TTSProviderList`(`SettingSpeechPage.kt:258`)、`ASRProviderList`(`:336`)、
  `AddTTSProviderButton`(`:418`)、`AddASRProviderButton`(`:467`)、`TTSProviderItem`、`ASRProviderItem`；
  Miuix 页调用它们（`SettingSpeechPageMiuix.kt:59,65,98,104`）并复用同一批配置表单
  （`ASRProviderConfigure`/`TTSProviderConfigure`，见 Miuix 页 `:141,175`）⇒ **无条目差异**
  （842 vs 182 只是「共享实现住在 MD3 文件里」造成的行数差）。
- 两侧都有：TTS/ASR 双页签、新增提供商、编辑弹窗、默认名、测试 TTS 等。

---

## 20. 没有 Miuix 变体的页面（「风格覆盖」缺口，不是选项集合差异）

| 页面（MD3 文件） | 行数 | Miuix 变体 | 证据 | 影响 |
|---|---|---|---|---|
| `SettingProviderPage.kt` | 602 | 无 | `grep -c 'KedgeStyle.Miuix\|isMiuix'` = 0 | Miuix 风格下进入该页看到 MD3 观感（主页 `SettingPageMiuix.kt:118-120` 有入口） |
| `SettingThemePage.kt` | 747 | 无 | 同上 = 0 | 同上 |
| `SettingWebPage.kt` | 387 | 无 | 同上 = 0 | 同上（Miuix 主页 `:157-160` 有入口） |
| `SettingModelPromptPage.kt` | 167 | 无 | 同上 = 0 | 同上 |
| `SettingTriggersPage.kt` | 811 | 无（仅按风格调圆角） | `:539` 用 `style == KedgeStyle.Miuix` 选 20/24dp | 观感近似，非缺项 |

> 这 4 页的**选项集合本身**在两种风格下是一样的（同一实现），要让用户「看起来也统一」需要单独的任务。

## 21. 共享组件（改一处影响哪些页）

| 组件 / 文件 | 作用 | 被多少设置页使用（`grep -rln` 计数） |
|---|---|---|
| `core/ui/components/ui/miuix/PreferenceItems.kt` → `MiuixSettingsPage` | Miuix 设置页外壳（标题/返回/底栏/滚动容器） | **19** |
| 同上 → `miuixGroup` | Miuix 分组容器（承担 MD3 `CardGroup` 的分组标题） | **14** |
| 同上 → `PreferenceArrow` | 可点条目（title + summary + 导航） | **8** |
| 同上 → `PreferenceSwitch` | 开关条目 | **5** |
| 同上 → `PreferenceSlider` | 滑块条目 | **2** |
| 同上 → `PreferenceDropdown` | 下拉条目 | **2** |
| 同上 → `PreferenceInfo` | 只读信息行 | **1** |
| 同上 → `PreferenceRadio` | 单选条目 | **0（当前无人用；Miuix 侧实为无单选控件可用）** |
| `core/ui/components/ui/miuix/KedgeForm.kt` | 表单项容器（与 MD3 表单对齐） | **1** |
| `feature/settings/components/SettingPreferencesUIComponentsMiuix.kt` | 界面设置页的 Miuix 分组（`BlurToggleGroupMiuix`/`CodeDisplaySettingsGroupMiuix`/`ListCardStyleGroupMiuix`/`UiStyleGroupMiuix`） | 界面设置页 1 页，但被 §15 的多条题目引用 |
| `feature/settings/components/{ASRProviderConfigure,TTSProviderConfigure,ProviderConfigure,ThemePickers*}.kt` | 两风格共用的配置表单/主题选择器（含 `*Miuix.kt` 变体） | 语音页、提供商页、主题相关页 |

⚠️ **改共享组件的后果**：`PreferenceItems.kt` 的任何签名/默认值变化会同时影响 **19 个 Miuix 设置页**；
`miuixGroup` 影响 14 页 ⇒ 统一两风格时应优先在**页面级**补条目，避免为了一个页面改共享 DSL。

---

## 22. 明确未做 / 待用户裁定的部分

- **未做**：未改任何代码或资源；未跑 gradle（本任务不需要）；未提交。
- **未验证**：真机/模拟器上的实际观感与交互（本清单是源码级盘点）。没有实机截图对比，
  因此「用户是否真的在意某条差异」需要产品侧裁定。
- **待用户裁定（7 条条目级 + 5 条语义/文案）**：见 §0 表格「待用户裁定」与「建议」列；
  其中 #1/#2（字体族与自定义字体）与 #7（保存语义）影响最大，建议先定这三条。
- **一处待确认**：MD3 侧从主页直达「主题」设置页的入口我没找到
  （`Screen.SettingThemes` 全仓只在 Miuix 主页与路由注册处出现，见 §1/§10），
  若 MD3 用户确实需要「主题」入口，则它与 #3（外观三选）要在同一次决策里一起定。

### 遗留与已知项（2026-10-14 收口时如实台账，**本批只记账、不改任何源码**）

> 以下全部来自各评审的**观察项**（均未判负、不影响对应任务的 verdict）。
> 记在这里是为了不让它们消失；**本批一律不修** —— 四条评审的通过结论都绑在冻结 hash 上，
> 任何源码改动都会让 t62–t65 / t68 的判定失效、逼出第二轮评审。

#### A. 来自 t64 评 t60（主题页 / Web 页 Miuix 变体，verdict=pass）

- **O1（low）`LocalClipboardManager` 弃用告警**：`feature/settings/SettingWebPageMiuix.kt:81`
  与 MD3 对位 `feature/settings/SettingWebPage.kt:90` 是同一条告警。
  - **定性：非本批新引入的缺陷**（两页同源，MD3 那条是既有的）；**本批未修**。
  - ⚠️ **台账更正**：t60 自述「4 个文件零 warning」**不准确**。收口时（t66）我用**同一棵树的 `--rerun` 编译日志**实测：
    全日志 `e:` = 0、`w:` = 41，其中 `feature/settings/` 下共 **2** 条，正是这一对 `LocalClipboardManager` 弃用，
    **其中一条就落在 t60 新建的 `SettingWebPageMiuix.kt:81`**。故本条按实测记录，不沿用「零 warning」的措辞。
  - 可选清理：改用 `LocalClipboard` / `ClipEntry`（同批新建的 `SettingThemePageMiuix.kt:35-36` 已这么写，可作范本）。**本批不做。**
- **O2（low，技术债）主题页镜像约 380 行私有 helper**：`SettingThemePageMiuix.kt:298-711` 镜像了 MD3 侧
  6 个 file-private helper；t64 已逐块核对语义与 MD3 **完全同构**（HSL 编解码与截断、secondary/tertiary 缺省回退、
  `take(8)` 历史、删除回退 `"sakura"`、`Unnamed`、导出同一 `Json { ignoreUnknownKeys; prettyPrint }`），
  差异只在观感（Miuix 取色 / `KedgeCard` 行 / `miuixItemSpacing`）。
  - **定性：技术债**（与 t59 的字体落盘助手镜像同类）。将来把两者一并**提升到 `core/`**（或抽成无状态 `*Helper`）后
    应**删除镜像**；风险是「将来只改一侧」。**本批不做**（提升会越出 t60 的 inScope）。
- **O3（low）顶栏「导入 / 新建」的可及状态是超集**：Miuix 把它们放进顶栏 `actions`
  （`SettingThemePageMiuix.kt:124-142`）＝**恒常可见**；MD3 放在 `if (!settings.dynamicColor)` 块内
  （`SettingThemePage.kt:183-215`）＝开启动态取色时不可见。
  - 后果：Miuix 下动态取色开启时仍能新建 / 导入主题，但 `themeId` 被改写后因 `dynamicColor` 仍为 true 而**不立即生效**
    （自定义主题列表 `:166` 仍被隐藏），需先关掉动态取色才看得到。
  - **定性：选项没少（主题页键集合 23 = 23），仅可及状态不一致，非阻断** ⇒ 记为待办。
    可选修法：`actions` 内同样按 `!settings.dynamicColor` 门控；或维持现状（与 `SettingMcpPageMiuix` 顶栏 actions 的既有做法一致）。**本批不做。**
- **O4（informational，本批之外）权限说明弹窗不做风格分流**：Web 页两风格共用的链路
  `core/.../PermissionManager.kt:26` → `PermissionRationaleDialog.kt:48` → Khromia `AnimatedAlertDialog.kt:39`
  不做风格分流（Khromia 的 `AnimatedAlertDialog` 里 `LocalKedgeStyle` 零命中；有分流的是 `AppAlertDialog.kt:51` 那条）
  ⇒ Miuix 下进入 Web 页并触发权限说明时会弹**MD3 观感**的对话框。
  - **定性：非 t60 引入**（MD3 页同一调用）、**相关文件不在 t60 的 inScope**，且修复要动 **Khromia**
    ⇒ 属**跨仓既有缺口**，记为待办，**本批不修**（若修建议单开任务）。

#### B. 来自 t62 评 t58（并集补齐的观察项）

- **O1 `values-ar` 缺既有键**：`setting_package_page_device` / `setting_package_page_device_id` /
  `setting_package_page_loading` 在 `res/values-ar/` **0 命中**（HEAD 亦为 0）⇒ 阿语下 MD3 设备行现在回落英文。
  **定性：既有缺口、非本批新增键、非回归**（此前 MD3 是硬编码中文、Miuix 本来就回落英文）。待裁定是否补 ar 翻译。**本批不修。**
- **O2 Miuix 主页 5 个 `miuixGroup` 未传分组标题** ⇒ `setting_page_general_settings` /
  `setting_page_model_and_services` / `setting_page_data_settings` / `setting_page_config`（+`_api_title`）
  共 5 个键 Miuix 未用。**定性：全页 / 批次级既有呈现层差异**，选项集合不受影响。**本批不修。**
- **O3「探索市场」「套餐激活」等条目两侧仍硬编码中文**（本批只处理了 #5 的「轨迹审计」）。
  **定性：既有问题**，本批未触碰。**本批不修。**
