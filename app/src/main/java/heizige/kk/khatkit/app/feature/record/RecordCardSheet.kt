package heizige.kk.khatkit.app.feature.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.feature.automation.TriggerController
import heizige.kk.khatkit.app.core.ui.icons.adsClick
import heizige.kk.khatkit.app.core.ui.icons.stopCircle
import heizige.kk.khatkit.record.RecordingCardFactory
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

/**
 * 录制操作面板：开始/停止录制、实时步数、命名并保存为 Lua 卡片。
 *
 * 录制的采集与生成分别在 [KhatKitOperationRecorder] / [RecordCardSaver]，
 * 这里只负责交互；保存成功后刷新已安装卡片缓存（供市场页与触发器立即看到）。
 */
@Composable
fun RecordCardSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val triggerController: TriggerController = rememberAppEntryPoint().triggerController()
    val state by KhatKitOperationRecorder.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val canSave = !state.recording && state.steps.isNotEmpty()
    val saveAction: (() -> Unit)? = if (canSave) {
        {
            if (!saving) {
                if (name.isBlank()) {
                    Toast.show("请先填写卡片名称", isError = true)
                } else {
                    saving = true
                    scope.launch {
                        val result = RecordCardSaver.save(context, name.trim(), state.steps)
                        saving = false
                        if (result.ok) {
                            Toast.show("已保存卡片 ${result.cardName}")
                            KhatKitOperationRecorder.discard(context)
                            triggerController.refreshCardsAsync()
                            onSaved(result.cardName)
                            onDismiss()
                        } else {
                            Toast.show(result.message, isError = true)
                        }
                    }
                }
            }
        }
    } else {
        null
    }

    PrimaryBottomSheet(
        visible = visible,
        title = "录制人工操作",
        imageVector = adsClick,
        dismissText = "关闭",
        confirmText = if (canSave) "保存为卡片" else null,
        onConfirm = saveAction,
        onDismiss = onDismiss,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "开始录制后去手动操作手机，KhatKit 会记录点击、输入、滑动与切换应用；停止后可保存为可回放的 Lua 卡片。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = buildString {
                    append("已录制 ${state.steps.size} / ${RecordingCardFactory.MAX_STEPS} 步")
                    if (state.full) append("（已达上限，后续操作不再记录）")
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.recording) {
                Text(
                    text = "录制中：可以离开本页去操作其他应用，通知栏有「停止」按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("卡片名称（如：每日签到）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.recording) {
                    Button(onClick = { KhatKitOperationRecorder.stop(context) }, shapes = ButtonDefaults.shapes()) {
                        Icon(stopCircle, contentDescription = null)
                        Text(
                            text = "停止录制",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (!KhatKitOperationRecorder.start(context)) {
                                Toast.show("无障碍服务未开启，请先在系统设置中开启 KhatKit 的无障碍服务", isError = true)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(adsClick, contentDescription = null)
                        Text(
                            text = "开始录制",
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                if (!state.recording && state.steps.isNotEmpty()) {
                    TextButton(onClick = { KhatKitOperationRecorder.discard(context) }, shapes = ButtonDefaults.shapes()) {
                        Text("清空")
                    }
                }
            }

            if (state.steps.isNotEmpty()) {
                Text("最近步骤：", style = MaterialTheme.typography.labelMedium)
                state.steps.takeLast(5).forEach { step ->
                    Text(
                        text = "· ${step.comment()}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
