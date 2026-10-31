package heizige.kk.khatkit.app.core.ui.components.ai

import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ui.OutlinedNumberInput
import heizige.kk.khatkit.app.core.ui.components.ui.RabbitLoadingIndicator
import heizige.kk.khatkit.uikit.mochi.KkSingleChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.KkMultiChoiceSegmentedRow
import heizige.kk.khatkit.uikit.mochi.SegmentedItem
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole

@Composable
fun CompressContextDialog(
    onDismiss: () -> Unit,
    onConfirm: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job
) {
    var additionalPrompt by remember { mutableStateOf("") }
    var selectedTokens by remember { mutableIntStateOf(2000) }
    var keepRecentMessages by remember { mutableIntStateOf(32) }
    val tokenOptions = listOf(500, 1000, 2000, 4000)
    var currentJob by remember { mutableStateOf<Job?>(null) }
    // 「加载中点取消」只停任务、不关对话框（MochiDialog 点按钮后必调 onDismissRequest）
    var justCancelled by remember { mutableStateOf(false) }
    val isLoading = currentJob?.isActive == true

    // Monitor job completion
    LaunchedEffect(currentJob) {
        currentJob?.join()
        if (currentJob?.isCompleted == true && currentJob?.isCancelled == false) {
            onDismiss()
        }
        currentJob = null
    }

    // DG1：material3 AlertDialog → MochiDialog。压缩期间（isLoading）不关：点外面/返回都被拦下，
    // 任务结束后由上面的 LaunchedEffect 收口 onDismiss
    MochiDialog(
        visible = true,
        onDismissRequest = {
            if (justCancelled) {
                justCancelled = false
            } else if (currentJob?.isActive != true) {
                onDismiss()
            }
        },
        title = stringResource(R.string.chat_page_compress_context_title),
        actions = if (isLoading) {
            persistentListOf(
                MochiDialogAction(
                    label = stringResource(R.string.cancel),
                    role = MochiDialogActionRole.Cancel,
                    onClick = {
                        currentJob?.cancel()
                        currentJob = null
                        justCancelled = true
                    },
                ),
            )
        } else {
            persistentListOf(
                MochiDialogAction(
                    label = stringResource(R.string.cancel),
                    role = MochiDialogActionRole.Cancel,
                ),
                MochiDialogAction(
                    label = stringResource(R.string.confirm),
                    role = MochiDialogActionRole.Primary,
                    onClick = {
                        currentJob = onConfirm(additionalPrompt, selectedTokens, keepRecentMessages)
                    },
                ),
            )
        },
        dismissOnOutsideTap = true,
    ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    // Loading state
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RabbitLoadingIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_page_compressing))
                    }
                } else {
                    Text(stringResource(R.string.chat_page_compress_context_desc))

                    // Token size selector
                    Text(
                        text = stringResource(R.string.chat_page_compress_target_tokens),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    KkSingleChoiceSegmentedRow(
                        modifier = Modifier.fillMaxWidth(),
                        items = tokenOptions.map { tokens ->
                            SegmentedItem(
                                label = "$tokens",
                                selected = selectedTokens == tokens,
                                onClick = { selectedTokens = tokens },
                            )
                        },
                    )

                    // Keep recent messages input
                    OutlinedNumberInput(
                        value = keepRecentMessages,
                        onValueChange = { keepRecentMessages = it },
                        label = stringResource(R.string.chat_page_compress_keep_recent),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Additional context input
                    Md3OutlinedTextFieldWithSlots(
                        value = additionalPrompt,
                        onValueChange = { additionalPrompt = it },
                        label = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt))
                        },
                        placeholder = {
                            Text(stringResource(R.string.chat_page_compress_additional_prompt_hint))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp)
                    )

                    // Warning text
                    Text(
                        text = stringResource(R.string.chat_page_compress_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
    }
}
