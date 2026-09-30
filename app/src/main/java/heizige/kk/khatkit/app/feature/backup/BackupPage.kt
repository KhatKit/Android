package heizige.kk.khatkit.app.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.feature.backup.components.BackupDialog
import heizige.kk.khatkit.app.feature.backup.tabs.ImportExportTab
import heizige.kk.khatkit.app.feature.backup.tabs.ReminderTab
import heizige.kk.khatkit.app.feature.backup.tabs.S3Tab
import heizige.kk.khatkit.app.feature.backup.tabs.WebDavTab
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeTabPageScaffold
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeTabRow

@Composable
fun BackupPage(vm: BackupViewModel = hiltViewModel()) {
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var showRestartDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    KedgeTabPageScaffold(
        title = stringResource(R.string.backup_page_title),
        titles = listOf(
            stringResource(R.string.backup_page_import_export),
            stringResource(R.string.backup_page_webdav_backup),
            stringResource(R.string.backup_page_s3_backup),
            stringResource(R.string.backup_page_reminder),
        ),
        selectedTabIndex = pagerState.currentPage,
        onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
        scrollableTab = true,
        scrollBehavior = scrollBehavior,
    ) { pagerModifier ->
            HorizontalPager(
                state = pagerState,
                modifier = pagerModifier.fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> {
                        ImportExportTab(
                            vm = vm,
                            onShowRestartDialog = { showRestartDialog = true }
                        )
                    }

                    1 -> {
                        WebDavTab(
                            vm = vm,
                            onShowRestartDialog = { showRestartDialog = true }
                        )
                    }

                    2 -> {
                        S3Tab(
                            vm = vm,
                            onShowRestartDialog = { showRestartDialog = true }
                        )
                    }

                    3 -> {
                        ReminderTab(vm = vm)
                    }
                }
            }
    }

    if (showRestartDialog) {
        BackupDialog()
    }
}
