package heizige.kk.khatkit.app.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 页面级留白度量，**MD3 与 Miuix 共用**（两种风格都要能跑，所以不能放进
 * `MiuixPageMetrics`）。
 *
 * 背景：设置页原先统一写 `contentPadding = innerPadding + PaddingValues(8.dp)`。
 * `PaddingValues(8.dp)` 本身四边对称，看着没问题，但 `innerPadding.bottom` 的大小
 * 完全取决于各骨架的 `contentWindowInsets`：
 *
 * - MD3 的 `Scaffold` 默认吃 `WindowInsets.systemBars`，底部至少有一条导航栏；
 * - Miuix 侧（`KedgeSettingsPageScaffold` → `MiuixPageScaffold`，以及
 *   `MiuixSettingsPage`）的 `contentWindowInsets` 只保留 `Horizontal`
 *   （见 `Kedge/kedge/.../adaptive/KedgeBlur.kt` 与 `MiuixSettingsPage.kt`），
 *   `innerPadding.bottom` 恰好是 `0.dp`。
 *
 * 于是 Miuix 下末项离屏幕底只剩 8dp，明显比顶部（顶栏 100dp+ 加状态栏 inset）
 * 空得多，滚到底像是被切断。给底部补 [BottomContentPadding] 让两侧观感平衡。
 */
object PageMetrics {
    /**
     * 列表底部留白（在 `innerPadding` 之上再叠加）。
     *
     * 取 16dp 与本仓既有惯例一致：`SettingDonatePageMiuix` / `SettingFilesPageMiuix` /
     * `SettingMcpPageMiuix` / `SettingSearchPageMiuix` 早已显式传
     * `bottomInnerPadding = 16.dp`。
     *
     * **页面已有 `bottomBar` 时不要加**：Scaffold 的 `innerPadding.bottom` 已经
     * 含 bottomBar 高度，再加就是双重留白。见 `SettingModelPage` /
     * `SettingSpeechPage` / `SettingProviderDetailPage`。
     */
    val BottomContentPadding = 16.dp

    /**
     * 有 FAB 的页面用的底部留白，给按钮让出位置，否则末项会被压住。
     *
     * 数值对齐 `SkillsPage.kt` 的既有写法（`bottom = 16.dp + 72.dp`）。
     * 见 `SettingWebPage`。
     */
    val BottomContentPaddingWithFab = 72.dp
}