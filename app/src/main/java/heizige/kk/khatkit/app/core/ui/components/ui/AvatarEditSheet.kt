package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.kedge.components.KedgeButton
import heizige.kk.kedge.components.KedgeOutlinedTextFieldWithSlots
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.icons.addPhotoAlternate
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.image
import heizige.kk.khatkit.app.core.ui.icons.link
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.khatkit.app.core.ui.icons.photoCamera
import heizige.kk.khromia.layout.FullscreenPopup

/**
 * 头像编辑弹层里可以选的动作。
 *
 * 弹层**不自己执行**这些动作，只在退出动画播完后把它们交还给 [UIAvatar]：
 * 相册/相机/图标文件那几条各自要开系统窗口，若在弹层还在屏上时开，会出现两个
 * 窗口叠一帧的闪烁；所以统一「先关弹层，再开门」。
 */
enum class AvatarEditSheetAction {
    /** 相册选图（自研媒体网格选择器，选完走裁切）。 */
    PickImage,

    /** 拍照（TakePicture，拍完走裁切）。 */
    TakePhoto,

    /** 选图标文件（含 SVG；SVG 直接当图片用，不裁切）。 */
    PickIconFile,

    /** 选 emoji（复用已有的 emoji 底部弹层）。 */
    PickEmoji,

    /** 重置为默认头像（[heizige.kk.khatkit.app.core.data.model.Avatar.Dummy]），同时也就是「清除」。 */
    Reset,
}

/** 关闭弹层时要带回的东西。`Dismiss` 是纯关闭（取消/遮罩/返回），不写数据。 */
private sealed interface AvatarSheetExit {
    data object Dismiss : AvatarSheetExit
    data class Action(val action: AvatarEditSheetAction) : AvatarSheetExit
    data class SubmitUrl(val url: String) : AvatarSheetExit
}

/**
 * 进场时长：**240ms**，滑动与淡入用同一个 tween，共用这一档。
 * 合同要求 200-300ms，取靠近 Material 底部弹层常用值的一档。
 */
private const val SheetEnterMillis = 240

/** 退场时长：**220ms**，比进场略快（退场不需要被看清），同样落在 200-300ms 区间。 */
private const val SheetExitMillis = 220

/** 「选项列表 ↔ 输入 URL」两个阶段之间的小交叉淡入，不与弹层进出场混用。 */
private const val SheetStageMillis = 180

/** 遮罩最深不透明度：再深会把下层页面压死，再浅又看不出模态。 */
private const val ScrimMaxAlpha = 0.32f

/**
 * 头像编辑的底部弹层（modal bottom sheet 语义），取代原来那个无转场的 Material 对话框。
 *
 * 进出场对齐 Khromia `BottomSheet.kt` 的写法（`:234` 的 `AnimatedVisibility` +
 * `:301-306` 的 tween）：进场 = `slideInVertically` + `fadeIn`，退场 =
 * `slideOutVertically` + `fadeOut`，遮罩自己一条 `animateFloatAsState` 跟着同进退。
 *
 * 宿主用 Khromia 的 [FullscreenPopup]（挂到 Activity decorView 的 MATCH_PARENT 子组合）：
 * - 全屏宿主才装得下「从屏幕底部滑上来」的位移，`Dialog`/`Popup` 那种 WRAP_CONTENT
 *   窗口会把入场动画裁掉（`AutomationInAppHost` 的注释里记着这个坑）；
 * - 子组合继承 CompositionLocals，所以弹层里的 Kedge 组件两种风格都取得到
 *   `LocalKedgeStyle`（Miuix / MD3Exp 各走各的）。
 *
 * 状态：`urlInput` / `urlStage` / `closing` 全在本文件的 remember 里，弹层一卸载就归零，
 * 所以「关了再开」永远是干净态，没有需要手动清的临时 uri/file（旧实现在 UIAvatar 里也是
 * 普通 remember，旋转同样丢，行为不降级）。
 *
 * @param onDismissRequest 弹层（含退出动画）彻底结束后回调，调用方在这里把挂载开关置 false。
 * @param onAction 选中某条动作后、退出动画结束时的回调。
 * @param onSubmitUrl 输入了图片 URL 并确认时的回调；空串不会走到这里（确认按钮会自己变灰）。
 */
