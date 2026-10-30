/*
 * 网格角标：图片格式（右上）与文件体积（左下）。
 * 对应 ImageToolbox `MediaExtensionHeader` / `MediaSizeFooter`（Apache-2.0, T8RIN）。
 */

package heizige.kk.khatkit.mediapicker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import heizige.kk.khatkit.mediapicker.domain.Media

/** 白字，投影下压在图片上才看得清。 */
internal val OnMediaOverlay = Color.White

@Composable
fun MediaExtensionHeader(
    modifier: Modifier = Modifier,
    media: Media,
) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .padding(vertical = 2.dp)
            .advancedShadow(cornersRadius = 4.dp, shadowBlurRadius = 6.dp, alpha = 0.4f)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = media.fileExtension.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = OnMediaOverlay,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun MediaSizeFooter(
    modifier: Modifier = Modifier,
    media: Media,
) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .padding(vertical = 2.dp)
            .advancedShadow(cornersRadius = 4.dp, shadowBlurRadius = 6.dp, alpha = 0.4f)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = media.humanFileSize,
            style = MaterialTheme.typography.labelMedium,
            color = OnMediaOverlay,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
