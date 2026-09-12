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
public val rocketLaunch: ImageVector
  get() {
    if (_rocket_launch != null) {
      return _rocket_launch!!
    }
    _rocket_launch =
      ImageVector.Builder(
          name = "rocketLaunch",
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
            moveTo(7.1f, 11.35f)
            quadTo(7.45f, 10.65f, 7.83f, 10f)
            reflectiveQuadTo(8.65f, 8.7f)
            lineTo(7.25f, 8.42f)
            lineToRelative(-2.1f, 2.1f)
            lineTo(7.1f, 11.35f)
            close()
            moveTo(19.15f, 4.47f)
            quadTo(17.4f, 4.52f, 15.41f, 5.5f)
            quadTo(13.43f, 6.47f, 11.8f, 8.1f)
            quadTo(10.75f, 9.15f, 9.93f, 10.35f)
            reflectiveQuadTo(8.7f, 12.6f)
            lineToRelative(2.85f, 2.82f)
            quadTo(12.6f, 15.03f, 13.8f, 14.2f)
            quadTo(15f, 13.38f, 16.05f, 12.33f)
            quadToRelative(1.63f, -1.63f, 2.6f, -3.6f)
            reflectiveQuadTo(19.68f, 5f)
            quadToRelative(0f, -0.1f, -0.04f, -0.2f)
            reflectiveQuadTo(19.53f, 4.63f)
            reflectiveQuadTo(19.35f, 4.51f)
            quadTo(19.25f, 4.47f, 19.15f, 4.47f)
            close()
            moveTo(13.08f, 9.06f)
            quadToRelative(0f, -0.84f, 0.57f, -1.41f)
            reflectiveQuadTo(15.08f, 7.07f)
            reflectiveQuadTo(16.5f, 7.65f)
            quadToRelative(0.57f, 0.58f, 0.57f, 1.41f)
            reflectiveQuadTo(16.5f, 10.48f)
            reflectiveQuadToRelative(-1.42f, 0.57f)
            reflectiveQuadTo(13.65f, 10.48f)
            reflectiveQuadTo(13.08f, 9.06f)
            close()
            moveTo(12.8f, 17.02f)
            lineTo(13.63f, 19f)
            lineToRelative(2.1f, -2.1f)
            lineTo(15.45f, 15.5f)
            quadToRelative(-0.65f, 0.45f, -1.3f, 0.81f)
            reflectiveQuadTo(12.8f, 17.02f)
            close()
            moveTo(21.58f, 3.67f)
            quadToRelative(0.2f, 2.75f, -0.9f, 5.36f)
            reflectiveQuadTo(17.2f, 14.02f)
            lineToRelative(0.5f, 2.48f)
            quadToRelative(0.1f, 0.5f, -0.05f, 0.98f)
            reflectiveQuadToRelative(-0.5f, 0.82f)
            lineTo(14f, 21.45f)
            quadToRelative(-0.38f, 0.38f, -0.9f, 0.29f)
            reflectiveQuadTo(12.38f, 21.15f)
            lineTo(10.85f, 17.58f)
            lineTo(6.58f, 13.3f)
            lineTo(3f, 11.77f)
            quadTo(2.5f, 11.58f, 2.4f, 11.05f)
            reflectiveQuadToRelative(0.27f, -0.9f)
            lineTo(5.83f, 7f)
            quadTo(6.18f, 6.65f, 6.66f, 6.5f)
            reflectiveQuadTo(7.65f, 6.45f)
            lineToRelative(2.47f, 0.5f)
            quadTo(12.5f, 4.57f, 15.11f, 3.47f)
            quadToRelative(2.61f, -1.1f, 5.36f, -0.9f)
            quadToRelative(0.2f, 0.02f, 0.4f, 0.11f)
            quadToRelative(0.2f, 0.09f, 0.35f, 0.24f)
            reflectiveQuadToRelative(0.24f, 0.35f)
            reflectiveQuadToRelative(0.11f, 0.4f)
            close()
            moveTo(3.93f, 15.98f)
            quadTo(4.8f, 15.1f, 6.06f, 15.09f)
            quadTo(7.33f, 15.08f, 8.2f, 15.95f)
            reflectiveQuadToRelative(0.86f, 2.14f)
            reflectiveQuadTo(8.18f, 20.23f)
            quadToRelative(-1.2f, 1.2f, -2.84f, 1.42f)
            quadTo(3.7f, 21.88f, 2.05f, 22.1f)
            quadTo(2.28f, 20.45f, 2.5f, 18.81f)
            reflectiveQuadTo(3.93f, 15.98f)
            close()
            moveToRelative(1.43f, 1.4f)
            quadTo(4.93f, 17.8f, 4.76f, 18.4f)
            reflectiveQuadTo(4.5f, 19.63f)
            quadToRelative(0.63f, -0.1f, 1.23f, -0.25f)
            reflectiveQuadTo(6.75f, 18.8f)
            quadToRelative(0.3f, -0.3f, 0.33f, -0.73f)
            reflectiveQuadTo(6.8f, 17.35f)
            reflectiveQuadTo(6.08f, 17.06f)
            reflectiveQuadTo(5.35f, 17.38f)
            close()
          }
        }
        .build()
    return _rocket_launch!!
  }

private var _rocket_launch: ImageVector? = null