@Composable
fun AvatarEditSheet(
    onDismissRequest: () -> Unit,
    onAction: (AvatarEditSheetAction) -> Unit,
    onSubmitUrl: (String) -> Unit,
) {
    // 先以 visible=false 组合一帧，再翻成 true —— 首帧即目标态会让 AnimatedVisibility
    // 直接显示、跳过进场动画。窗口本身是 addView 上去的（无平台窗口动画），
    // 因此不存在「系统转场 + 我们的转场」两层叠加。
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visibleState.targetState = true }

    var closing by remember { mutableStateOf(false) }
    var exit by remember { mutableStateOf<AvatarSheetExit>(AvatarSheetExit.Dismiss) }
    var urlStage by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }

    // 关一次就够：连点「取消」或遮罩不重复触发，也避免和返回键抢同一次关闭。
    val requestClose: (AvatarSheetExit) -> Unit = { target ->
        if (!closing) {
            closing = true
            exit = target
            visibleState.targetState = false
        }
    }

    // 退场动画播完（isIdle 且 currentState=false）才把结果交回去：动作/写数据都发生在
    // 弹层已经不可见之后，弹层自己不再触碰任何外部状态。
    LaunchedEffect(closing, visibleState.isIdle, visibleState.currentState) {
        if (closing && visibleState.isIdle && !visibleState.currentState) {
            when (val target = exit) {
                is AvatarSheetExit.Action -> onAction(target.action)
                is AvatarSheetExit.SubmitUrl -> onSubmitUrl(target.url)
                AvatarSheetExit.Dismiss -> Unit
            }
            onDismissRequest()
        }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visibleState.targetState) ScrimMaxAlpha else 0f,
        animationSpec = tween(
            durationMillis = if (visibleState.targetState) SheetEnterMillis else SheetExitMillis,
            easing = if (visibleState.targetState) FastOutSlowInEasing else FastOutLinearInEasing,
        ),
        label = "avatarEditSheetScrim",
    )

    FullscreenPopup(
        modifier = Modifier.fillMaxSize(),
        // 返回键：FullscreenPopup 自己的 key 分发（窗口 requestFocus 后收 BACK），
        // 不用再叠一层 BackHandler，否则一次返回会关两次。
        onDismiss = { requestClose(AvatarSheetExit.Dismiss) },
    ) {
        val scrimInteraction = remember { MutableInteractionSource() }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = { requestClose(AvatarSheetExit.Dismiss) },
                    ),
            )
            AnimatedVisibility(
                visibleState = visibleState,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                    // 从自身高度处升到 0：起点整块在窗口下方（宿主全屏，位移不被裁）
                    initialOffsetY = { it },
                ) + fadeIn(
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                ),
                exit = slideOutVertically(
                    animationSpec = tween(SheetExitMillis, easing = FastOutLinearInEasing),
                    targetOffsetY = { it },
                ) + fadeOut(
                    animationSpec = tween(SheetExitMillis, easing = FastOutLinearInEasing),
                ),
            ) {
                KedgeSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp, bottom = 16.dp),
                    ) {
                        // 拖拽把手（纯视觉：本弹层不做跟手拖拽，只保证看起来是 sheet）
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 12.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ),
                        )
                        Crossfade(
                            targetState = urlStage,
                            animationSpec = tween(SheetStageMillis),
                            label = "avatarEditSheetStage",
                        ) { showUrlInput ->
                            if (showUrlInput) {
                                UrlInputStage(
                                    urlInput = urlInput,
                                    onUrlInputChange = { urlInput = it },
                                    onConfirm = {
                                        if (urlInput.isNotBlank()) {
                                            requestClose(AvatarSheetExit.SubmitUrl(urlInput.trim()))
                                        }
                                    },
                                    onCancel = { requestClose(AvatarSheetExit.Dismiss) },
                                )
                            } else {
                                ActionListStage(
                                    onPick = { action ->
                                        requestClose(AvatarSheetExit.Action(action))
                                    },
                                    onInputUrl = { urlStage = true },
                                    onCancel = { requestClose(AvatarSheetExit.Dismiss) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 选项列表：**全部用 [CardGroup] 的 option item 行**（内部即 `KedgeOptionItem`，
 * 按 index/count 给圆角），不手搓 Row，两种风格的行观感与设置页一致。
 */
@Composable
private fun ActionListStage(
    onPick: (AvatarEditSheetAction) -> Unit,
    onInputUrl: () -> Unit,
    onCancel: () -> Unit,
) {
    // stringResource 不能进 CardGroup 的收集期（那是普通 lambda，不是 @Composable），先取出来
    val title = stringResource(R.string.avatar_change_avatar)
    val pickImageLabel = stringResource(R.string.avatar_pick_image)
    val pickEmojiLabel = stringResource(R.string.avatar_pick_emoji)
    val inputUrlLabel = stringResource(R.string.avatar_input_url)
    val resetLabel = stringResource(R.string.avatar_reset)
    val takePhotoLabel = stringResource(R.string.avatar_take_photo)
    val pickIconFileLabel = stringResource(R.string.avatar_pick_icon_file)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        CardGroup {
            OptionRow(
                icon = image,
                title = pickImageLabel,
                onClick = { onPick(AvatarEditSheetAction.PickImage) },
            )
            OptionRow(
                icon = photoCamera,
                title = takePhotoLabel,
                onClick = { onPick(AvatarEditSheetAction.TakePhoto) },
            )
            OptionRow(
                icon = addPhotoAlternate,
                title = pickIconFileLabel,
                onClick = { onPick(AvatarEditSheetAction.PickIconFile) },
            )
            OptionRow(
                icon = palette,
                title = pickEmojiLabel,
                onClick = { onPick(AvatarEditSheetAction.PickEmoji) },
            )
            OptionRow(
                icon = link,
                title = inputUrlLabel,
                onClick = onInputUrl,
            )
            // 重置 = 清除：Avatar.Dummy 会把图片/emoji 都清掉，回到按角色名取色的默认头像
            OptionRow(
                icon = delete,
                title = resetLabel,
                onClick = { onPick(AvatarEditSheetAction.Reset) },
            )
        }
        KedgeTextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.avatar_cancel))
        }
    }
}

/** 输入图片 URL：原来是第二个 AlertDialog，现在收进同一个弹层里换个阶段，少一次硬切换。 */
@Composable
private fun UrlInputStage(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.avatar_url_dialog_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
        KedgeOutlinedTextFieldWithSlots(
            value = urlInput,
            onValueChange = onUrlInputChange,
            label = { Text(stringResource(R.string.avatar_url_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        // 空串无处可去：按钮自己变灰（旧实现是点了没反应，结果等价、这里更明确）
        KedgeButton(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = urlInput.isNotBlank(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.avatar_url_confirm))
        }
        KedgeTextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.avatar_cancel))
        }
    }
}

/**
 * 一条选项行：图标 + 标题，位号圆角与组内间距由 [CardGroup] 给（内部即 `KedgeOptionItem`），
 * 这里不手搓 Row —— 两种风格的行的内边距/最小高度/圆角都跟着设置页那套走。
 */
// 注意：CardGroup 的 DSL 是普通 lambda（收集期），这里不能标 @Composable，
// 图标/标题都在各自的 @Composable 槽里画。
private fun CardGroupScope.OptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    item(
        onClick = onClick,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
    )
}
