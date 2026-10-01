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
public val table: ImageVector
  get() {
    if (_table != null) {
      return _table!!
    }
    _table =
      ImageVector.Builder(
          name = "table",
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
            moveTo(5f, 21f)
            quadTo(4.18f, 21f, 3.59f, 20.41f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5f)
            quadTo(3f, 4.17f, 3.59f, 3.59f)
            reflectiveQuadTo(5f, 3f)
            horizontalLineTo(19f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(21f, 5f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 21f)
            horizontalLineTo(5f)
            close()
            moveToRelative(6f, -6f)
            horizontalLineTo(5f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(6f)
            verticalLineTo(15f)
            close()
            moveToRelative(2f, 0f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(6f)
            verticalLineTo(15f)
            horizontalLineTo(13f)
            close()
            moveTo(11f, 13f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(6f)
            close()
            moveToRelative(2f, 0f)
            horizontalLineToRelative(6f)
            verticalLineTo(9f)
            horizontalLineTo(13f)
            verticalLineToRelative(4f)
            close()
            moveTo(5f, 7f)
            horizontalLineTo(19f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(7f)
            close()
          }
        }
        .build()
    return _table!!
  }

private var _table: ImageVector? = null
