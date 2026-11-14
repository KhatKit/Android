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
public val formatListNumbered: ImageVector
  get() {
    if (_format_list_numbered != null) {
      return _format_list_numbered!!
    }
    _format_list_numbered =
      ImageVector.Builder(
          name = "formatListNumbered",
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
            moveTo(3f, 22f)
            verticalLineTo(20.5f)
            horizontalLineTo(5.5f)
            verticalLineTo(19.75f)
            horizontalLineTo(4f)
            verticalLineToRelative(-1.5f)
            horizontalLineTo(5.5f)
            verticalLineTo(17.5f)
            horizontalLineTo(3f)
            verticalLineTo(16f)
            horizontalLineTo(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(7f, 17f)
            verticalLineToRelative(1f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 19f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(7f, 20f)
            verticalLineToRelative(1f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 22f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 15f)
            verticalLineTo(12.25f)
            quadTo(3f, 11.83f, 3.29f, 11.54f)
            reflectiveQuadTo(4f, 11.25f)
            horizontalLineTo(5.5f)
            verticalLineTo(10.5f)
            horizontalLineTo(3f)
            verticalLineTo(9f)
            horizontalLineTo(6f)
            quadTo(6.43f, 9f, 6.71f, 9.29f)
            reflectiveQuadTo(7f, 10f)
            verticalLineToRelative(1.75f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 12.75f)
            horizontalLineTo(4.5f)
            verticalLineTo(13.5f)
            horizontalLineTo(7f)
            verticalLineTo(15f)
            horizontalLineTo(3f)
            close()
            moveTo(4.5f, 8f)
            verticalLineTo(3.5f)
            horizontalLineTo(3f)
            verticalLineTo(2f)
            horizontalLineTo(6f)
            verticalLineTo(8f)
            horizontalLineTo(4.5f)
            close()
            moveTo(9f, 19f)
            verticalLineTo(17f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            close()
            moveTo(9f, 13f)
            verticalLineTo(11f)
            horizontalLineTo(21f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            close()
            moveTo(9f, 7f)
            verticalLineTo(5f)
            horizontalLineTo(21f)
            verticalLineTo(7f)
            horizontalLineTo(9f)
            close()
          }
        }
        .build()
    return _format_list_numbered!!
  }

private var _format_list_numbered: ImageVector? = null
