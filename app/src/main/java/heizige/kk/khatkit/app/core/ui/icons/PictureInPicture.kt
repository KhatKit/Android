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
public val pictureInPicture: ImageVector
  get() {
    if (_pictureInPicture != null) {
      return _pictureInPicture!!
    }
    _pictureInPicture =
      ImageVector.Builder(
          name = "pictureInPicture",
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
            moveTo(19f, 11f)
            lineTo(11f, 11f)
            lineTo(11f, 17f)
            lineTo(19f, 17f)
            lineTo(19f, 11f)
            close()
            moveTo(23f, 19f)
            lineTo(23f, 4.98f)
            curveTo(23f, 3.88f, 22.1f, 3f, 21f, 3f)
            lineTo(3f, 3f)
            curveTo(1.9f, 3f, 1f, 3.88f, 1f, 4.98f)
            lineTo(1f, 19f)
            curveTo(1f, 20.1f, 1.9f, 21f, 3f, 21f)
            lineTo(21f, 21f)
            curveTo(22.1f, 21f, 23f, 20.1f, 23f, 19f)
            close()
            moveTo(21f, 19.02f)
            lineTo(3f, 19.02f)
            lineTo(3f, 4.97f)
            lineTo(21f, 4.97f)
            lineTo(21f, 19.02f)
            close()
          }
        }
        .build()
    return _pictureInPicture!!
  }

private var _pictureInPicture: ImageVector? = null
