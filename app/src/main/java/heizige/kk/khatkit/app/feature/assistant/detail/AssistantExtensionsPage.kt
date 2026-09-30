package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeTabPageScaffold
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ai.ExtensionEmptyState
import heizige.kk.khatkit.app.core.ui.components.ai.LorebooksContent
import heizige.kk.khatkit.app.core.ui.components.ai.ModeInjectionsContent
import heizige.kk.khatkit.app.core.ui.components.ai.QuickMessagesContent
import heizige.kk.khatkit.app.core.ui.components.ai.SkillsContent
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun AssistantExtensionsPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 4 }

    // Tab 页统一骨架：MD3 下 KedgePageScaffold + SecondaryTabRow（原行为），
    // Miuix 下 Miuix Scaffold + Miuix 顶栏 + KedgeTabRow（原生 TabRow/WithContour）。
    KedgeTabPageScaffold(
        title = stringResource(R.string.assistant_extensions_page_title),
        titles = listOf(
            stringResource(R.string.assistant_extensions_page_tab_quick_messages),
            stringResource(R.string.assistant_extensions_page_tab_mode_injections),
            stringResource(R.string.assistant_extensions_page_tab_lorebooks),
            stringResource(R.string.assistant_extensions_page_tab_skills),
        ),
        selectedTabIndex = pagerState.currentPage,
        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
        scrollBehavior = scrollBehavior,
    ) { pagerModifier ->

            HorizontalPager(
                state = pagerState,
                modifier = pagerModifier.fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> {
                        if (settings.quickMessages.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_quick_messages),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                                onAction = { navController.navigate(Screen.QuickMessages) },
                            )
                        } else {
                            Column {
                                QuickMessagesContent(
                                    modifier = Modifier.weight(1f),
                                    quickMessages = settings.quickMessages,
                                    selectedIds = assistant.quickMessageIds,
                                    onToggle = { quickMessageId, checked ->
                                        val newIds = if (checked) assistant.quickMessageIds + quickMessageId
                                        else assistant.quickMessageIds - quickMessageId
                                        vm.update(assistant.copy(quickMessageIds = newIds))
                                    },
                                )
                                KedgeTextButton(
                                    onClick = { navController.navigate(Screen.QuickMessages) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_extensions))
                                }
                            }
                        }
                    }

                    1 -> {
                        if (settings.modeInjections.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_mode_injections),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                ModeInjectionsContent(
                                    modifier = Modifier.weight(1f),
                                    modeInjections = settings.modeInjections,
                                    selectedIds = assistant.modeInjectionIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.modeInjectionIds + injId
                                        else assistant.modeInjectionIds - injId
                                        vm.update(assistant.copy(modeInjectionIds = newIds))
                                    },
                                )
                                KedgeTextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    2 -> {
                        if (settings.lorebooks.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_lorebooks),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_prompts),
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            Column {
                                LorebooksContent(
                                    modifier = Modifier.weight(1f),
                                    lorebooks = settings.lorebooks,
                                    selectedIds = assistant.lorebookIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.lorebookIds + injId
                                        else assistant.lorebookIds - injId
                                        vm.update(assistant.copy(lorebookIds = newIds))
                                    },
                                )
                                KedgeTextButton(
                                    onClick = { navController.navigate(Screen.Prompts) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_prompts))
                                }
                            }
                        }
                    }

                    3 -> {
                        if (skills.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_skills),
                                buttonText = stringResource(R.string.assistant_extensions_page_goto_extensions),
                                onAction = { navController.navigate(Screen.Skills) },
                            )
                        } else {
                            Column {
                                SkillsContent(
                                    modifier = Modifier.weight(1f),
                                    skills = skills,
                                    enabledSkills = assistant.enabledSkills,
                                    onToggle = { name, checked ->
                                        val newSkills = if (checked) assistant.enabledSkills + name
                                        else assistant.enabledSkills - name
                                        vm.update(assistant.copy(enabledSkills = newSkills))
                                    },
                                )
                                KedgeTextButton(
                                    onClick = { navController.navigate(Screen.Skills) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.assistant_extensions_page_goto_extensions))
                                }
                            }
                        }
                    }
                }
            }
    }
}
