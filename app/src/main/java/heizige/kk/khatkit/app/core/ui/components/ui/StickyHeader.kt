package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun StickyHeader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        ProvideTextStyle(
            KedgeTextStyles.title().copy(
                color = MaterialTheme.colorScheme.secondary
            )
        ) {
            content()
        }
    }
}
