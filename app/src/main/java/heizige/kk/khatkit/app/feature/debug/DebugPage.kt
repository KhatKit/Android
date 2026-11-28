package heizige.kk.khatkit.app.feature.debug

import androidx.compose.ui.platform.LocalResources
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextField
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.MochiPageTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.common.android.Logging
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.ui.components.ui.UIAvatar
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.richtext.MarkdownBlock
import heizige.kk.khatkit.app.core.ui.components.richtext.MathBlock
import heizige.kk.khatkit.app.core.ui.components.richtext.Mermaid
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.khatkit.app.core.ui.theme.JetbrainsMono
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiTabPageScaffold
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import heizige.kk.khatkit.app.core.util.Emoji



@Composable
fun DebugPage(vm: DebugViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    // Tab 数 = titles.size：原先第 3 个 tab「Logging」的页体是 `2 -> Box {}`（空页），
    // 已按上游判定删除 —— 上游 2.5.6（447bb7e89）本身就是 `2 -> Box {}`，没有任何页体内容，
    // 且 f8141eeb7 的提交信息明写「移除原 DebugPage 中的废弃日志查看代码」并把日志查看器
    // 挪到设置页（本地 feature/log/LogPage.kt 仍在 RouteActivity 里注册，入口不受影响）。
    // 删 tab 时同步删 titles 项并把后续页索引前移，保持 pager 数与 titles 一致。
    val state = rememberPagerState { 3 }
    // Tab 页统一骨架：MD3 下保持 SecondaryTabRow，Miuix 下换原生 TabRow。
    MochiTabPageScaffold(
        title = stringResource(R.string.debug_page_title),
        titles = listOf(
            stringResource(R.string.debug_page_tab_main),
            stringResource(R.string.debug_page_tab_colors),
            stringResource(R.string.debug_page_tab_recovery),
        ),
        selectedTabIndex = state.currentPage,
        onTabSelected = { scope.launch { state.animateScrollToPage(it) } },
    ) { pagerModifier ->
        HorizontalPager(
            state = state,
            modifier = pagerModifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> MainPage(vm)
                1 -> ColorsPage()
                2 -> RecoveryPage(vm)
            }
        }
    }
}

