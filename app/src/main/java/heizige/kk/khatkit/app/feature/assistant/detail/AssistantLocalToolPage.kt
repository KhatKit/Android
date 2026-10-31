package heizige.kk.khatkit.app.feature.assistant.detail

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.uikit.mochi.Toast
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
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.khatkit.app.core.ui.theme.PageMetrics
import heizige.kk.khatkit.app.core.util.plus
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = innerPadding + PaddingValues(
            start = 16.dp,
            top = PageMetrics.TopContentPadding,
            end = 16.dp,
            bottom = 8.dp + PageMetrics.BottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 八个本地工具同属一组 ⇒ 一张 Mochi 分组卡片；整行可点切换，行尾开关同样可拨。
        item(key = "local_tools") {
            MochiListSection {
                LocalToolRows.forEach { row ->
                    val checked = assistant.localTools.contains(row.option)
                    MochiListRow(
                        title = stringResource(row.title),
                        subtitle = stringResource(row.desc),
                        onClick = { toggleLocalTool(row.option, !checked) },
                        trailing = {
                            MochiSwitch(
                                checked = checked,
                                onCheckedChange = { toggleLocalTool(row.option, it) },
                            )
                        },
                    )
                }
            }
        }
    }
}

private class LocalToolRow(
    val option: LocalToolOption,
    @StringRes val title: Int,
    @StringRes val desc: Int,
)

/** 显示顺序与改动前一致。 */
private val LocalToolRows = listOf(
    LocalToolRow(
        LocalToolOption.JavascriptEngine,
        R.string.assistant_page_local_tools_javascript_engine_title,
        R.string.assistant_page_local_tools_javascript_engine_desc,
    ),
    LocalToolRow(
        LocalToolOption.TimeInfo,
        R.string.assistant_page_local_tools_time_info_title,
        R.string.assistant_page_local_tools_time_info_desc,
    ),
    LocalToolRow(
        LocalToolOption.Clipboard,
        R.string.assistant_page_local_tools_clipboard_title,
        R.string.assistant_page_local_tools_clipboard_desc,
    ),
    LocalToolRow(
        LocalToolOption.Tts,
        R.string.assistant_page_local_tools_tts_title,
        R.string.assistant_page_local_tools_tts_desc,
    ),
    LocalToolRow(
        LocalToolOption.AskUser,
        R.string.assistant_page_local_tools_ask_user_title,
        R.string.assistant_page_local_tools_ask_user_desc,
    ),
    LocalToolRow(
        LocalToolOption.ScreenTime,
        R.string.assistant_page_local_tools_screen_time_title,
        R.string.assistant_page_local_tools_screen_time_desc,
    ),
    LocalToolRow(
        LocalToolOption.Calendar,
        R.string.assistant_page_local_tools_calendar_title,
        R.string.assistant_page_local_tools_calendar_desc,
    ),
    LocalToolRow(
        LocalToolOption.ChartDisplay,
        R.string.assistant_page_local_tools_chart_display_title,
        R.string.assistant_page_local_tools_chart_display_desc,
    ),
)
