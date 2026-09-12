package heizige.kk.khatkit.app.ui.pages.khatkit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import heizige.kk.khatkit.app.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.data.ai.tools.KhatKitToolProvider
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.context.LocalToaster
import heizige.kk.khatkit.uikit.KhatKitMarketContent
import heizige.kk.khatkit.uikit.KhatKitTheme
import org.koin.compose.koinInject

/**
 * KhatKit 卡片市场（宿主壳）：顶栏/导航/Toast 由 app 提供，
 * 正文与设置/密钥弹窗在 :khatkit-ui，多风格由 [KhatKitTheme] 统一驱动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhatKitMarketPage(
    provider: KhatKitToolProvider = koinInject(),
) {
    val toaster = LocalToaster.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var style by remember { mutableStateOf(provider.uiStyle) }

    Scaffold(
        topBar = {
            KedgePageLargeTopBar(
                title = stringResource(R.string.khatkit_market_title),
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        KhatKitTheme(style = style) {
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
