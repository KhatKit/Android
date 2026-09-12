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
public val keyboardArrowUp: ImageVector
  get() {
    if (_keyboard_arrow_up != null) {
      return _keyboard_arrow_up!!
    }
    _keyboard_arrow_up =
      ImageVector.Builder(
          name = "keyboardArrowUp",
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
            moveTo(12f, 10.8f)
            lineTo(8.1f, 14.7f)
            quadTo(7.83f, 14.98f, 7.4f, 14.98f)
            reflectiveQuadTo(6.7f, 14.7f)
            reflectiveQuadTo(6.43f, 14f)
            reflectiveQuadTo(6.7f, 13.3f)
            lineTo(11.3f, 8.7f)
            quadTo(11.6f, 8.4f, 12f, 8.4f)
            reflectiveQuadToRelative(0.7f, 0.3f)
            lineToRelative(4.6f, 4.6f)
            quadToRelative(0.27f, 0.28f, 0.27f, 0.7f)
            reflectiveQuadTo(17.3f, 14.7f)
            reflectiveQuadToRelative(-0.7f, 0.28f)
            reflectiveQuadTo(15.9f, 14.7f)
            lineTo(12f, 10.8f)
            close()
          }
        }
        .build()
    return _keyboard_arrow_up!!
  }

private var _keyboard_arrow_up: ImageVector? = null
