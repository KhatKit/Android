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
public val book5: ImageVector
  get() {
    if (_book_5 != null) {
      return _book_5!!
    }
    _book_5 =
      ImageVector.Builder(
          name = "book5",
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
            moveTo(6.75f, 22f)
            quadTo(5.63f, 22f, 4.81f, 21.24f)
            reflectiveQuadTo(4f, 19.35f)
            verticalLineTo(5.4f)
            quadTo(4f, 4.45f, 4.59f, 3.7f)
            reflectiveQuadTo(6.13f, 2.75f)
            lineToRelative(7.5f, -1.48f)
            quadToRelative(0.93f, -0.2f, 1.65f, 0.4f)
            reflectiveQuadTo(16f, 3.22f)
            verticalLineTo(15.15f)
            quadToRelative(0f, 0.72f, -0.45f, 1.29f)
            reflectiveQuadTo(14.4f, 17.13f)
            lineTo(6.53f, 18.7f)
            quadTo(6.3f, 18.75f, 6.15f, 18.94f)
            reflectiveQuadTo(6f, 19.35f)
            quadToRelative(0f, 0.27f, 0.23f, 0.46f)
            reflectiveQuadTo(6.75f, 20f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            quadTo(18f, 4.57f, 18.29f, 4.29f)
            reflectiveQuadTo(19f, 4f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(20f, 5f)
            verticalLineTo(20f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(18f, 22f)
            horizontalLineTo(6.75f)
            close()
            moveTo(9f, 16.18f)
            lineTo(14f, 15.2f)
            verticalLineTo(3.25f)
            lineTo(9f, 4.22f)
            verticalLineTo(16.18f)
            close()
            moveToRelative(-2f, 0.4f)
            verticalLineTo(4.63f)
            lineTo(6.63f, 4.7f)
            quadTo(6.35f, 4.75f, 6.18f, 4.94f)
            reflectiveQuadTo(6f, 5.4f)
            verticalLineTo(16.83f)
            quadTo(6.13f, 16.77f, 6.26f, 16.74f)
            reflectiveQuadTo(6.53f, 16.68f)
            lineTo(7f, 16.58f)
            close()
            moveTo(6f, 4.7f)
            verticalLineTo(16.83f)
            verticalLineTo(4.7f)
            close()
          }
        }
        .build()
    return _book_5!!
  }

private var _book_5: ImageVector? = null
