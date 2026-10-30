package heizige.kk.khatkit.uikit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

interface StyleResolver {
    @Composable fun textStyle(role: String): TextStyle
    @Composable fun color(tone: String): Color
}

object MaterialStyleResolver : StyleResolver {
    @Composable override fun textStyle(role: String): TextStyle = when (role) {
        "title" -> MaterialTheme.typography.titleLarge
        "subtitle" -> MaterialTheme.typography.titleMedium
        "label" -> MaterialTheme.typography.labelMedium
        "code" -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyMedium
    }
    @Composable override fun color(tone: String): Color = when (tone) {
        "primary" -> MaterialTheme.colorScheme.primary
        "muted" -> MaterialTheme.colorScheme.onSurfaceVariant
        "error" -> MaterialTheme.colorScheme.error
        "success" -> MaterialTheme.colorScheme.tertiary
        "warning" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

object MiuixStyleResolver : StyleResolver {
    @Composable override fun textStyle(role: String): TextStyle = when (role) {
        "title" -> MaterialTheme.typography.headlineMedium
        "subtitle" -> MaterialTheme.typography.titleMedium
        "label" -> MaterialTheme.typography.labelMedium
        "code" -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        else -> MaterialTheme.typography.bodyMedium
    }
    @Composable override fun color(tone: String): Color = when (tone) {
        "primary" -> MaterialTheme.colorScheme.primary
        "muted" -> MaterialTheme.colorScheme.onSurfaceVariant
        "error" -> MaterialTheme.colorScheme.error
        "success" -> MaterialTheme.colorScheme.tertiary
        "warning" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

object StyleResolvers {
    // U4b：旧风格枚举 双风格已下线，只剩 MD3/Mochi 一套解析器。
    fun register(@Suppress("UNUSED_PARAMETER") resolver: StyleResolver) = Unit
    fun resolve(): StyleResolver = MaterialStyleResolver
    @Composable fun current(): StyleResolver = MaterialStyleResolver
}
