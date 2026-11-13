package heizige.kk.khatkit.app.feature.chat

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import heizige.kk.khatkit.app.core.ui.components.khatkit.CardPageStackPage
import heizige.kk.khatkit.app.core.ui.components.khatkit.KhatKitTreeRequest
import heizige.kk.khatkit.app.core.ui.components.khatkit.isCardPage
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import heizige.kk.khatkit.ui.UiRequest
import heizige.kk.khatkit.ui.UiSheetOptions
import heizige.kk.khatkit.uikit.CardRunResult
import heizige.kk.khatkit.uikit.LocalKhatKitPageInline
import heizige.kk.khatkit.uikit.LocalKhatKitPageRegisterScrollProgress
import heizige.kk.khatkit.uikit.UiIconVectors
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.theme.MochiTheme

/**
 * 欢迎页小组件点开的卡片整页。页面从小组件那一格长出来（Mochi 来源容器变形，
 * 来源 key 见 [widgetSourceKey]），进来就以用户身份运行卡片，卡片的节点树请求
 * （ui.screen / ui.form / ui.show）铺在页里；确认框使用 Mochi 对话框，其他交互请求继续进入卡片页面。
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
            Log.i("KKCardPage", "start card=$name")
            provider.startCard(name) { r ->
                Log.i("KKCardPage", "done ok=${r.ok} msg=${r.message}")
                result = r
            }
        }
    }
    val done = result
    val keep = shown is UiRequest.ShowTree
    LaunchedEffect(done, owned, keep) {
        Log.i("KKCardPage", "effect done=${done?.ok} owned=${owned?.javaClass?.simpleName} keep=$keep shown=${shown?.javaClass?.simpleName}")
        if (done?.ok == true && owned == null && !keep && nav?.current == Screen.CardWidget(name, title)) nav.popBackStack()
    }

    // 卡片整页（全屏分页页 / push 叠层）：不再当弹层弹，直接铺成一张真正的页面，
    // 页面里跑卡片自己的导航栈（今日页 → 设置页逐层压/退）。
    val cardPage = ((owned ?: shown) as? UiRequest.Screen)?.isCardPage() == true
    if (cardPage) {
        // 卡片在算下一屏（手上没有待应答的请求）时只叠一个转圈，**不盖掉**当前这层：
        // 慢的时候用户看到的还是原来的页面，而不是白屏。等 160ms 才出现，避开请求队列的瞬时空档。
        var waiting by remember { mutableStateOf(false) }
        LaunchedEffect(owned, done) {
            if (owned == null && done == null) {
                kotlinx.coroutines.delay(160)
                waiting = true
            } else {
                waiting = false
            }
        }
        Box(Modifier.fillMaxSize()) {
            CardPageStackPage(request = owned, provider = provider)
            val loading = shown == null && done == null
            val failed = done != null && !done.ok
            if (waiting && !loading && !failed) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KkProgressIndicator(Modifier.size(28.dp))
                }
            }
            if (loading || failed) {
                Box(Modifier.fillMaxSize().background(MochiTheme.colorScheme.background)) {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            KkProgressIndicator(Modifier.size(28.dp))
                        }
                    } else {
                        Column(
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
                    }
                }
            }
        }
        return
    }

    // 顶层骨架不自己滚（正文是卡片自己的滚动容器），所以标题进度由正文注册进来：
    // KhatKitPage 在内联模式下把这一页的滚动进度（State）交给这里，骨架顶栏按它淡入标题。
    // 持有 State 而不是缓存的数值：读 .value 时才会订阅，滚动才推得动顶栏。
    val body = owned ?: shown?.takeIf { keep }
    // 保留上一屏的外观配置覆盖脚本计算中的短暂空档，加载指示器出现时顶栏不跳回默认样式。
    val chrome = owned ?: shown
    var cardTitleProgress by remember { mutableStateOf<State<Float>?>(null) }
    val requestOptions = when (val request = chrome) {
        is UiRequest.Form -> request.options
        is UiRequest.FormTree -> request.options
        is UiRequest.Screen -> request.options
        is UiRequest.Show -> request.options
        is UiRequest.ShowTree -> request.options
        is UiRequest.Sheet -> request.options
        is UiRequest.Confirm -> UiSheetOptions.DEFAULT
        null -> UiSheetOptions.DEFAULT
    }
    val requestTitle = when (val request = chrome) {
        is UiRequest.Form -> request.title
        is UiRequest.FormTree -> request.title
        is UiRequest.Screen -> request.title
        is UiRequest.ShowTree -> request.title
        is UiRequest.Show -> request.card["title"]?.toString().orEmpty()
        is UiRequest.Sheet -> request.title
        is UiRequest.Confirm -> request.title
        null -> ""
    }.ifBlank { title.ifBlank { name } }
    val screenRequest = owned as? UiRequest.Screen
    MochiSettingsPageScaffold(
        title = requestTitle,
        largeTitle = requestOptions.largeTopBar,
        showTopBar = !requestOptions.hideTopBar,
        titleProgress = {
            cardTitleProgress?.value ?: 0f
        },
        actions = {
            requestOptions.topActions.forEach { action ->
                val icon = UiIconVectors.vector(action.icon ?: "more_vert")
                    ?: UiIconVectors.vector("more_vert")!!
                MochiBarIconButton(
                    onClick = {
                        if (screenRequest != null) {
                            provider.submitScreen(action.event, emptyMap())
                        }
                    },
                    enabled = screenRequest != null,
                    navId = "card.action.${action.event}",
                    contentKey = action.icon ?: action.event,
                ) {
                    Icon(icon, contentDescription = action.label)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MochiBlurCrossfade(
                targetState = when {
                    body != null -> PageBody.Content
                    done != null && !done.ok -> PageBody.Failed
                    else -> PageBody.Loading
                },
                modifier = Modifier.fillMaxSize(),
            ) { state ->
                when (state) {
                    PageBody.Content -> if (body != null) {
                        CompositionLocalProvider(
                            LocalKhatKitPageInline provides true,
                            LocalKhatKitPageRegisterScrollProgress provides { reader ->
                                cardTitleProgress = reader
                            },
                        ) {
                            KhatKitTreeRequest(body, provider)
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
