package heizige.kk.khatkit.app.core.ui.components.ui

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.context.LocalSettings
import heizige.kk.kedge.overlays.KedgeProgressIndicator
import heizige.kk.kedge.overlays.KedgeProgressIndicatorType



@Composable
fun RabbitLoadingIndicator(modifier: Modifier = Modifier) {
    val useAppIconStyleLoadingIndicator = LocalSettings.current.displaySetting.useAppIconStyleLoadingIndicator
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    if (useAppIconStyleLoadingIndicator) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                ImageView(context).apply {
                    val drawable = AppCompatResources.getDrawable(context, R.drawable.rabbit) as? AnimatedVectorDrawable
                    setImageDrawable(drawable)
                    drawable?.setTint(primaryColor)
                    drawable?.start()
                }
            },
            update = { imageView ->
                (imageView.drawable as? AnimatedVectorDrawable)?.apply {
                    setTint(primaryColor)
                    start()
                }
            }
        )
    } else {
        // 不用 MD3 的 ContainedLoadingIndicator（Miuix 下不画任何东西）。
        // 统一走 Kedge 的双风格加载指示器：MD3 侧是 Expressive 的 LoadingIndicator
        // （图形循环变形），Miuix 侧是 Miuix 自己的环。
        KedgeProgressIndicator(
            modifier = modifier,
            type = KedgeProgressIndicatorType.Circular,
        )
    }
}