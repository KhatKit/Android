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
public val construction: ImageVector
  get() {
    if (_construction != null) {
      return _construction!!
    }
    _construction =
      ImageVector.Builder(
          name = "construction",
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
            moveTo(17.85f, 19.95f)
            lineTo(13.43f, 15.53f)
            lineToRelative(2.1f, -2.1f)
            lineToRelative(4.43f, 4.43f)
            quadToRelative(0.42f, 0.42f, 0.42f, 1.05f)
            reflectiveQuadToRelative(-0.42f, 1.05f)
            reflectiveQuadTo(18.9f, 20.38f)
            reflectiveQuadTo(17.85f, 19.95f)
            close()
            moveTo(3.63f, 18.9f)
            quadToRelative(0f, -0.63f, 0.42f, -1.05f)
            lineTo(9.9f, 12f)
            lineTo(8.2f, 10.3f)
            quadTo(7.93f, 10.58f, 7.5f, 10.58f)
            quadToRelative(-0.42f, 0f, -0.7f, -0.28f)
            lineTo(6.23f, 9.73f)
            verticalLineToRelative(2.25f)
            quadToRelative(0f, 0.35f, -0.3f, 0.47f)
            reflectiveQuadTo(5.38f, 12.33f)
            lineTo(2.65f, 9.6f)
            quadTo(2.4f, 9.35f, 2.53f, 9.05f)
            reflectiveQuadTo(3f, 8.75f)
            horizontalLineTo(5.25f)
            lineTo(4.7f, 8.2f)
            quadTo(4.4f, 7.9f, 4.4f, 7.5f)
            reflectiveQuadTo(4.7f, 6.8f)
            lineTo(7.55f, 3.95f)
            quadTo(8.05f, 3.45f, 8.63f, 3.22f)
            reflectiveQuadTo(9.8f, 3f)
            quadToRelative(0.5f, 0f, 0.94f, 0.15f)
            reflectiveQuadTo(11.6f, 3.6f)
            quadToRelative(0.2f, 0.13f, 0.21f, 0.35f)
            quadToRelative(0.01f, 0.22f, -0.16f, 0.4f)
            lineToRelative(-1.9f, 1.9f)
            lineTo(10.3f, 6.8f)
            quadToRelative(0.28f, 0.27f, 0.28f, 0.7f)
            reflectiveQuadTo(10.3f, 8.2f)
            lineTo(12f, 9.9f)
            lineTo(14.25f, 7.65f)
            quadTo(14.15f, 7.38f, 14.09f, 7.07f)
            reflectiveQuadTo(14.03f, 6.47f)
            quadTo(14.03f, 5f, 15.04f, 3.99f)
            reflectiveQuadTo(17.53f, 2.97f)
            quadToRelative(0.2f, 0f, 0.38f, 0.01f)
            reflectiveQuadToRelative(0.35f, 0.06f)
            quadToRelative(0.23f, 0.08f, 0.29f, 0.31f)
            reflectiveQuadTo(18.43f, 3.77f)
            lineTo(16.8f, 5.4f)
            quadTo(16.65f, 5.55f, 16.65f, 5.75f)
            reflectiveQuadTo(16.8f, 6.1f)
            lineToRelative(1.1f, 1.1f)
            quadToRelative(0.15f, 0.15f, 0.35f, 0.15f)
            reflectiveQuadTo(18.6f, 7.2f)
            lineTo(20.23f, 5.57f)
            quadTo(20.4f, 5.4f, 20.64f, 5.45f)
            reflectiveQuadToRelative(0.31f, 0.3f)
            quadTo(21f, 5.93f, 21.01f, 6.1f)
            reflectiveQuadToRelative(0.01f, 0.38f)
            quadToRelative(0f, 1.48f, -1.01f, 2.49f)
            quadTo(19f, 9.98f, 17.53f, 9.98f)
            quadToRelative(-0.3f, 0f, -0.6f, -0.05f)
            quadTo(16.63f, 9.88f, 16.35f, 9.75f)
            lineTo(6.15f, 19.95f)
            quadTo(5.73f, 20.38f, 5.1f, 20.38f)
            reflectiveQuadTo(4.05f, 19.95f)
            quadTo(3.63f, 19.52f, 3.63f, 18.9f)
            close()
          }
        }
        .build()
    return _construction!!
  }

private var _construction: ImageVector? = null
