package heizige.kk.khatkit.app.feature.settings

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.refresh
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import kotlinx.coroutines.delay
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

/**
 * 关于页图标彩蛋的落点：一个不在任何菜单里的「隐藏终端」。
 *
 * 为什么不是跳到真的工作区终端：那要一个 workspace + shell 会话，彩蛋就不该先让你去建东西；
 * 这里是一块纯 UI 的假终端，但它把版本、设备、构建这些真信息用真命令吐出来，还能输入
 * help / version / mochi / egg / clear / exit —— 有来有回，才像一个彩蛋，而不是一张静态图。
 *
 * 视觉全走主题：面板是分组卡片底色压一层 5% 主色（像一块墨绿玻璃屏，但没有硬编码色值），
 * 文字用等宽字体 + Mochi 的字阶，状态色取自 colorScheme（primary / onSurfaceVariant / error）。
 */
@Composable
fun AboutTerminalPage() {
    val navController = LocalNavController.current
    var replay by remember { mutableIntStateOf(0) }

    KedgeSettingsPageScaffold(
        title = "终端",
        actions = {
            MochiIconButton(
                onClick = { replay += 1 },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = refresh,
                    contentDescription = "重播启动日志",
                    tint = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { innerPadding ->
        TerminalPanel(
            replay = replay,
            onExit = { navController.popBackStack() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = PageMetrics.TopContentPadding,
                    bottom = 16.dp,
                ),
        )
    }
}

/** 一行的语气：决定颜色，不决定字号 —— 终端里所有文字同一档等宽。 */
private enum class TermLevel { Prompt, Output, Dim, Success, Error }

private data class TermLine(val text: String, val level: TermLevel)

@Composable
private fun TerminalPanel(
    replay: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MochiTheme.colorScheme
    val scroll = rememberScrollState()
    val lines = remember { mutableStateListOf<TermLine>() }
    var input by remember { mutableStateOf("") }
    var booting by remember { mutableStateOf(true) }

    val mono = MochiTheme.textStyles.footnote.copy(
        fontFamily = FontFamily.Monospace,
        lineHeight = 19.sp,
    )

    fun color(level: TermLevel) = when (level) {
        TermLevel.Prompt -> cs.primary
        TermLevel.Output -> cs.onSurface
        TermLevel.Dim -> cs.onSurfaceVariant.copy(alpha = 0.75f)
        TermLevel.Success -> cs.primary
        TermLevel.Error -> cs.error
    }

    fun echo(text: String, level: TermLevel = TermLevel.Output) {
        lines.add(TermLine(text, level))
    }

    fun run(raw: String) {
        val command = raw.trim()
        if (command.isEmpty()) return
        echo("❯ " + command, TermLevel.Prompt)
        when (command.lowercase()) {
            "help", "?" -> {
                echo("help     列出全部命令")
                echo("version  版本与构建信息")
                echo("mochi    这次界面背后的设计语言")
                echo("egg      彩蛋本体")
                echo("whoami   你是谁")
                echo("clear    清屏")
                echo("exit     关掉终端")
            }
            "version", "ver" -> {
                echo("khatkit  " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                echo("package  " + BuildConfig.APPLICATION_ID)
                echo("abis     " + Build.SUPPORTED_ABIS.joinToString(", "))
            }
            "mochi" -> {
                echo("Mochi Compose：MD3E 的色板与字阶，iOS 26 的交互手感。", TermLevel.Success)
                echo("本页所有圆角都是连续曲率，没有一个 RoundedCornerShape。", TermLevel.Dim)
                echo("github.com/feice11/mochi-compose", TermLevel.Dim)
            }
            "egg" -> {
                echo("你已经在彩蛋里了。🍡", TermLevel.Success)
                echo("这一页没有任何入口，只有长按关于页的图标才会出现。", TermLevel.Dim)
            }
            "whoami" -> echo("彩蛋猎人（本机）", TermLevel.Success)
            "sudo" -> echo("没有 root，也不需要 root。", TermLevel.Dim)
            "clear", "cls" -> lines.clear()
            "exit", "quit", "q" -> onExit()
            else -> echo("command not found: " + command + "（输入 help 看命令）", TermLevel.Error)
        }
    }

    // 启动日志：一行一行打出来。replay 变了就清屏重放。
    LaunchedEffect(replay) {
        lines.clear()
        booting = true
        input = ""
        val script = buildBootLine()
        for (line in script) {
            lines.add(TermLine("", line.level))
            val typed = StringBuilder()
            for (char in line.text) {
                typed.append(char)
                lines[lines.lastIndex] = TermLine(typed.toString(), line.level)
                delay(if (char == ' ') 2L else 7L)
            }
            delay(80)
        }
        booting = false
    }

    // 打字/新命令都让画面贴着底部；用「行数 + 末行字数」当 key，每多一个字滚一次
    val tailKey = lines.size to (lines.lastOrNull()?.text?.length ?: 0)
    LaunchedEffect(tailKey) {
        scroll.scrollTo(scroll.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MochiTheme.shapes.extraLarge)
            // 墨绿玻璃屏：分组卡片底色上压 5% 主色，深浅色都成立，且没有硬编码色值
            .background(cs.primary.copy(alpha = 0.05f).compositeOver(MochiDefaults.groupedContainer()))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 标题栏：三个装饰点 + 会话名（终端语言，但圆点大小/间距走 Mochi 的同心关系）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(9.dp).clip(MochiTheme.shapes.full).background(cs.error.copy(alpha = 0.55f)))
            Box(Modifier.size(9.dp).clip(MochiTheme.shapes.full).background(cs.tertiary.copy(alpha = 0.55f)))
            Box(Modifier.size(9.dp).clip(MochiTheme.shapes.full).background(cs.primary.copy(alpha = 0.55f)))
            Text(
                text = "khatkit@本机 · 隐藏终端",
                style = mono.copy(fontWeight = FontWeight.Medium),
                color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .height(1.dp)
                .background(cs.onSurfaceVariant.copy(alpha = 0.12f)),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            lines.forEach { line ->
                Text(
                    text = line.text,
                    style = mono,
                    color = color(line.level),
                )
            }
            if (!booting) {
                // 光标：跟着输入行走；没有输入时是一枚常亮的方块
                TerminalPrompt(
                    input = input,
                    onInputChange = { input = it },
                    onSubmit = {
                        run(input)
                        input = ""
                    },
                    mono = mono,
                )
            } else {
                BlinkingCursor(color = cs.primary)
            }
        }
    }
}

/** 命令行：一个等宽输入框 + 行首箭头 + 发送按钮。 */
@Composable
private fun TerminalPrompt(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    mono: TextStyle,
) {
    val cs = MochiTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "❯", style = mono, color = cs.primary)
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            textStyle = mono.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) {
                        Text(
                            text = "输入命令，help 看全部",
                            style = mono,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            },
        )
        MochiIconButton(onClick = onSubmit, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = arrowForward,
                contentDescription = "执行",
                tint = if (input.isBlank()) cs.onSurfaceVariant.copy(alpha = 0.4f) else cs.primary,
            )
        }
    }
}

/** 打字时的方块光标：一秒一闪。 */
@Composable
private fun BlinkingCursor(color: Color) {
    val transition = rememberInfiniteTransition(label = "termCursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "termCursorAlpha",
    )
    Text(
        text = "▊",
        color = color.copy(alpha = alpha),
        style = MochiTheme.textStyles.footnote.copy(fontFamily = FontFamily.Monospace),
    )
}

/** 启动日志脚本：设备信息是真的，其余是彩蛋话术。 */
private fun buildBootLine(): List<TermLine> = listOf(
    TermLine("[khatkit] 唤醒隐藏终端 ……", TermLevel.Prompt),
    TermLine("kernel  : mochi-compose · 连续曲率 / 无玻璃", TermLevel.Dim),
    TermLine("surface : " + Build.MANUFACTURER + " " + Build.MODEL, TermLevel.Dim),
    TermLine("android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")", TermLevel.Dim),
    TermLine("build   : " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", TermLevel.Dim),
    TermLine("", TermLevel.Output),
    TermLine("彩蛋终端已就绪：这一页不在任何菜单里。", TermLevel.Success),
    TermLine("输入 help 看能做什么，exit 回到关于页。", TermLevel.Output),
    TermLine("", TermLevel.Output),
)
