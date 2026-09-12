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
public val shelves: ImageVector
  get() {
    if (_shelves != null) {
      return _shelves!!
    }
    _shelves =
      ImageVector.Builder(
          name = "shelves",
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
            moveTo(3.29f, 22.71f)
            quadTo(3f, 22.43f, 3f, 22f)
            verticalLineTo(2f)
            quadTo(3f, 1.57f, 3.29f, 1.29f)
            reflectiveQuadTo(4f, 1f)
            reflectiveQuadTo(4.71f, 1.29f)
            reflectiveQuadTo(5f, 2f)
            verticalLineTo(3f)
            horizontalLineTo(19f)
            verticalLineTo(2f)
            quadTo(19f, 1.57f, 19.29f, 1.29f)
            reflectiveQuadTo(20f, 1f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 2f)
            verticalLineTo(22f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(20f, 23f)
            reflectiveQuadTo(19.29f, 22.71f)
            quadTo(19f, 22.43f, 19f, 22f)
            verticalLineTo(21f)
            horizontalLineTo(5f)
            verticalLineToRelative(1f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(4f, 23f)
            reflectiveQuadTo(3.29f, 22.71f)
            close()
            moveTo(5f, 11f)
            horizontalLineTo(7f)
            verticalLineTo(8f)
            quadTo(7f, 7.57f, 7.29f, 7.29f)
            reflectiveQuadTo(8f, 7f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(13f, 8f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            close()
            moveToRelative(0f, 8f)
            horizontalLineToRelative(6f)
            verticalLineTo(16f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(12f, 15f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(17f, 16f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(13f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            close()
            moveTo(9f, 11f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            close()
            moveToRelative(4f, 8f)
            horizontalLineToRelative(2f)
            verticalLineTo(17f)
            horizontalLineTo(13f)
            verticalLineToRelative(2f)
            close()
            moveTo(9f, 11f)
            horizontalLineToRelative(2f)
            horizontalLineTo(9f)
            close()
            moveToRelative(4f, 8f)
            horizontalLineToRelative(2f)
            horizontalLineTo(13f)
            close()
          }
        }
        .build()
    return _shelves!!
  }

private var _shelves: ImageVector? = null
