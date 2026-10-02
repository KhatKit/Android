package heizige.kk.khatkit.uikit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import heizige.kk.kedge.theme.KedgeColors
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.theme.LocalKedgeStyle

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
        "title" -> KedgeTextStyles.displayTitle()
        "subtitle" -> KedgeTextStyles.title()
        "label" -> KedgeTextStyles.footnote()
        "code" -> KedgeTextStyles.body().copy(fontFamily = FontFamily.Monospace)
        else -> KedgeTextStyles.body()
    }
    @Composable override fun color(tone: String): Color = when (tone) {
        "primary" -> KedgeColors.primary
        "muted" -> KedgeColors.onSurfaceVariant
        "error" -> KedgeColors.error
        "success" -> KedgeColors.tertiary
        "warning" -> KedgeColors.secondary
        else -> KedgeColors.onSurface
    }
}

object StyleResolvers {
    private val resolvers = java.util.concurrent.ConcurrentHashMap<KedgeStyle, StyleResolver>().apply {
        put(KedgeStyle.MD3Exp, MaterialStyleResolver)
        put(KedgeStyle.Miuix, MiuixStyleResolver)
    }
    private val warned = java.util.concurrent.ConcurrentHashMap.newKeySet<KedgeStyle>()
    fun register(style: KedgeStyle, resolver: StyleResolver) { resolvers[style] = resolver }
    fun resolve(style: KedgeStyle): StyleResolver = resolvers[style] ?: MaterialStyleResolver.also {
        if (warned.add(style)) java.util.logging.Logger.getLogger("KhatKit.UI").warning("No StyleResolver for $style; using Material")
    }
    @Composable fun current(): StyleResolver = resolve(LocalKedgeStyle.current)
}
