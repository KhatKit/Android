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
public val folderCopy: ImageVector
  get() {
    if (_folder_copy != null) {
      return _folder_copy!!
    }
    _folder_copy =
      ImageVector.Builder(
          name = "folderCopy",
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
            moveTo(3f, 21f)
            quadTo(2.18f, 21f, 1.59f, 20.41f)
            reflectiveQuadTo(1f, 19f)
            verticalLineTo(7f)
            quadTo(1f, 6.57f, 1.29f, 6.29f)
            reflectiveQuadTo(2f, 6f)
            quadTo(2.43f, 6f, 2.71f, 6.29f)
            reflectiveQuadTo(3f, 7f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 20f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(19f, 21f)
            horizontalLineTo(3f)
            close()
            moveTo(7f, 17f)
            quadTo(6.18f, 17f, 5.59f, 16.41f)
            reflectiveQuadTo(5f, 15f)
            verticalLineTo(4f)
            quadTo(5f, 3.17f, 5.59f, 2.59f)
            reflectiveQuadTo(7f, 2f)
            horizontalLineToRelative(4.18f)
            quadToRelative(0.4f, 0f, 0.76f, 0.15f)
            reflectiveQuadToRelative(0.64f, 0.43f)
            lineTo(14f, 4f)
            horizontalLineToRelative(7f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(23f, 5.18f, 23f, 6f)
            verticalLineToRelative(9f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(21f, 17f)
            horizontalLineTo(7f)
            close()
            moveTo(7f, 15f)
            horizontalLineTo(21f)
            verticalLineTo(6f)
            horizontalLineTo(13.18f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(7f)
            verticalLineTo(15f)
            close()
            moveToRelative(0f, 0f)
            verticalLineTo(4f)
            verticalLineTo(6f)
            verticalLineToRelative(9f)
            close()
          }
        }
        .build()
    return _folder_copy!!
  }

private var _folder_copy: ImageVector? = null
