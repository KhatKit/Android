package heizige.kk.khatkit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeTextButton
import heizige.kk.kedge.components.KedgeIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.db.entity.ManagedFileEntity
import heizige.kk.khatkit.app.core.data.files.FileFolders
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.khatkit.app.core.ui.components.ui.AppAlertDialog
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.MiuixSettingsPage
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import heizige.kk.khatkit.app.core.ui.icons.cleaningServices
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 聊天文件页的 Miuix 风格版。
 *
 * 筛选栏用 Miuix [TabRow]（Miuix 无 FilterChip，TabRow 是最接近的横向单选控件）；
 * 文件网格与文件卡片沿用原有实现（图片网格与风格无关，且卡片底色已是双风格）。
 */
@Composable
fun SettingFilesPageMiuix() {
    val scope = rememberCoroutineScope()
    val filesManager = rememberAppEntryPoint().filesManager()
    val deletedToast = stringResource(R.string.setting_files_page_deleted_toast)
    val deleteFailedToast = stringResource(R.string.setting_files_page_delete_failed_toast)
    val cleanedToast = stringResource(R.string.setting_files_page_cleaned_toast)
    val cleanFailedToast = stringResource(R.string.setting_files_page_clean_failed_toast)

    var selectedFolder by remember { mutableStateOf(FileFolders.UPLOAD) }
    var pendingDelete by remember { mutableStateOf<ManagedFileEntity?>(null) }
    var showCleanSheet by remember { mutableStateOf(false) }
    var selectedCleanRange by remember { mutableStateOf(CleanRange.DAYS_7) }
    val files by filesManager.observe(selectedFolder).collectAsState(initial = emptyList())
    val folders = remember { listOf(FileFolders.UPLOAD) }
    val gridState = rememberLazyStaggeredGridState()

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AppAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.setting_files_page_delete_file_title)) },
            text = { Text(target.displayName) },
            confirmButton = {
                KedgeTextButton(onClick = {
                    scope.launch {
                        val ok = filesManager.delete(target.id, deleteFromDisk = true)
                        Toast.show(if (ok) deletedToast else deleteFailedToast)
                        pendingDelete = null
                    }
                }) { Text(stringResource(R.string.setting_files_page_delete_action)) }
            },
            dismissButton = {
                KedgeTextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.setting_files_page_cancel_action))
                }
            },
        )
    }

    if (showCleanSheet) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(R.string.setting_files_page_clean_title),
            imageVector = cleaningServices,
            confirmText = stringResource(R.string.setting_files_page_clean_action),
            onConfirm = {
                showCleanSheet = false
                scope.launch {
                    val ok = selectedCleanRange.days?.let { days ->
                        filesManager.deleteOlderThan(
                            folder = selectedFolder,
                            cutoffMillis = System.currentTimeMillis() -
                                java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong()),
                        )
                    } ?: filesManager.deleteAll(selectedFolder)
                    Toast.show(if (ok) cleanedToast else cleanFailedToast)
                }
            },
            onDismiss = { showCleanSheet = false },
        ) { _ ->
            CleanFilesSheet(
                selectedRange = selectedCleanRange,
                onRangeSelected = { selectedCleanRange = it },
            )
        }
    }

    MiuixSettingsPage(
        title = stringResource(R.string.setting_files_page_title),
        navigationIcon = { BackButton() },
        actions = {
            KedgeIconButton(
                onClick = { showCleanSheet = true },
                enabled = files.isNotEmpty(),
            ) {
                Icon(
                    imageVector = cleaningServices,
                    contentDescription = stringResource(
                        R.string.setting_files_page_clean_content_description
                    ),
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        bottomInnerPadding = 16.dp,
    ) {
        item {
            TabRow(
                tabs = folders.map { folderDisplayName(it) },
                selectedTabIndex = folders.indexOf(selectedFolder).coerceAtLeast(0),
                onTabSelected = { selectedFolder = folders[it] },
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (files.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight(0.7f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.setting_files_page_no_files))
                }
            }
        } else {
            item {
                LazyVerticalStaggeredGrid(
                    modifier = Modifier.fillParentMaxHeight(0.8f),
                    contentPadding = PaddingValues(4.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    state = gridState,
                    columns = StaggeredGridCells.Fixed(2),
                ) {
                    items(files, key = { it.id }) { file ->
                        FileItem(
                            file = file,
                            fileOnDisk = filesManager.getFile(file),
                            onDelete = { pendingDelete = file },
                        )
                    }
                }
            }
        }
    }
}
