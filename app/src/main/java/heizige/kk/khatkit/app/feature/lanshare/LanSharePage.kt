package heizige.kk.khatkit.app.feature.lanshare

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiSettingsPageScaffold
import ui.feice.mochi.basic.MochiActivityIndicator
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListLeadingStyle
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun LanSharePage() {
    val manager = hiltViewModel<LanShareViewModel>().manager
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) manager.select(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) manager.start() else manager.showError("未授予局域网访问权限")
    }
    val sessionUrl = state.url
    val active = sessionUrl != null
    val canEditDirectory = !active && !state.isBusy
    MochiSettingsPageScaffold(title = "局域网共享") { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MochiListSection {
                MochiListRow(
                    title = if (active) "分享中" else "未启动",
                    subtitle = if (active) "临时链接 · 30 分钟后失效" else null,
                    leading = { MochiIonicon("wifi") },
                    leadingStyle = MochiListLeadingStyle.Symbol,
                    trailing = {
                        if (state.isBusy) MochiActivityIndicator(Modifier.size(20.dp))
                        else MochiButton(
                            onClick = {
                                if (active) manager.stop()
                                else if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_LOCAL_NETWORK,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                                else manager.start()
                            },
                            style = if (active) MochiButtonStyle.Tonal else MochiButtonStyle.Filled,
                        ) { Text(if (active) "停止" else "启动") }
                    },
                )
                if (sessionUrl != null) {
                    MochiListRow(
                        title = "复制访问链接",
                        subtitle = "同一网络 · HTTP，未加密",
                        onClick = {
                            val clip = ClipData.newPlainText("局域网共享", sessionUrl)
                            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                        },
                        leading = { MochiIonicon("copy") },
                    )
                    SelectionContainer {
                        Text(sessionUrl, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 4,
                            overflow = TextOverflow.Ellipsis, style = MochiTheme.textStyles.footnote)
                    }
                }
            }
            state.error?.let { Text(it, color = MochiTheme.colorScheme.error) }
            MochiListSection {
                MochiListRow(
                    title = "共享目录",
                    subtitle = state.directory?.name?.takeIf { it.isNotBlank() } ?: "未选择目录",
                    onClick = if (canEditDirectory) ({ picker.launch(null) }) else null,
                    leading = { MochiIonicon("folder-open") },
                    trailing = {
                        if (state.directory != null && canEditDirectory) {
                            MochiIconButton(onClick = manager::removeDirectory, style = MochiButtonStyle.Text) {
                                MochiIonicon("close", contentDescription = "移除共享目录")
                            }
                        }
                    },
                )
            }
            Text("文本互传", style = MochiTheme.textStyles.titleMedium)
            MochiTextField(
                value = state.outgoing,
                onValueChange = manager::editText,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "发送到浏览器的文本",
                singleLine = false,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("收到的文本", Modifier.weight(1f), style = MochiTheme.textStyles.footnote)
                if (state.incoming.isNotEmpty()) {
                    MochiIconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("收到的文本", state.incoming))
                    }, style = MochiButtonStyle.Text) {
                        MochiIonicon("copy", contentDescription = "复制收到的文本")
                    }
                }
            }
            SelectionContainer { Text(state.incoming.ifBlank { "暂无内容" }) }
        }
    }
}
