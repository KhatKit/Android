package heizige.kk.khatkit.app.feature.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.app.core.ui.icons.checkCircle
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.app.core.util.UpdateCheckResponse
import heizige.kk.khatkit.uikit.mochi.kkPressState
import ui.feice.mochi.basic.MochiProgressRing
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.mochiBounce
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/** 检查更新这一件事的状态机：空闲 / 检查中 / 已最新 / 有新版本 / 失败。 */
internal sealed interface AboutUpdateState {
    data object Idle : AboutUpdateState
    data object Checking : AboutUpdateState
    data object UpToDate : AboutUpdateState
    data class Available(val info: UpdateCheckResponse) : AboutUpdateState
    data class Failed(val message: String) : AboutUpdateState
}

/**
 * 「检查更新」独立卡片（不再塞在「资源」分组里当一行）。
 *
 * 三个理由让它单独成一块：
 * 1. 它是页面上唯一有**状态机**的东西 —— 空闲、检查中、已最新、有新版本、失败，五种样子；
 *    埋在分组卡里时，状态只能靠副标题一行小字表达，看不出来。
 * 2. 它要能承载「有新版本」这种**需要行动的**结果（点开更新说明、下载），一行行高不够。
 * 3. 分组是「点进去/打开外链」的集合，混一个「执行检查」的按钮进去，语义不一致。
 *
 * 整张卡是一个点击区（不再嵌套按钮）：空闲/失败点了检查，有新版本点了看说明。
 * 右侧那枚胶囊只是视觉上的行动暗示，跟卡片共用一次点击，避免「点按钮无反应、点旁边才有」。
 */
@Composable
internal fun AboutUpdateCard(
    state: AboutUpdateState,
    onCheck: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val available = state as? AboutUpdateState.Available
    val checking = state is AboutUpdateState.Checking
    // 有新版本时卡片透一层主色（8% 压在分组卡片底色上），不画描边 —— Mochi 不用框线表达状态
    val container = if (available != null) {
        cs.primary.copy(alpha = 0.08f).compositeOver(MochiDefaults.groupedContainer())
    } else {
        MochiDefaults.groupedContainer()
    }

    // 检查中图标转圈；出结果时图标弹一下（状态变了才弹，靠 key 变化触发）
    val spin = rememberInfiniteTransition(label = "aboutUpdateSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "aboutUpdateSpinAngle",
    )
    val bounceKey = when (state) {
        AboutUpdateState.Idle -> "idle"
        AboutUpdateState.Checking -> "checking"
        AboutUpdateState.UpToDate -> "done"
        is AboutUpdateState.Available -> "new"
        is AboutUpdateState.Failed -> "failed"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MochiTheme.shapes.extraLarge)
            .background(container)
            .kkPressState(interaction, enabled = !checking, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !checking,
            ) {
                if (available != null) onOpenDetail() else onCheck()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 40dp 图标块：比行内 28dp 大一号，是卡片头不是列表行
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MochiRoundedRectangle(13.dp))
                    .background(cs.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon(state),
                    contentDescription = null,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer { rotationZ = if (checking) angle else 0f }
                        .mochiBounce(bounceKey, depth = 0.16f),
                    tint = cs.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MochiBlurText(
                    text = title(state),
                    style = MochiTheme.textStyles.navTitle,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MochiBlurText(
                    text = supporting(state),
                    style = MochiTheme.textStyles.footnote,
                    color = if (state is AboutUpdateState.Failed) cs.error else cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (state) {
                AboutUpdateState.Checking -> MochiProgressRing(
                    progress = null,
                    size = 22.dp,
                    strokeWidth = 2.5.dp,
                )
                AboutUpdateState.UpToDate -> Icon(
                    imageVector = checkCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .mochiBounce(bounceKey, depth = 0.24f),
                    tint = cs.primary,
                )
                is AboutUpdateState.Available -> UpdateCapsule(text = "更新", filled = true)
                is AboutUpdateState.Failed -> UpdateCapsule(text = "重试", filled = false)
                AboutUpdateState.Idle -> UpdateCapsule(text = "检查", filled = false)
            }
        }
        if (available != null) {
            Text(
                text = "点开查看版本说明并下载",
                style = MochiTheme.textStyles.footnote,
                color = cs.primary,
                modifier = Modifier.padding(start = 54.dp),
            )
        }
    }
}

private fun leadingIcon(state: AboutUpdateState): ImageVector = when (state) {
    AboutUpdateState.Idle -> refresh
    AboutUpdateState.Checking -> refresh
    AboutUpdateState.UpToDate -> checkCircle
    is AboutUpdateState.Available -> download
    is AboutUpdateState.Failed -> warning
}

private fun title(state: AboutUpdateState): String = when (state) {
    AboutUpdateState.Idle -> "检查更新"
    AboutUpdateState.Checking -> "正在检查更新"
    AboutUpdateState.UpToDate -> "已是最新版本"
    is AboutUpdateState.Available -> "发现新版本 " + state.info.latestVersion
    is AboutUpdateState.Failed -> "检查更新失败"
}

private fun supporting(state: AboutUpdateState): String = when (state) {
    AboutUpdateState.Idle -> "查看是否有可安装的新版本"
    AboutUpdateState.Checking -> "正在获取版本信息……"
    AboutUpdateState.UpToDate -> "当前版本已经是最新，无需更新"
    is AboutUpdateState.Available -> state.info.description.lineSequence().firstOrNull { it.isNotBlank() }
        ?.take(80) ?: "有新版本可以更新"
    is AboutUpdateState.Failed -> state.message
}

/** 右侧行动胶囊：视觉暗示，点击归整张卡。 */
@Composable
private fun UpdateCapsule(text: String, filled: Boolean) {
    val cs = MochiTheme.colorScheme
    Text(
        text = text,
        style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = if (filled) cs.onPrimary else cs.primary,
        modifier = Modifier
            .clip(MochiTheme.shapes.full)
            .background(if (filled) cs.primary else cs.primary.copy(alpha = 0.14f))
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}
