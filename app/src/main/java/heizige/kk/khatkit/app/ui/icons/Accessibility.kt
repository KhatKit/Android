package heizige.kk.khatkit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val accessibility: ImageVector
  get() {
    if (_accessibility != null) {
      return _accessibility!!
    }
    _accessibility =
      ImageVector.Builder(
          name = "accessibility",
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
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(12f, 2f)
            curveTo(13.1f, 2f, 14f, 2.9f, 14f, 4f)
            reflectiveCurveTo(13.1f, 6f, 12f, 6f)
            reflectiveCurveTo(10f, 5.1f, 10f, 4f)
            reflectiveCurveTo(10.9f, 2f, 12f, 2f)
            close()
            moveTo(21f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 22f)
            lineTo(13f, 22f)
            lineTo(13f, 16f)
            lineTo(11f, 16f)
            lineTo(11f, 22f)
            lineTo(9f, 22f)
            lineTo(9f, 9f)
            lineTo(3f, 9f)
            lineTo(3f, 7f)
            lineTo(21f, 7f)
            lineTo(21f, 9f)
            close()
          }
        }
        .build()
    return _accessibility!!
  }

private var _accessibility: ImageVector? = null
