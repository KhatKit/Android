package heizige.kk.khatkit.app.feature.assistant.detail

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionInfo
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.hasUsageStatsPermission
import heizige.kk.khatkit.app.core.util.openUsageAccessSettings
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.kedge.adaptive.KedgePageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.PreferenceSwitch
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeFormCard
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.kedge.theme.KedgeStyle
import heizige.kk.kedge.theme.LocalKedgeStyle

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (LocalKedgeStyle.current == KedgeStyle.Miuix) {
        KedgeSettingsPageScaffold(
            title = stringResource(R.string.assistant_page_tab_local_tools),
            navigationIcon = { BackButton() },
        ) { innerPadding ->
            AssistantLocalToolContent(
                // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
                // 旧写法硬编码 PaddingValues(horizontal = 16.dp)（因为 MiuixSettingsPage
                // 自己用 LazyColumn 的 contentPadding 处理了顶栏），改成内容直接透传后
                // 顶部就是 0，content 直接压在 TopAppBar 下面。
                innerPadding = innerPadding,
                assistant = assistant,
                onUpdate = { vm.update(it) },
            )
        }
        return
    }

    KedgePageScaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.assistant_page_tab_local_tools),
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.pageContainerColor,
    
        md3ScrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantLocalToolContent(
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val permissionRequiredText =
        stringResource(R.string.assistant_page_local_tools_screen_time_permission_required)

    val calendarPermissionState = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.READ_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_read)) },
                usage = { Text(stringResource(R.string.permission_calendar_read_desc)) },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.WRITE_CALENDAR,
                displayName = { Text(stringResource(R.string.permission_calendar_write)) },
                usage = { Text(stringResource(R.string.permission_calendar_write_desc)) },
                required = true
            ),
        )
    )
    PermissionManager(permissionState = calendarPermissionState)

    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        if (enabled && option == LocalToolOption.ScreenTime && !context.hasUsageStatsPermission()) {
            Toast.show(message = permissionRequiredText, isError = false)
            context.openUsageAccessSettings()
        }
        if (enabled && option == LocalToolOption.Calendar && !calendarPermissionState.allPermissionsGranted) {
            calendarPermissionState.requestPermissions()
            return
        }
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        KedgeFormCard {
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
                summary = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
                checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_time_info_title),
                summary = stringResource(R.string.assistant_page_local_tools_time_info_desc),
                checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_clipboard_title),
                summary = stringResource(R.string.assistant_page_local_tools_clipboard_desc),
                checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_tts_title),
                summary = stringResource(R.string.assistant_page_local_tools_tts_desc),
                checked = assistant.localTools.contains(LocalToolOption.Tts),
                onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_ask_user_title),
                summary = stringResource(R.string.assistant_page_local_tools_ask_user_desc),
                checked = assistant.localTools.contains(LocalToolOption.AskUser),
                onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_screen_time_title),
                summary = stringResource(R.string.assistant_page_local_tools_screen_time_desc),
                checked = assistant.localTools.contains(LocalToolOption.ScreenTime),
                onCheckedChange = { toggleLocalTool(LocalToolOption.ScreenTime, it) },
            )
            PreferenceSwitch(
                title = stringResource(R.string.assistant_page_local_tools_calendar_title),
                summary = stringResource(R.string.assistant_page_local_tools_calendar_desc),
                checked = assistant.localTools.contains(LocalToolOption.Calendar),
                onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) },
            )
        }
    }
}
