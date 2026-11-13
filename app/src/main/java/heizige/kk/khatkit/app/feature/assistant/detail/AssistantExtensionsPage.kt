package heizige.kk.khatkit.app.feature.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import heizige.kk.khatkit.app.core.ui.components.ui.mochi.MochiTabPageScaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.ai.ExtensionEmptyState
import heizige.kk.khatkit.app.core.ui.components.ai.extensionActionSection
import heizige.kk.khatkit.app.core.ui.components.ai.lorebooksSection
import heizige.kk.khatkit.app.core.ui.components.ai.modeInjectionsSection
import heizige.kk.khatkit.app.core.ui.components.ai.quickMessagesSection
import heizige.kk.khatkit.app.core.ui.components.ai.skillsSection
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ui.feice.mochi.list.LocalMochiListItemSpacing

/** 分组之间的间距；mochiListSection 的行要跨过这段间距连成一张卡片，同值提供给 LocalMochiListItemSpacing */
private val ExtensionsSectionSpacing = 16.dp

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
    val gotoExtensions = stringResource(R.string.assistant_extensions_page_goto_extensions)
    val gotoPrompts = stringResource(R.string.assistant_extensions_page_goto_prompts)

    // Tab 页统一骨架：顶栏 + MochiTabRow；每个标签页是一个 LazyColumn，
    // 列表是一张 Mochi 分组卡片（行尾开关，点整行也切换），下面单独一组放跳转操作。
    MochiTabPageScaffold(
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
                                buttonText = gotoExtensions,
                                onAction = { navController.navigate(Screen.QuickMessages) },
                            )
                        } else {
                            ExtensionTabList {
                                quickMessagesSection(
                                    quickMessages = settings.quickMessages,
                                    selectedIds = assistant.quickMessageIds,
                                    onToggle = { quickMessageId, checked ->
                                        val newIds = if (checked) assistant.quickMessageIds + quickMessageId
                                        else assistant.quickMessageIds - quickMessageId
                                        vm.update(assistant.copy(quickMessageIds = newIds))
                                    },
                                )
                                extensionActionSection("goto", gotoExtensions) {
                                    navController.navigate(Screen.QuickMessages)
                                }
                            }
                        }
                    }

                    1 -> {
                        if (settings.modeInjections.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_mode_injections),
                                buttonText = gotoPrompts,
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            ExtensionTabList {
                                modeInjectionsSection(
                                    modeInjections = settings.modeInjections,
                                    selectedIds = assistant.modeInjectionIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.modeInjectionIds + injId
                                        else assistant.modeInjectionIds - injId
                                        vm.update(assistant.copy(modeInjectionIds = newIds))
                                    },
                                )
                                extensionActionSection("goto", gotoPrompts) {
                                    navController.navigate(Screen.Prompts)
                                }
                            }
                        }
                    }

                    2 -> {
                        if (settings.lorebooks.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_lorebooks),
                                buttonText = gotoPrompts,
                                onAction = { navController.navigate(Screen.Prompts) },
                            )
                        } else {
                            ExtensionTabList {
                                lorebooksSection(
                                    lorebooks = settings.lorebooks,
                                    selectedIds = assistant.lorebookIds,
                                    onToggle = { injId, checked ->
                                        val newIds = if (checked) assistant.lorebookIds + injId
                                        else assistant.lorebookIds - injId
                                        vm.update(assistant.copy(lorebookIds = newIds))
                                    },
                                )
                                extensionActionSection("goto", gotoPrompts) {
                                    navController.navigate(Screen.Prompts)
                                }
                            }
                        }
                    }

                    3 -> {
                        if (skills.isEmpty()) {
                            ExtensionEmptyState(
                                message = stringResource(R.string.assistant_extensions_page_empty_skills),
                                buttonText = gotoExtensions,
                                onAction = { navController.navigate(Screen.Skills) },
                            )
                        } else {
                            ExtensionTabList {
                                skillsSection(
                                    skills = skills,
                                    enabledSkills = assistant.enabledSkills,
                                    onToggle = { name, checked ->
                                        val newSkills = if (checked) assistant.enabledSkills + name
                                        else assistant.enabledSkills - name
                                        vm.update(assistant.copy(enabledSkills = newSkills))
                                    },
                                )
                                extensionActionSection("goto", gotoExtensions) {
                                    navController.navigate(Screen.Skills)
                                }
                            }
                        }
                    }
                }
            }
    }
}

/** 一个标签页的列表：留白口径和设置页一致（左右 16、上下 PageMetrics），分组之间 16dp */
@Composable
private fun ExtensionTabList(content: LazyListScope.() -> Unit) {
    CompositionLocalProvider(LocalMochiListItemSpacing provides ExtensionsSectionSpacing) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = PageMetrics.TopContentPadding,
                end = 16.dp,
                bottom = 8.dp + PageMetrics.BottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(ExtensionsSectionSpacing),
            content = content,
        )
    }
}
