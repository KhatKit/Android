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
public val payments: ImageVector
  get() {
    if (_payments != null) {
      return _payments!!
    }
    _payments =
      ImageVector.Builder(
          name = "payments",
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
            moveTo(14f, 13f)
            quadToRelative(-1.25f, 0f, -2.13f, -0.88f)
            reflectiveQuadTo(11f, 10f)
            reflectiveQuadTo(11.88f, 7.88f)
            reflectiveQuadTo(14f, 7f)
            reflectiveQuadToRelative(2.13f, 0.88f)
            reflectiveQuadTo(17f, 10f)
            reflectiveQuadToRelative(-0.88f, 2.13f)
            reflectiveQuadTo(14f, 13f)
            close()
            moveTo(7f, 16f)
            quadTo(6.18f, 16f, 5.59f, 15.41f)
            reflectiveQuadTo(5f, 14f)
            verticalLineTo(6f)
            quadTo(5f, 5.18f, 5.59f, 4.59f)
            reflectiveQuadTo(7f, 4f)
            horizontalLineTo(21f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(23f, 5.18f, 23f, 6f)
            verticalLineToRelative(8f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(21f, 16f)
            horizontalLineTo(7f)
            close()
            moveTo(9f, 14f)
            horizontalLineTo(19f)
            quadToRelative(0f, -0.83f, 0.59f, -1.41f)
            reflectiveQuadTo(21f, 12f)
            verticalLineTo(8f)
            quadTo(20.18f, 8f, 19.59f, 7.41f)
            reflectiveQuadTo(19f, 6f)
            horizontalLineTo(9f)
            quadTo(9f, 6.82f, 8.41f, 7.41f)
            quadTo(7.83f, 8f, 7f, 8f)
            verticalLineToRelative(4f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(9f, 13.18f, 9f, 14f)
            close()
            moveToRelative(10f, 6f)
            horizontalLineTo(3f)
            quadTo(2.18f, 20f, 1.59f, 19.41f)
            reflectiveQuadTo(1f, 18f)
            verticalLineTo(8f)
            quadTo(1f, 7.57f, 1.29f, 7.29f)
            reflectiveQuadTo(2f, 7f)
            quadTo(2.43f, 7f, 2.71f, 7.29f)
            reflectiveQuadTo(3f, 8f)
            verticalLineTo(18f)
            horizontalLineTo(19f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 19f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(19f, 20f)
            close()
            moveTo(7f, 14f)
            verticalLineTo(6f)
            verticalLineToRelative(8f)
            close()
          }
        }
        .build()
    return _payments!!
  }

private var _payments: ImageVector? = null
