package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.LocalKkNavController
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitTreeRequest
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.uikit.CardRunResult
import heizige.kk.khatkit.uikit.LocalKhatKitSheetInline
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.theme.MochiTheme

/**
 * 欢迎页小组件点开的卡片整页。页面从小组件那一格长出来（Mochi 来源容器变形，
 * 来源 key 见 [widgetSourceKey]），进来就以用户身份运行卡片，卡片的节点树请求
 * （ui.screen / ui.form / ui.show）铺在页里；确认框、动作面板仍由全局宿主弹层。
 *
 * - 运行结束且没有可留的内容：自动返回，缩回小组件；
 * - 最后一屏是 ui.show 的只读结果：留在页面上，用户自己返回；
 * - 用户返回：收掉本卡片还开着的那一屏，脚本按「取消」继续。
 */
@Composable
internal fun CardWidgetPage(name: String, title: String) {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val nav = LocalKkNavController.current
    val queued by provider.uiRequest.collectAsStateWithLifecycle()
    val owned = queued?.takeIf { provider.isPageHosted(it, setOf(name)) }
    var shown by remember { mutableStateOf<UiRequest?>(null) }
    LaunchedEffect(owned) { if (owned != null) shown = owned }
    var result by remember { mutableStateOf<CardRunResult?>(null) }
    var started by rememberSaveable(name) { mutableStateOf(false) }

    DisposableEffect(provider, name) {
        provider.attachCardPage(name)
        onDispose {
            provider.detachCardPage(name)
            val current = provider.uiRequest.value
            if (current != null && provider.isPageHosted(current, setOf(name))) provider.dismissUi()
        }
    }
    LaunchedEffect(provider, name) {
        if (!started) {
            started = true
            provider.startCard(name) { result = it }
        }
    }
    val done = result
    val keep = shown is UiRequest.ShowTree
    LaunchedEffect(done, owned, keep) {
        if (done?.ok == true && owned == null && !keep && nav?.current == Screen.CardWidget(name, title)) nav.popBackStack()
    }

    KedgeSettingsPageScaffold(title = title.ifBlank { name }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val body = owned ?: shown?.takeIf { keep }
            MochiBlurCrossfade(
                targetState = when {
                    body != null -> PageBody.Content
                    done != null && !done.ok -> PageBody.Failed
                    else -> PageBody.Loading
                },
                modifier = Modifier.fillMaxSize(),
            ) { state ->
                when (state) {
                    PageBody.Content -> body?.let {
                        CompositionLocalProvider(LocalKhatKitSheetInline provides true) {
                            KhatKitTreeRequest(it, provider)
                        }
                    }
                    PageBody.Failed -> Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.chat_widget_open_failed),
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurface,
                        )
                        Text(
                            done?.message.orEmpty(),
                            style = MochiTheme.textStyles.footnote,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    PageBody.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        KkProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

private enum class PageBody { Loading, Content, Failed }

/** 小组件格子登记的 `mochiNavSource` key；同一个导航主机里不能和别的来源重名。 */
internal fun widgetSourceKey(name: String) = "welcome_widget:$name"
