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
public val addComment: ImageVector
  get() {
    if (_add_comment != null) {
      return _add_comment!!
    }
    _add_comment =
      ImageVector.Builder(
          name = "addComment",
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
            moveTo(11f, 11f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(12f, 14f)
            reflectiveQuadToRelative(0.71f, -0.29f)
            quadTo(13f, 13.43f, 13f, 13f)
            verticalLineTo(11f)
            horizontalLineToRelative(2f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(16f, 10.43f, 16f, 10f)
            quadTo(16f, 9.57f, 15.71f, 9.29f)
            reflectiveQuadTo(15f, 9f)
            horizontalLineTo(13f)
            verticalLineTo(7f)
            quadTo(13f, 6.57f, 12.71f, 6.29f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(11.29f, 6.29f)
            reflectiveQuadTo(11f, 7f)
            verticalLineTo(9f)
            horizontalLineTo(9f)
            quadTo(8.58f, 9f, 8.29f, 9.29f)
            reflectiveQuadTo(8f, 10f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            quadTo(8.58f, 11f, 9f, 11f)
            horizontalLineToRelative(2f)
            close()
            moveTo(6f, 18f)
            lineTo(3.7f, 20.3f)
            quadTo(3.23f, 20.78f, 2.61f, 20.51f)
            reflectiveQuadTo(2f, 19.58f)
            verticalLineTo(4f)
            quadTo(2f, 3.17f, 2.59f, 2.59f)
            reflectiveQuadTo(4f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 18f)
            horizontalLineTo(6f)
            close()
            moveTo(5.15f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(17.13f)
            lineTo(5.15f, 16f)
            close()
            moveTo(4f, 16f)
            verticalLineTo(4f)
            verticalLineTo(16f)
            close()
          }
        }
        .build()
    return _add_comment!!
  }

private var _add_comment: ImageVector? = null
