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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.kedge.components.KedgeSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.tools.local.LocalToolOption
import heizige.kk.khatkit.app.core.data.model.Assistant
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionInfo
import heizige.kk.khatkit.app.core.ui.components.ui.permission.PermissionManager
import heizige.kk.khatkit.app.core.ui.components.ui.permission.rememberPermissionState
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.util.hasUsageStatsPermission
import heizige.kk.khatkit.app.core.util.openUsageAccessSettings
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.components.ui.CardGroup
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailViewModel = hiltViewModel<AssistantDetailViewModel, AssistantDetailViewModel.Factory>(creationCallback = { it.create(id) })
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.assistant_page_tab_local_tools),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        AssistantLocalToolContent(
            // 必须把 KedgeSettingsPageScaffold 给的 innerPadding 透传下去。
            innerPadding = innerPadding,
            assistant = assistant,
            onUpdate = { vm.update(it) },
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
        CardGroup {
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.JavascriptEngine,
                        !assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_time_info_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_time_info_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.TimeInfo,
                        !assistant.localTools.contains(LocalToolOption.TimeInfo),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_clipboard_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_clipboard_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.Clipboard,
                        !assistant.localTools.contains(LocalToolOption.Clipboard),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_tts_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_tts_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.Tts),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.Tts,
                        !assistant.localTools.contains(LocalToolOption.Tts),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_ask_user_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_ask_user_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.AskUser),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.AskUser,
                        !assistant.localTools.contains(LocalToolOption.AskUser),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_screen_time_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_screen_time_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.ScreenTime),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ScreenTime, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.ScreenTime,
                        !assistant.localTools.contains(LocalToolOption.ScreenTime),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_calendar_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_calendar_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.Calendar),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.Calendar,
                        !assistant.localTools.contains(LocalToolOption.Calendar),
                    )
                },
            )
            item(
                headlineContent = { Text(stringResource(R.string.assistant_page_local_tools_chart_display_title)) },
                supportingContent = { Text(stringResource(R.string.assistant_page_local_tools_chart_display_desc)) },
                trailingContent = {
                    KedgeSwitch(
                        checked = assistant.localTools.contains(LocalToolOption.ChartDisplay),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.ChartDisplay, it) },
                    )
                },
                onClick = {
                    toggleLocalTool(
                        LocalToolOption.ChartDisplay,
                        !assistant.localTools.contains(LocalToolOption.ChartDisplay),
                    )
                },
            )
        }
    }
}
