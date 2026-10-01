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
public val scatter_plot: ImageVector
  get() {
    if (_scatter_plot != null) {
      return _scatter_plot!!
    }
    _scatter_plot =
      ImageVector.Builder(
          name = "scatter_plot",
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
            moveTo(14.18f, 19.83f)
            quadTo(13f, 18.65f, 13f, 17f)
            reflectiveQuadToRelative(1.18f, -2.83f)
            reflectiveQuadTo(17f, 13f)
            reflectiveQuadToRelative(2.83f, 1.17f)
            reflectiveQuadTo(21f, 17f)
            reflectiveQuadToRelative(-1.17f, 2.82f)
            reflectiveQuadTo(17f, 21f)
            reflectiveQuadTo(14.18f, 19.83f)
            close()
            moveToRelative(4.24f, -1.41f)
            quadTo(19f, 17.83f, 19f, 17f)
            reflectiveQuadTo(18.41f, 15.59f)
            reflectiveQuadTo(17f, 15f)
            reflectiveQuadToRelative(-1.41f, 0.59f)
            reflectiveQuadTo(15f, 17f)
            reflectiveQuadToRelative(0.59f, 1.41f)
            reflectiveQuadTo(17f, 19f)
            reflectiveQuadToRelative(1.41f, -0.59f)
            close()
            moveTo(4.18f, 16.83f)
            quadTo(3f, 15.65f, 3f, 14f)
            reflectiveQuadTo(4.18f, 11.18f)
            reflectiveQuadTo(7f, 10f)
            reflectiveQuadToRelative(2.83f, 1.17f)
            reflectiveQuadTo(11f, 14f)
            reflectiveQuadTo(9.83f, 16.83f)
            reflectiveQuadTo(7f, 18f)
            reflectiveQuadTo(4.18f, 16.83f)
            close()
            moveTo(8.41f, 15.41f)
            quadTo(9f, 14.83f, 9f, 14f)
            reflectiveQuadTo(8.41f, 12.59f)
            quadTo(7.83f, 12f, 7f, 12f)
            reflectiveQuadTo(5.59f, 12.59f)
            quadTo(5f, 13.18f, 5f, 14f)
            reflectiveQuadToRelative(0.59f, 1.41f)
            reflectiveQuadTo(7f, 16f)
            quadToRelative(0.83f, 0f, 1.41f, -0.59f)
            close()
            moveTo(8.18f, 8.82f)
            quadTo(7f, 7.65f, 7f, 6f)
            reflectiveQuadTo(8.18f, 3.17f)
            reflectiveQuadTo(11f, 2f)
            reflectiveQuadToRelative(2.83f, 1.17f)
            reflectiveQuadTo(15f, 6f)
            reflectiveQuadTo(13.83f, 8.82f)
            reflectiveQuadTo(11f, 10f)
            reflectiveQuadTo(8.18f, 8.82f)
            close()
            moveTo(12.41f, 7.41f)
            quadTo(13f, 6.82f, 13f, 6f)
            reflectiveQuadTo(12.41f, 4.59f)
            reflectiveQuadTo(11f, 4f)
            quadTo(10.18f, 4f, 9.59f, 4.59f)
            quadTo(9f, 5.18f, 9f, 6f)
            reflectiveQuadTo(9.59f, 7.41f)
            reflectiveQuadTo(11f, 8f)
            reflectiveQuadTo(12.41f, 7.41f)
            close()
            moveTo(17f, 17f)
            close()
            moveTo(7f, 14f)
            close()
            moveTo(11f, 6f)
            close()
          }
        }
        .build()
    return _scatter_plot!!
  }

private var _scatter_plot: ImageVector? = null
