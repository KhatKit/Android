package heizige.kk.khatkit.app.core.ui.components.ui.miuix

import androidx.compose.animation.core.Animatable
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import heizige.kk.kedge.adaptive.miuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 顶栏标题的「morph 成输入框」组件。
 *
 * 背景：Miuix 原版 [TopAppBar] 的 `title` 是 `String`，无法像 MD3
 * `TopAppBar(title = { ... })` 那样把标题替换成输入框。本组件在原版
 * `TopAppBar` **之上叠一层**覆盖标题区，按进度交叉淡化：
 *
 * - 关闭态：显示标题（可带副标题）
 * - 打开态：标题淡出，输入框淡入，位置与标题完全重合
 * - 导航槽宽度与进度同步（0→48dp），返回箭头淡入，避免占位突变导致输入框瞬移
 *
 * 进度来源与抽屉一致：以 [expanded] 为准自持 [Animatable]，
 * [dragProgress] 仅在预测返回手势进行中（[dragging]）才接管，保证跟手且不重播动画。
 *
 * @param title 关闭态标题。
 * @param subtitle 副标题（Miuix 用 `body2` 样式）。
 * @param expanded 是否处于搜索态。
 * @param keyword 当前输入。
 * @param dragProgress 预测返回手势的实时进度（非拖动时忽略）。
 * @param dragging 手势是否进行中。
 * @param onExitSearch 关闭搜索（返回箭头 / 输入完成）。
 * @param modifier 传给底层 [TopAppBar]。
 * @param navigationIcon 导航槽内容（默认无；打开态会前置返回箭头）。
 * @param actions 尾部操作。
 * @param scrollBehavior Miuix 折叠行为，用于大标题收缩。
 * @param topBarHeight 顶栏内容高度，默认 Miuix 小标题栏高度。
 */
@Composable
fun KedgeMiuixMorphingTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    expanded: Boolean,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    dragProgress: Float = 0f,
    dragging: Boolean = false,
    onExitSearch: () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior? = null,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    topBarHeight: androidx.compose.ui.unit.Dp =
        TopAppBarDefaults.SmallTopAppBarCenterHeight,
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(if (expanded) 1f else 0f, tween(220))
    }
    LaunchedEffect(dragProgress, dragging) {
        if (dragging) progress.snapTo(dragProgress.coerceIn(0f, 1f))
    }
    val p = progress.value

    // 页面级 backdrop（KedgePageScaffold 在 Miuix 下建立并下发）。
    // 模糊生效时顶栏底色必须透明，否则模糊无从透出。
    val backdrop = heizige.kk.kedge.adaptive.LocalKedgePageBackdrop.current
    val barColor = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    // 大标题折叠必须观察「本屏那个」Miuix 行为：KedgePageScaffold 建了唯一一个实例，
    // 接上 nestedScroll 并下发到这里。页面不用（也不该）自己再 remember 一个。
    val effectiveScroll = scrollBehavior
        ?: heizige.kk.kedge.adaptive.LocalKedgePageScrollBehavior.current
            ?.miuixScrollBehavior

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 底层原版 TopAppBar：负责大标题折叠动画与整体度量。
            // 必须包 KedgeBlurredBar 才有毛玻璃，否则顶栏是死板的纯色。
            heizige.kk.kedge.adaptive.KedgeBlurredBar(backdrop = backdrop) {
            TopAppBar(
                color = barColor,
                title = title,
                subtitle = subtitle.orEmpty(),
                navigationIcon = navigationIcon,
                actions = actions,
                scrollBehavior = effectiveScroll,
            )
            }

            // 覆盖层：搜索态的返回箭头 + 输入框，位置与标题区重合。
            if (p > 0.001f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarHeight)
                        .padding(start = TopAppBarDefaults.NavigationIconPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp * p)
                            .alpha(p),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p > 0.5f) {
                            KedgeIconButton(onClick = onExitSearch) {
                                Icon(
                                    imageVector = heizige.kk.khatkit.app.core.ui.icons.arrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = TopAppBarDefaults.TitlePadding)
                            .alpha(p),
                    ) {
                        if (keyword.isBlank() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = MiuixTheme.textStyles.title3.fontSize,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = keyword,
                            onValueChange = onKeywordChange,
                            singleLine = true,
                            textStyle = MiuixTheme.textStyles.title3.copy(
                                color = MiuixTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onExitSearch() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (focusRequester != null) {
                                        Modifier.focusRequester(focusRequester)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}
