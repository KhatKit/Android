package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormRow
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceArrow
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.kedge.components.KedgeOutlinedTextField
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.theme.KedgeTextStyles
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 提示词设置（模型页的「提示词」页签）的 Miuix 风格版。
 *
 * MD3 侧这些设置项是「一个 `CardGroup` 装若干 `item`」，Miuix 侧对应「一项一张分组卡片」，
 * 所以用 [KedgeFormCard]（`KedgeForm.kt` 的双风格表单容器，Miuix 分支就是分组卡片）
 * 承载 [PreferenceArrow] 条目 —— 不再出现 MD3 的 `CardGroup` 观感。
 *
 * 四项提示词、默认值复位、思考预算**一个不少**，只是在 Miuix 下换了呈现：
 * 条目标题直接用「翻译/标题/OCR/压缩提示词」（MD3 里这个标题是分组标题，条目本身只写「提示词」），
 * 编辑弹窗沿用双风格化的 [PrimaryBottomSheet] + `KedgeOutlinedTextField`。
 *
 * 本页不改任何共享组件，也不新增字符串资源：用到的键全部来自
 * `SettingModelPromptPage.kt`。
 */
@Composable
internal fun PromptSettingsContentMiuix(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
) {
    Column {
        PromptSettingItemMiuix(
            title = stringResource(R.string.setting_model_page_prompt_translation),
            promptDescription = stringResource(R.string.setting_model_page_translate_prompt_vars),
            promptValue = settings.translatePrompt,
            onPromptChange = { onUpdate(settings.copy(translatePrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(translatePrompt = DEFAULT_TRANSLATION_PROMPT)) },
            reasoningLevel = ReasoningLevel.fromBudgetTokens(settings.translateThinkingBudget),
            onUpdateReasoningLevel = { onUpdate(settings.copy(translateThinkingBudget = it.budgetTokens)) },
        )
        PromptSettingItemMiuix(
            title = stringResource(R.string.setting_model_page_prompt_title),
            promptDescription = stringResource(R.string.setting_model_page_suggestion_prompt_vars),
            promptValue = settings.titlePrompt,
            onPromptChange = { onUpdate(settings.copy(titlePrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(titlePrompt = DEFAULT_TITLE_PROMPT)) },
        )
        PromptSettingItemMiuix(
            title = stringResource(R.string.setting_model_page_prompt_ocr),
            promptDescription = stringResource(R.string.setting_model_page_ocr_prompt_vars),
            promptValue = settings.ocrPrompt,
            onPromptChange = { onUpdate(settings.copy(ocrPrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(ocrPrompt = DEFAULT_OCR_PROMPT)) },
        )
        PromptSettingItemMiuix(
            title = stringResource(R.string.setting_model_page_prompt_compress),
            promptDescription = stringResource(R.string.setting_model_page_compress_prompt_vars),
            promptValue = settings.compressPrompt,
            onPromptChange = { onUpdate(settings.copy(compressPrompt = it)) },
            onResetPrompt = { onUpdate(settings.copy(compressPrompt = DEFAULT_COMPRESS_PROMPT)) },
        )
    }
}

@Composable
private fun PromptSettingItemMiuix(
    title: String,
    promptDescription: String,
    promptValue: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
) {
    var showEditor by remember { mutableStateOf(false) }

    KedgeFormCard {
        PreferenceArrow(
            title = title,
            onClick = { showEditor = true },
        )
        if (reasoningLevel != null && onUpdateReasoningLevel != null) {
            // 思考预算：复用既有 ReasoningButton（它弹出的 ReasoningPicker 已双风格化，
            // 滑块走 KedgeSlider），只把外壳换成 Miuix 的表单行。
            KedgeFormRow(
                label = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                tail = {
                    ReasoningButton(
                        onlyIcon = true,
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
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = promptDescription,
                    style = KedgeTextStyles.body(),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                KedgeOutlinedTextField(
                    value = promptValue,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 15,
                    shape = RoundedCornerShape(16.dp)
                )
                KedgeTextButton(onClick = onResetPrompt) {
                    Text(stringResource(R.string.setting_model_page_reset_to_default))
                }
            }
        }
    }
}
