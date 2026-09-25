package heizige.kk.khatkit.app.feature.market

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.feature.record.RecordCardSheet
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.icons.adsClick
import heizige.kk.khatkit.uikit.KhatKitMarketContent
import heizige.kk.khatkit.uikit.KhatKitTheme
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint

/**
 * KhatKit 卡片市场（宿主壳）：顶栏/导航/Toast 由 app 提供，
 * 正文与设置/密钥弹窗在 :khatkit-ui，多风格由 [KhatKitTheme] 统一驱动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatKitMarketPage() {
    val provider = rememberAppEntryPoint().khatKitToolProvider()
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var style by remember { mutableStateOf(provider.uiStyle) }
    var showRecorder by remember { mutableStateOf(false) }
    var installedReloadKey by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.khatkit_market_title),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showRecorder = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(adsClick, contentDescription = "录制操作")
                    }
                },
            )
        },
    ) { padding ->
        KhatKitTheme(style = style) {
            // 录制保存后重建市场正文，让新卡片立即出现在已安装列表里
            key(installedReloadKey) {
                KhatKitMarketContent(
                    controller = provider,
                    onToast = { message, isError ->
                        Toast.show(
                            message = message,
                            isError = isError,
                        )
                    },
                    onStyleChanged = { style = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
        }
    }

    RecordCardSheet(
        visible = showRecorder,
        onDismiss = { showRecorder = false },
        onSaved = { installedReloadKey++ },
    )
}
