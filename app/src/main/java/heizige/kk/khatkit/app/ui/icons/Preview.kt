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
public val preview: ImageVector
  get() {
    if (_preview != null) {
      return _preview!!
    }
    _preview =
      ImageVector.Builder(
          name = "preview",
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
            moveTo(5f, 19f)
            horizontalLineTo(19f)
            verticalLineTo(7f)
            horizontalLineTo(5f)
            verticalLineTo(19f)
            close()
            moveTo(8.34f, 15.89f)
            quadTo(6.73f, 14.78f, 6f, 13f)
            quadTo(6.73f, 11.23f, 8.34f, 10.11f)
            reflectiveQuadTo(12f, 9f)
            reflectiveQuadToRelative(3.66f, 1.11f)
            quadTo(17.28f, 11.23f, 18f, 13f)
            quadToRelative(-0.72f, 1.78f, -2.34f, 2.89f)
            reflectiveQuadTo(12f, 17f)
            reflectiveQuadTo(8.34f, 15.89f)
            close()
            moveToRelative(6.21f, -1.05f)
            quadTo(15.7f, 14.18f, 16.35f, 13f)
            quadTo(15.7f, 11.83f, 14.55f, 11.16f)
            reflectiveQuadTo(12f, 10.5f)
            reflectiveQuadTo(9.45f, 11.16f)
            reflectiveQuadTo(7.65f, 13f)
            quadToRelative(0.65f, 1.17f, 1.8f, 1.84f)
            reflectiveQuadTo(12f, 15.5f)
            reflectiveQuadToRelative(2.55f, -0.66f)
            close()
            moveTo(12f, 13f)
            close()
            moveToRelative(1.06f, 1.06f)
            quadTo(13.5f, 13.63f, 13.5f, 13f)
            reflectiveQuadTo(13.06f, 11.94f)
            reflectiveQuadTo(12f, 11.5f)
            reflectiveQuadToRelative(-1.06f, 0.44f)
            reflectiveQuadTo(10.5f, 13f)
            reflectiveQuadToRelative(0.44f, 1.06f)
            reflectiveQuadTo(12f, 14.5f)
            reflectiveQuadToRelative(1.06f, -0.44f)
            close()
          }
        }
        .build()
    return _preview!!
  }

private var _preview: ImageVector? = null
