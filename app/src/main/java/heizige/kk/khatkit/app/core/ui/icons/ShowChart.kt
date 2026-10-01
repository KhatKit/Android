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
public val show_chart: ImageVector
  get() {
    if (_show_chart != null) {
      return _show_chart!!
    }
    _show_chart =
      ImageVector.Builder(
          name = "show_chart",
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
            moveTo(2.43f, 17f)
            quadToRelative(0f, -0.43f, 0.32f, -0.75f)
            lineTo(8.08f, 10.93f)
            quadTo(8.65f, 10.35f, 9.5f, 10.35f)
            reflectiveQuadToRelative(1.43f, 0.57f)
            lineTo(13.5f, 13.5f)
            lineTo(19.9f, 6.27f)
            quadTo(20.18f, 5.95f, 20.61f, 5.95f)
            reflectiveQuadToRelative(0.74f, 0.3f)
            quadToRelative(0.27f, 0.27f, 0.29f, 0.66f)
            reflectiveQuadTo(21.38f, 7.6f)
            lineTo(14.9f, 14.9f)
            quadToRelative(-0.57f, 0.65f, -1.42f, 0.69f)
            reflectiveQuadTo(12f, 15f)
            lineTo(9.5f, 12.5f)
            lineTo(4.25f, 17.75f)
            quadTo(3.93f, 18.08f, 3.5f, 18.08f)
            reflectiveQuadTo(2.75f, 17.75f)
            quadTo(2.43f, 17.43f, 2.43f, 17f)
            close()
          }
        }
        .build()
    return _show_chart!!
  }

private var _show_chart: ImageVector? = null