@Composable
private fun RecoveryPage(vm: DebugViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val references by vm.conversationAssistants.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val missing = references.orEmpty().filterKeys { id -> settings.assistants.none { it.id == id } }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.debug_page_refs_title))
        Text(stringResource(R.string.debug_page_refs_scan, references?.size ?: 0, missing.size))
        MochiButton(
            onClick = {
                scope.launch {
                    val count = vm.recoverAssistantsFromConversations()
                    Toast.show(if (count == null) resources.getString(R.string.debug_page_settings_loading) else resources.getString(R.string.debug_page_recovered_count, count))
                }
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_recover_missing))
        }
        missing.entries.sortedByDescending { it.value }.forEach { (id, count) ->
            Text(stringResource(R.string.debug_page_conversation_count, id, count), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MainPage(vm: DebugViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings = LocalSettings.current
    val conversationCount by vm.conversationCount.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var avatar: Avatar by remember { mutableStateOf(Avatar.Emoji("😎")) }
        UIAvatar(
            value = avatar,
            onUpdate = {
                println("Avatar updated: $it")
                avatar = it
            },
            name = "A"
        )
        Mermaid(
            code = """
                mindmap
                  root((mindmap))
                    Origins
                      Long history
                      ::icon(fa fa-book)
                      Popularisation
                        British popular psychology author Tony Buzan
                    Research
                      On effectiveness<br/>and features
                      On Automatic creation
                        Uses
                            Creative techniques
                            Strategic planning
                            Argument mapping
                    Tools
                      Pen and paper
                      Mermaid
                """.trimIndent(),
            modifier = Modifier.fillMaxWidth(),
        )

        var counter by remember {
            mutableIntStateOf(0)
        }

        MochiButton(
            onClick = {
                Toast.show(resources.getString(R.string.debug_page_toast_test, counter++))
                Toast.show(resources.getString(R.string.debug_page_toast_test, counter++), isError = false)
                Toast.show(resources.getString(R.string.debug_page_toast_test, counter++), isError = true)
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_toast_button))
        }
        MochiButton(
            onClick = {
                vm.updateSettings(
                    settings.copy(
                        chatModelId = Uuid.random()
                    )
                )
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_reset_chat_model))
        }

        MochiButton(
            onClick = {
                error("测试崩溃 ${Random.nextInt(0..1000)}")
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_crash))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.debug_page_conversation_counter, conversationCount?.toString() ?: "..."),
                modifier = Modifier.weight(1f),
            )
            MochiButton(onClick = { vm.refreshConversationCount() }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.debug_page_refresh))
            }
        }

        MochiButton(
            onClick = {
                vm.createOversizedConversation(30)
                Toast.show(resources.getString(R.string.debug_page_creating_oversized))
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_create_oversized))
        }

        MochiButton(
            onClick = {
                vm.createConversationWithMessages(1024)
                Toast.show(resources.getString(R.string.debug_page_creating_many))
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.debug_page_create_many))
        }

        MochiHorizontalDivider()

        Text(stringResource(R.string.debug_page_launch_stats), style = MaterialTheme.typography.bodyMedium)

        var launchCountInput by remember(settings.launchCount) {
            mutableStateOf(settings.launchCount.toString())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Md3OutlinedTextFieldWithSlots(
                value = launchCountInput,
                onValueChange = { launchCountInput = it },
                label = { Text(stringResource(R.string.debug_page_launch_count_label, settings.launchCount)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            MochiButton(onClick = {
                launchCountInput.toIntOrNull()?.let {
                    vm.updateSettings(settings.copy(launchCount = it))
                }
            },
                 shapes = ButtonDefaults.shapes(),) {
                Text(stringResource(R.string.debug_page_set))
            }
        }

        var markdown by remember { mutableStateOf("") }
        MarkdownBlock(markdown, modifier = Modifier.fillMaxWidth())
        MathBlock(markdown)
        Md3OutlinedTextField(
            value = markdown,
            onValueChange = { markdown = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ColorsPage() {
    // 必须走主题色：Miuix 模式下 KedgeTheme 只包了 MiuixTheme，直读
    // MaterialTheme.colorScheme 拿到的是 Compose 兜底的亮色方案，暗色主题下
    // 这一页会白得发光。
    val coreTokens = listOf(
        "primary" to MaterialTheme.colorScheme.primary,
        "onPrimary" to MaterialTheme.colorScheme.onPrimary,
        "primaryContainer" to MaterialTheme.colorScheme.primaryContainer,
        "onPrimaryContainer" to MaterialTheme.colorScheme.onPrimaryContainer,
        "secondary" to MaterialTheme.colorScheme.secondary,
        "onSecondary" to MaterialTheme.colorScheme.onSecondary,
        "secondaryContainer" to MaterialTheme.colorScheme.secondaryContainer,
        "onSecondaryContainer" to MaterialTheme.colorScheme.onSecondaryContainer,
        "tertiary" to MaterialTheme.colorScheme.tertiary,
        "onTertiary" to MaterialTheme.colorScheme.onTertiary,
        "tertiaryContainer" to MaterialTheme.colorScheme.tertiaryContainer,
        "onTertiaryContainer" to MaterialTheme.colorScheme.onTertiaryContainer,
        "background" to MaterialTheme.colorScheme.background,
        "onBackground" to MaterialTheme.colorScheme.onBackground,
        "surface" to MaterialTheme.colorScheme.surface,
        "onSurface" to MaterialTheme.colorScheme.onSurface,
        "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
        "onSurfaceVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
        "surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
        "surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
        "surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
        "error" to MaterialTheme.colorScheme.error,
        "onError" to MaterialTheme.colorScheme.onError,
        "errorContainer" to MaterialTheme.colorScheme.errorContainer,
        "onErrorContainer" to MaterialTheme.colorScheme.onErrorContainer,
        "outline" to MaterialTheme.colorScheme.outline,
        "outlineVariant" to MaterialTheme.colorScheme.outlineVariant,
    )
    // MD3 专有 token（inverse/surfaceTint/Fixed* 等）没有 Miuix 对应项，
    // 只在 MD3Exp 列出，免得在 Miuix 下展示兜底的假色。
    val colorTokens = run {
        val colorScheme = MaterialTheme.colorScheme
        coreTokens + listOf(
            "inversePrimary" to colorScheme.inversePrimary,
            "surfaceTint" to colorScheme.surfaceTint,
            "inverseSurface" to colorScheme.inverseSurface,
            "inverseOnSurface" to colorScheme.inverseOnSurface,
            "surfaceBright" to colorScheme.surfaceBright,
            "surfaceDim" to colorScheme.surfaceDim,
            "surfaceContainerLow" to colorScheme.surfaceContainerLow,
            "surfaceContainerLowest" to colorScheme.surfaceContainerLowest,
            "scrim" to colorScheme.scrim,
            "primaryFixed" to colorScheme.primaryFixed,
            "primaryFixedDim" to colorScheme.primaryFixedDim,
            "onPrimaryFixed" to colorScheme.onPrimaryFixed,
            "onPrimaryFixedVariant" to colorScheme.onPrimaryFixedVariant,
            "secondaryFixed" to colorScheme.secondaryFixed,
            "secondaryFixedDim" to colorScheme.secondaryFixedDim,
            "onSecondaryFixed" to colorScheme.onSecondaryFixed,
            "onSecondaryFixedVariant" to colorScheme.onSecondaryFixedVariant,
            "tertiaryFixed" to colorScheme.tertiaryFixed,
            "tertiaryFixedDim" to colorScheme.tertiaryFixedDim,
            "onTertiaryFixed" to colorScheme.onTertiaryFixed,
            "onTertiaryFixedVariant" to colorScheme.onTertiaryFixedVariant,
        )
    }
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(colorTokens, key = { it.first }) { (name, color) ->
            ColorTokenItem(name, color)
        }
    }
}

@Composable
private fun ColorTokenItem(name: String, color: Color) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
        )
        Column(modifier = Modifier.weight(2f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                color.toHexString(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = JetbrainsMono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Color.toHexString(): String {
    val argb = toArgb()
    return "#%08X".format(argb)
}
