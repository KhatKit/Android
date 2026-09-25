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
public val inkEraser: ImageVector
  get() {
    if (_ink_eraser != null) {
      return _ink_eraser!!
    }
    _ink_eraser =
      ImageVector.Builder(
          name = "inkEraser",
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
            moveTo(17.25f, 18f)
            horizontalLineTo(21f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 19f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(21f, 20f)
            horizontalLineTo(15.25f)
            lineToRelative(2f, -2f)
            close()
            moveTo(5.18f, 20f)
            quadTo(4.98f, 20f, 4.79f, 19.93f)
            reflectiveQuadTo(4.45f, 19.7f)
            lineTo(2.63f, 17.88f)
            quadTo(2.05f, 17.3f, 2.04f, 16.45f)
            reflectiveQuadTo(2.6f, 15f)
            lineTo(13.6f, 3.6f)
            quadTo(14.18f, 3f, 15.01f, 3f)
            reflectiveQuadToRelative(1.41f, 0.57f)
            lineTo(21.4f, 8.55f)
            quadToRelative(0.57f, 0.58f, 0.57f, 1.43f)
            reflectiveQuadTo(21.4f, 11.4f)
            lineToRelative(-8.1f, 8.3f)
            quadToRelative(-0.15f, 0.15f, -0.34f, 0.23f)
            reflectiveQuadTo(12.58f, 20f)
            horizontalLineTo(5.18f)
            close()
            moveToRelative(6.98f, -2f)
            lineTo(20f, 9.95f)
            lineTo(15.05f, 5f)
            lineTo(4f, 16.4f)
            lineTo(5.6f, 18f)
            horizontalLineToRelative(6.55f)
            close()
            moveTo(12f, 12f)
            close()
          }
        }
        .build()
    return _ink_eraser!!
  }

private var _ink_eraser: ImageVector? = null
