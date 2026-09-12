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
public val build: ImageVector
  get() {
    if (_build != null) {
      return _build!!
    }
    _build =
      ImageVector.Builder(
          name = "build",
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
            moveTo(9f, 15f)
            quadTo(6.5f, 15f, 4.75f, 13.25f)
            reflectiveQuadTo(3f, 9f)
            quadTo(3f, 8.5f, 3.08f, 8f)
            reflectiveQuadTo(3.35f, 7.05f)
            quadTo(3.48f, 6.8f, 3.66f, 6.68f)
            reflectiveQuadTo(4.08f, 6.5f)
            reflectiveQuadTo(4.54f, 6.51f)
            reflectiveQuadTo(4.98f, 6.77f)
            lineTo(7.6f, 9.4f)
            lineTo(9.4f, 7.6f)
            lineTo(6.78f, 4.97f)
            quadTo(6.58f, 4.77f, 6.51f, 4.54f)
            reflectiveQuadTo(6.5f, 4.07f)
            reflectiveQuadTo(6.68f, 3.66f)
            reflectiveQuadTo(7.05f, 3.35f)
            quadTo(7.5f, 3.15f, 8f, 3.07f)
            reflectiveQuadTo(9f, 3f)
            quadToRelative(2.5f, 0f, 4.25f, 1.75f)
            reflectiveQuadTo(15f, 9f)
            quadToRelative(0f, 0.57f, -0.1f, 1.09f)
            reflectiveQuadTo(14.6f, 11.1f)
            lineToRelative(5.05f, 5f)
            quadToRelative(0.72f, 0.72f, 0.72f, 1.77f)
            reflectiveQuadToRelative(-0.72f, 1.77f)
            quadToRelative(-0.73f, 0.73f, -1.78f, 0.73f)
            reflectiveQuadTo(16.1f, 19.63f)
            lineToRelative(-5f, -5.03f)
            quadToRelative(-0.5f, 0.2f, -1.01f, 0.3f)
            quadTo(9.58f, 15f, 9f, 15f)
            close()
            moveTo(9f, 13f)
            quadToRelative(0.65f, 0f, 1.3f, -0.2f)
            reflectiveQuadToRelative(1.18f, -0.63f)
            lineToRelative(6.07f, 6.08f)
            quadToRelative(0.13f, 0.13f, 0.34f, 0.11f)
            reflectiveQuadToRelative(0.34f, -0.14f)
            reflectiveQuadToRelative(0.13f, -0.34f)
            quadToRelative(0f, -0.21f, -0.13f, -0.34f)
            lineTo(12.15f, 11.5f)
            quadTo(12.6f, 11f, 12.8f, 10.34f)
            reflectiveQuadTo(13f, 9f)
            quadTo(13f, 7.5f, 12.04f, 6.39f)
            reflectiveQuadTo(9.65f, 5.05f)
            lineTo(11.5f, 6.9f)
            quadToRelative(0.3f, 0.3f, 0.3f, 0.7f)
            reflectiveQuadTo(11.5f, 8.3f)
            lineTo(8.3f, 11.5f)
            quadTo(8f, 11.8f, 7.6f, 11.8f)
            reflectiveQuadTo(6.9f, 11.5f)
            lineTo(5.05f, 9.65f)
            quadToRelative(0.22f, 1.43f, 1.34f, 2.39f)
            reflectiveQuadTo(9f, 13f)
            close()
            moveToRelative(2.73f, -1.28f)
            close()
          }
        }
        .build()
    return _build!!
  }

private var _build: ImageVector? = null
