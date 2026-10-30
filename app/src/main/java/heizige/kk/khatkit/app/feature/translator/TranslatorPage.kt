package heizige.kk.khatkit.app.feature.translator

import heizige.kk.khatkit.app.core.ui.components.ui.SelectTextField
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextField
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.MochiButton
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.ai.ModelSelector
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.util.getText
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.Locale
import heizige.kk.khatkit.app.core.ui.icons.contentPaste
import heizige.kk.khatkit.app.core.ui.icons.translate
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.uikit.mochi.MochiHorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.uikit.mochi.KkFloatingActionButton
import androidx.compose.foundation.layout.navigationBarsPadding
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicatorType

@Composable
fun TranslatorPage(vm: TranslatorViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val inputText by vm.inputText.collectAsStateWithLifecycle()
    val translatedText by vm.translatedText.collectAsStateWithLifecycle()
    val targetLanguage by vm.targetLanguage.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    // 处理错误
    LaunchedEffect(Unit) {
        vm.errorFlow.collect { error ->
            Toast.show(error.message ?: "错误", isError = true)
        }
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.translator_page_title),
        bottomBar = {
            BottomBar(
                translating = translating,
                onTranslate = {
                    vm.translate()
                },
                onCancelTranslation = {
                    vm.cancelTranslation()
                },
                onLanguageSelected = {
                    vm.updateTargetLanguage(it)
                },
                targetLanguage = targetLanguage
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 输入区域
            Column {
                Md3OutlinedTextFieldWithSlots(
                    value = inputText,
                    onValueChange = { vm.updateInputText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.translator_page_input_placeholder)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    shape = RoundedCornerShape(16.dp)
                )

                MochiButton(
                    onClick = {
                        scope.launch {
                            clipboard.getClipEntry()?.clipData?.getText()?.let {
                                vm.updateInputText(it)
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(contentPaste, null)
                    Text("粘贴文本", modifier = Modifier.padding(start = 4.dp))
                }
            }

            // 翻译进度条
            Crossfade(translating) { isTranslating ->
                if (isTranslating) {
                    KkProgressIndicator(
                    type = KkProgressIndicatorType.Linear,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                } else {
                    // Miuix 的分隔线颜色比 MD3 淡，直接用 MD3 会显得过重
                    MochiHorizontalDivider()
                }
            }

            // 翻译结果
            SelectionContainer {
                Text(
                    text = translatedText.ifEmpty {
                        stringResource(R.string.translator_page_result_placeholder)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            AnimatedVisibility(translatedText.isNotBlank()) {
                MochiButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        null, translatedText
                                    )
                                )
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(contentPaste, null)
                    Text("复制翻译结果", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private val Locales by lazy {
    listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.ENGLISH,
        Locale.TRADITIONAL_CHINESE,
        Locale.JAPANESE,
        Locale.KOREAN,
        Locale.FRENCH,
        Locale.GERMAN,
        Locale.ITALIAN,
        Locale("es", "ES")
    )
}

@Composable
private fun LanguageSelector(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit
) {

    @Composable
    fun getLanguageDisplayName(locale: Locale): String {
        return when (locale) {
            Locale.SIMPLIFIED_CHINESE -> stringResource(R.string.language_simplified_chinese)
            Locale.ENGLISH -> stringResource(R.string.language_english)
            Locale.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
            Locale.JAPANESE -> stringResource(R.string.language_japanese)
            Locale.KOREAN -> stringResource(R.string.language_korean)
            Locale.FRENCH -> stringResource(R.string.language_french)
            Locale.GERMAN -> stringResource(R.string.language_german)
            Locale.ITALIAN -> stringResource(R.string.language_italian)
            Locale("es", "ES") -> stringResource(R.string.language_spanish)
            // LocalConfiguration 是可观察的：语言切换后会重组（Locale.getDefault() 不会）
            else -> locale.getDisplayLanguage(LocalConfiguration.current.locales[0])
        }
    }


    Box(
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        // U3d：语言下拉改用 SelectTextField（内部即 MochiContextMenu），与其余下拉同一套菜单。
        SelectTextField(
            value = getLanguageDisplayName(targetLanguage),
            options = Locales,
            onOptionSelected = onLanguageSelected,
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            optionToString = { getLanguageDisplayName(it) },
        )
    
    }
}


/** 语言显示名，与 [LanguageSelector] 内部逻辑一致。 */
@Composable
private fun getLanguageDisplayName(locale: Locale): String {
    return when (locale) {
        Locale.SIMPLIFIED_CHINESE -> stringResource(R.string.language_simplified_chinese)
        Locale.ENGLISH -> stringResource(R.string.language_english)
        Locale.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
        Locale.JAPANESE -> stringResource(R.string.language_japanese)
        else -> locale.displayLanguage
    }
}

@Composable
private fun BottomBar(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit,
    translating: Boolean,
    onTranslate: () -> Unit,
    onCancelTranslation: () -> Unit
) {
    // 不用 MD3 BottomAppBar：它自带底色/内边距，会在 Miuix 下漏出 MD3 容器。
    // 这里自己排：左侧语言选择，右侧翻译/取消按钮，都浮在内容之上。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        LanguageSelector(
            targetLanguage = targetLanguage,
            onLanguageSelected = { onLanguageSelected(it) },
        )
        Box(
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            KkFloatingActionButton(
                onClick = {
                    if (translating) {
                        onCancelTranslation()
                    } else {
                        onTranslate()
                    }
                },
            ) {
                if (!translating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            translate,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.translator_page_translate),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } else {
                    Text(stringResource(R.string.translator_page_cancel))
                }
            }
        }
    }
}
