package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.ai.core.ReasoningLevel
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_OCR_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_TITLE_PROMPT
import heizige.kk.khatkit.app.core.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import heizige.kk.khatkit.app.core.data.datastore.Settings
import heizige.kk.khatkit.app.core.ui.components.ai.ReasoningButton
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.icons.arrowForward
import heizige.kk.khatkit.app.core.ui.icons.editNote

@Composable
internal fun PromptSettingsPage(settings: Settings, vm: SettingViewModel, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PromptSettingsContent(settings = settings, onUpdate = vm::updateSettings)
        }
    }
}

/**
 * 提示词设置项集合：MD3 与 Miuix 两份页面共用，避免两处各写一遍导致漏项或数值漂移。
 */
@Composable
internal fun PromptSettingsContent(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PromptSettingItem(
            title = stringResource(R.string.setting_model_page_prompt_translation),
            promptDescription = stringResource(R.string.setting_model_page_translate_prompt_vars),
            promptValue = settings.translatePrompt,
            onPromptChange = { onUpdate(settings.copy(translatePrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(translatePrompt = DEFAULT_TRANSLATION_PROMPT)) },
            reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
            onUpdateReasoningLevel = { onUpdate(settings.copy(translateThinkingBudget = it.budgetTokens)) },
        )
        PromptSettingItem(
            title = stringResource(R.string.setting_model_page_prompt_title),
            promptDescription = stringResource(R.string.setting_model_page_suggestion_prompt_vars),
            promptValue = settings.titlePrompt,
            onPromptChange = { onUpdate(settings.copy(titlePrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(titlePrompt = DEFAULT_TITLE_PROMPT)) },
        )
        PromptSettingItem(
            title = stringResource(R.string.setting_model_page_prompt_ocr),
            promptDescription = stringResource(R.string.setting_model_page_ocr_prompt_vars),
            promptValue = settings.ocrPrompt,
            onPromptChange = { onUpdate(settings.copy(ocrPrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(ocrPrompt = DEFAULT_OCR_PROMPT)) },
        )
        PromptSettingItem(
            title = stringResource(R.string.setting_model_page_prompt_compress),
            promptDescription = stringResource(R.string.setting_model_page_compress_prompt_vars),
            promptValue = settings.compressPrompt,
            onPromptChange = { onUpdate(settings.copy(compressPrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(compressPrompt = DEFAULT_COMPRESS_PROMPT)) },
        )
    }
}

@Composable
private fun PromptSettingItem(
    title: String,
    promptDescription: String,
    promptValue: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
) {
    var showEditor by remember { mutableStateOf(false) }

    CardGroup(title = { Text(title) }) {
        item(
            onClick = { showEditor = true },
            headlineContent = { Text(stringResource(R.string.setting_model_page_prompt)) },
            trailingContent = {
                Icon(
                    arrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        if (reasoningLevel != null && onUpdateReasoningLevel != null) {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                trailingContent = {
                    ReasoningButton(
                        reasoningLevel = reasoningLevel,
                        onUpdateReasoningLevel = onUpdateReasoningLevel,
                    )
                },
            )
        }
    }

    if (showEditor) {
        PrimaryBottomSheet(
            visible = true,
            title = title,
            imageVector = editNote,
            onDismiss = { showEditor = false },
            scrollable = false,
        ) { _ ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = promptDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KedgeOutlinedTextField(
                    value = promptValue,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 15,
                    shape = RoundedCornerShape(16.dp)
                )
                KedgeTextButton(onClick = onResetPrompt, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.setting_model_page_reset_to_default))
                }
            }
        }
    }
}
