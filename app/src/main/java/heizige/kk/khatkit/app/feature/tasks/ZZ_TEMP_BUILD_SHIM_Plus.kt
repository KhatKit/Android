package heizige.kk.khatkit.app.feature.tasks

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * ⚠️ 临时编译垫片，**用完立刻删**。
 *
 * 另一会话的 `CardRunsPage.kt` 用了 `PaddingValues + PaddingValues`，但没 import
 * `heizige.kk.khatkit.app.core.util.plus`。同包声明一个等价扩展就不需要对方改文件。
 */
@Composable
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + other.calculateStartPadding(direction),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(direction) + other.calculateEndPadding(direction),
        bottom = calculateBottomPadding() + other.calculateBottomPadding(),
    )
}
