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
public val travelExplore: ImageVector
  get() {
    if (_travel_explore != null) {
      return _travel_explore!!
    }
    _travel_explore =
      ImageVector.Builder(
          name = "travelExplore",
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
            moveTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            quadToRelative(3.18f, 0f, 5.66f, 1.75f)
            reflectiveQuadToRelative(3.61f, 4.52f)
            quadToRelative(0.18f, 0.43f, 0.01f, 0.85f)
            reflectiveQuadTo(20.7f, 9.7f)
            quadTo(20.3f, 9.82f, 19.94f, 9.63f)
            quadTo(19.58f, 9.42f, 19.43f, 9.02f)
            quadTo(18.83f, 7.52f, 17.7f, 6.38f)
            quadTo(16.58f, 5.22f, 15f, 4.6f)
            verticalLineTo(5f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(13f, 7f)
            horizontalLineTo(11f)
            verticalLineTo(9f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(10f, 10f)
            horizontalLineTo(8f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(10f, 13f)
            verticalLineToRelative(2f)
            horizontalLineTo(9f)
            lineTo(4.2f, 10.2f)
            quadTo(4.13f, 10.65f, 4.06f, 11.1f)
            reflectiveQuadTo(4f, 12f)
            quadToRelative(0f, 3.05f, 2.01f, 5.32f)
            reflectiveQuadToRelative(5.06f, 2.63f)
            quadToRelative(0.4f, 0.05f, 0.66f, 0.34f)
            reflectiveQuadTo(12f, 21f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadToRelative(-0.69f, 0.24f)
            quadTo(7.2f, 21.58f, 4.6f, 18.75f)
            reflectiveQuadTo(2f, 12f)
            close()
            moveToRelative(18.4f, 8.8f)
            lineTo(17.9f, 18.3f)
            quadToRelative(-0.53f, 0.3f, -1.13f, 0.5f)
            reflectiveQuadTo(15.5f, 19f)
            quadToRelative(-1.88f, 0f, -3.19f, -1.31f)
            reflectiveQuadTo(11f, 14.5f)
            reflectiveQuadToRelative(1.31f, -3.19f)
            reflectiveQuadTo(15.5f, 10f)
            reflectiveQuadToRelative(3.19f, 1.31f)
            reflectiveQuadTo(20f, 14.5f)
            quadToRelative(0f, 0.67f, -0.2f, 1.28f)
            reflectiveQuadTo(19.3f, 16.9f)
            lineToRelative(2.5f, 2.5f)
            quadToRelative(0.28f, 0.28f, 0.28f, 0.7f)
            reflectiveQuadTo(21.8f, 20.8f)
            reflectiveQuadToRelative(-0.7f, 0.27f)
            reflectiveQuadTo(20.4f, 20.8f)
            close()
            moveTo(17.28f, 16.27f)
            quadTo(18f, 15.55f, 18f, 14.5f)
            reflectiveQuadTo(17.28f, 12.73f)
            reflectiveQuadTo(15.5f, 12f)
            reflectiveQuadToRelative(-1.77f, 0.72f)
            reflectiveQuadTo(13f, 14.5f)
            reflectiveQuadToRelative(0.73f, 1.77f)
            reflectiveQuadTo(15.5f, 17f)
            reflectiveQuadToRelative(1.78f, -0.73f)
            close()
          }
        }
        .build()
    return _travel_explore!!
  }

private var _travel_explore: ImageVector? = null
