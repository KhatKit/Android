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
public val book4: ImageVector
  get() {
    if (_book_4 != null) {
      return _book_4!!
    }
    _book_4 =
      ImageVector.Builder(
          name = "book4",
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
            moveTo(6f, 22f)
            quadTo(4.75f, 22f, 3.88f, 21.13f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5f)
            quadTo(3f, 3.75f, 3.88f, 2.88f)
            reflectiveQuadTo(6f, 2f)
            horizontalLineToRelative(9f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(17f, 4f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(15f, 18f)
            horizontalLineTo(6f)
            quadTo(5.58f, 18f, 5.29f, 18.29f)
            reflectiveQuadTo(5f, 19f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(6f, 20f)
            horizontalLineTo(19f)
            verticalLineTo(5f)
            quadTo(19f, 4.57f, 19.29f, 4.29f)
            reflectiveQuadTo(20f, 4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 5f)
            verticalLineTo(20f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 22f)
            horizontalLineTo(6f)
            close()
            moveTo(9f, 16f)
            horizontalLineToRelative(6f)
            verticalLineTo(4f)
            horizontalLineTo(9f)
            verticalLineTo(16f)
            close()
            moveTo(7f, 16f)
            verticalLineTo(4f)
            horizontalLineTo(6f)
            quadTo(5.58f, 4f, 5.29f, 4.29f)
            reflectiveQuadTo(5f, 5f)
            verticalLineTo(16.18f)
            quadTo(5.25f, 16.1f, 5.49f, 16.05f)
            reflectiveQuadTo(6f, 16f)
            horizontalLineTo(7f)
            close()
            moveTo(5f, 4f)
            verticalLineTo(16.18f)
            verticalLineTo(4f)
            close()
          }
        }
        .build()
    return _book_4!!
  }

private var _book_4: ImageVector? = null
