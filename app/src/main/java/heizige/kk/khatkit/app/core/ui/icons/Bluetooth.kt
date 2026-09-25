package heizige.kk.khatkit.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val bluetooth: ImageVector
  get() {
    if (_bluetooth != null) {
      return _bluetooth!!
    }
    _bluetooth =
      ImageVector.Builder(
          name = "bluetooth",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.EvenOdd,
          ) {
            moveTo(17.71f, 7.71f)
            lineTo(12f, 2f)
            lineTo(11f, 2f)
            lineTo(11f, 9.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(11f, 14.41f)
            lineTo(11f, 22f)
            lineTo(12f, 22f)
            lineTo(17.71f, 16.29f)
            lineTo(13.41f, 12f)
            lineTo(17.71f, 7.71f)
            close()
            moveTo(13f, 5.83f)
            lineTo(14.88f, 7.71f)
            lineTo(13f, 9.59f)
            lineTo(13f, 5.83f)
            close()
            moveTo(14.88f, 16.29f)
            lineTo(13f, 18.17f)
            lineTo(13f, 14.41f)
            lineTo(14.88f, 16.29f)
            close()
          }
        }
        .build()
    return _bluetooth!!
  }

private var _bluetooth: ImageVector? = null
