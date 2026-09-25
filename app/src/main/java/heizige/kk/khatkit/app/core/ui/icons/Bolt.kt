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
public val bolt: ImageVector
  get() {
    if (_bolt != null) {
      return _bolt!!
    }
    _bolt =
      ImageVector.Builder(
          name = "bolt",
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
            moveTo(10.55f, 18.2f)
            lineTo(15.73f, 12f)
            horizontalLineToRelative(-4f)
            lineTo(12.45f, 6.32f)
            lineTo(7.83f, 13f)
            horizontalLineTo(11.3f)
            lineToRelative(-0.75f, 5.2f)
            close()
            moveTo(9f, 15f)
            horizontalLineTo(5.9f)
            quadTo(5.3f, 15f, 5.01f, 14.46f)
            quadTo(4.73f, 13.93f, 5.08f, 13.43f)
            lineTo(12.55f, 2.67f)
            quadTo(12.8f, 2.32f, 13.2f, 2.19f)
            reflectiveQuadTo(14.03f, 2.2f)
            reflectiveQuadToRelative(0.63f, 0.52f)
            reflectiveQuadToRelative(0.15f, 0.8f)
            lineTo(14f, 10f)
            horizontalLineToRelative(3.88f)
            quadToRelative(0.65f, 0f, 0.91f, 0.57f)
            reflectiveQuadToRelative(-0.16f, 1.07f)
            lineTo(10.4f, 21.5f)
            quadToRelative(-0.28f, 0.32f, -0.67f, 0.43f)
            reflectiveQuadTo(8.95f, 21.85f)
            reflectiveQuadTo(8.36f, 21.31f)
            reflectiveQuadTo(8.2f, 20.53f)
            lineTo(9f, 15f)
            close()
            moveToRelative(2.78f, -2.75f)
            close()
          }
        }
        .build()
    return _bolt!!
  }

private var _bolt: ImageVector? = null
