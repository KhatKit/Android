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
public val createNewFolder: ImageVector
  get() {
    if (_create_new_folder != null) {
      return _create_new_folder!!
    }
    _create_new_folder =
      ImageVector.Builder(
          name = "createNewFolder",
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
            moveTo(4f, 20f)
            quadTo(3.18f, 20f, 2.59f, 19.41f)
            reflectiveQuadTo(2f, 18f)
            verticalLineTo(6f)
            quadTo(2f, 5.18f, 2.59f, 4.59f)
            reflectiveQuadTo(4f, 4f)
            horizontalLineTo(9.18f)
            quadToRelative(0.4f, 0f, 0.76f, 0.15f)
            reflectiveQuadToRelative(0.64f, 0.43f)
            lineTo(12f, 6f)
            horizontalLineToRelative(8f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(22f, 7.18f, 22f, 8f)
            verticalLineTo(18f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 20f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 18f)
            horizontalLineTo(20f)
            verticalLineTo(8f)
            horizontalLineTo(11.18f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(4f)
            verticalLineTo(18f)
            close()
            moveToRelative(0f, 0f)
            verticalLineTo(6f)
            verticalLineTo(8f)
            verticalLineTo(18f)
            close()
            moveTo(14f, 14f)
            verticalLineToRelative(1f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(15f, 16f)
            reflectiveQuadToRelative(0.71f, -0.29f)
            reflectiveQuadTo(16f, 15f)
            verticalLineTo(14f)
            horizontalLineToRelative(1f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(18f, 13.43f, 18f, 13f)
            reflectiveQuadTo(17.71f, 12.29f)
            reflectiveQuadTo(17f, 12f)
            horizontalLineTo(16f)
            verticalLineTo(11f)
            quadToRelative(0f, -0.43f, -0.29f, -0.71f)
            reflectiveQuadTo(15f, 10f)
            reflectiveQuadToRelative(-0.71f, 0.29f)
            reflectiveQuadTo(14f, 11f)
            verticalLineToRelative(1f)
            horizontalLineTo(13f)
            quadToRelative(-0.42f, 0f, -0.71f, 0.29f)
            reflectiveQuadTo(12f, 13f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(13f, 14f)
            horizontalLineToRelative(1f)
            close()
          }
        }
        .build()
    return _create_new_folder!!
  }

private var _create_new_folder: ImageVector? = null
