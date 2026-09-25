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
public val book2: ImageVector
  get() {
    if (_book_2 != null) {
      return _book_2!!
    }
    _book_2 =
      ImageVector.Builder(
          name = "book2",
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
            moveTo(6f, 15.33f)
            quadTo(6.35f, 15.15f, 6.73f, 15.08f)
            reflectiveQuadTo(7.5f, 15f)
            horizontalLineTo(8f)
            verticalLineTo(4f)
            horizontalLineTo(7.5f)
            quadTo(6.88f, 4f, 6.44f, 4.44f)
            reflectiveQuadTo(6f, 5.5f)
            verticalLineToRelative(9.82f)
            close()
            moveTo(10f, 15f)
            horizontalLineToRelative(8f)
            verticalLineTo(4f)
            horizontalLineTo(10f)
            verticalLineTo(15f)
            close()
            moveTo(6f, 15.33f)
            verticalLineTo(4f)
            verticalLineTo(15.33f)
            close()
            moveTo(7.5f, 22f)
            quadTo(6.05f, 22f, 5.03f, 20.98f)
            reflectiveQuadTo(4f, 18.5f)
            verticalLineTo(5.5f)
            quadTo(4f, 4.05f, 5.03f, 3.02f)
            reflectiveQuadTo(7.5f, 2f)
            horizontalLineTo(18f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(20f, 4f)
            verticalLineTo(16.52f)
            quadToRelative(0f, 0.2f, -0.16f, 0.36f)
            reflectiveQuadToRelative(-0.59f, 0.36f)
            quadToRelative(-0.35f, 0.18f, -0.55f, 0.5f)
            reflectiveQuadTo(18.5f, 18.5f)
            reflectiveQuadToRelative(0.2f, 0.76f)
            reflectiveQuadToRelative(0.55f, 0.49f)
            reflectiveQuadToRelative(0.55f, 0.41f)
            reflectiveQuadTo(20f, 20.73f)
            verticalLineToRelative(0.25f)
            quadToRelative(0f, 0.42f, -0.29f, 0.72f)
            reflectiveQuadTo(19f, 22f)
            horizontalLineTo(7.5f)
            close()
            moveToRelative(0f, -2f)
            horizontalLineToRelative(9.32f)
            quadTo(16.68f, 19.65f, 16.59f, 19.29f)
            reflectiveQuadTo(16.5f, 18.5f)
            quadToRelative(0f, -0.4f, 0.07f, -0.77f)
            reflectiveQuadTo(16.83f, 17f)
            horizontalLineTo(7.5f)
            quadTo(6.85f, 17f, 6.43f, 17.44f)
            reflectiveQuadTo(6f, 18.5f)
            quadToRelative(0f, 0.65f, 0.43f, 1.07f)
            reflectiveQuadTo(7.5f, 20f)
            close()
          }
        }
        .build()
    return _book_2!!
  }

private var _book_2: ImageVector? = null
