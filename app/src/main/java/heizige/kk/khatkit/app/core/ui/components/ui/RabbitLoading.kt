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
        // MD3 的 ContainedLoadingIndicator 在 Miuix 下不画任何东西，
        // 换成 Kedge 的 Circular：Miuix 走自己的环形进度，MD3 侧外观基本一致。
        KedgeProgressIndicator(
            modifier = modifier,
            type = KedgeProgressIndicatorType.Circular,
        )
    }
}