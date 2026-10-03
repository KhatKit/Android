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
public val dashboard: ImageVector
  get() {
    if (_dashboard != null) {
      return _dashboard!!
    }
    _dashboard =
      ImageVector.Builder(
          name = "dashboard",
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
            moveTo(13f, 8f)
            verticalLineTo(4f)
            quadTo(13f, 3.57f, 13.29f, 3.29f)
            reflectiveQuadTo(14f, 3f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 4f)
            verticalLineTo(8f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(20f, 9f)
            horizontalLineTo(14f)
            quadTo(13.58f, 9f, 13.29f, 8.71f)
            reflectiveQuadTo(13f, 8f)
            close()
            moveTo(3f, 12f)
            verticalLineTo(4f)
            quadTo(3f, 3.57f, 3.29f, 3.29f)
            reflectiveQuadTo(4f, 3f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(11f, 4f)
            verticalLineToRelative(8f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(10f, 13f)
            horizontalLineTo(4f)
            quadTo(3.58f, 13f, 3.29f, 12.71f)
            quadTo(3f, 12.43f, 3f, 12f)
            close()
            moveToRelative(10f, 8f)
            verticalLineTo(12f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(14f, 11f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(21f, 12f)
            verticalLineToRelative(8f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(20f, 21f)
            horizontalLineTo(14f)
            quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
            quadTo(13f, 20.43f, 13f, 20f)
            close()
            moveTo(3f, 20f)
            verticalLineTo(16f)
            quadTo(3f, 15.58f, 3.29f, 15.29f)
            reflectiveQuadTo(4f, 15f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(11f, 16f)
            verticalLineToRelative(4f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(10f, 21f)
            horizontalLineTo(4f)
            quadTo(3.58f, 21f, 3.29f, 20.71f)
            quadTo(3f, 20.43f, 3f, 20f)
            close()
            moveTo(5f, 11f)
            horizontalLineTo(9f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            close()
            moveToRelative(10f, 8f)
            horizontalLineToRelative(4f)
            verticalLineTo(13f)
            horizontalLineTo(15f)
            verticalLineToRelative(6f)
            close()
            moveTo(15f, 7f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(15f)
            verticalLineTo(7f)
            close()
            moveTo(5f, 19f)
            horizontalLineTo(9f)
            verticalLineTo(17f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            close()
            moveTo(9f, 11f)
            close()
            moveTo(15f, 7f)
            close()
            moveToRelative(0f, 6f)
            close()
            moveTo(9f, 17f)
            close()
          }
        }
        .build()
    return _dashboard!!
  }

private var _dashboard: ImageVector? = null