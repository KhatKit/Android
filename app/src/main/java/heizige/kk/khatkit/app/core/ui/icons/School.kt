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
public val school: ImageVector
  get() {
    if (_school != null) {
      return _school!!
    }
    _school =
      ImageVector.Builder(
          name = "school",
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
            moveTo(6.05f, 17.77f)
            quadTo(5.55f, 17.5f, 5.28f, 17.04f)
            reflectiveQuadTo(5f, 16f)
            verticalLineTo(11.2f)
            lineTo(2.6f, 9.88f)
            quadTo(2.33f, 9.73f, 2.2f, 9.5f)
            quadTo(2.08f, 9.27f, 2.08f, 9f)
            reflectiveQuadTo(2.2f, 8.5f)
            quadTo(2.33f, 8.27f, 2.6f, 8.13f)
            lineToRelative(8.45f, -4.6f)
            quadTo(11.28f, 3.4f, 11.51f, 3.34f)
            reflectiveQuadTo(12f, 3.27f)
            reflectiveQuadToRelative(0.49f, 0.06f)
            reflectiveQuadToRelative(0.46f, 0.19f)
            lineToRelative(9.53f, 5.2f)
            quadToRelative(0.25f, 0.13f, 0.39f, 0.36f)
            reflectiveQuadTo(23f, 9.6f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(22f, 17f)
            reflectiveQuadTo(21.29f, 16.71f)
            quadTo(21f, 16.43f, 21f, 16f)
            verticalLineTo(10.1f)
            lineToRelative(-2f, 1.1f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.57f, -0.27f, 1.04f)
            reflectiveQuadToRelative(-0.78f, 0.74f)
            lineToRelative(-5f, 2.7f)
            quadToRelative(-0.22f, 0.13f, -0.46f, 0.19f)
            reflectiveQuadTo(12f, 20.73f)
            reflectiveQuadTo(11.51f, 20.66f)
            reflectiveQuadTo(11.05f, 20.48f)
            lineToRelative(-5f, -2.7f)
            close()
            moveTo(12f, 12.7f)
            lineTo(18.85f, 9f)
            lineTo(12f, 5.3f)
            lineTo(5.15f, 9f)
            lineTo(12f, 12.7f)
            close()
            moveToRelative(0f, 6.03f)
            lineToRelative(5f, -2.7f)
            verticalLineTo(12.25f)
            lineToRelative(-4.02f, 2.22f)
            quadTo(12.75f, 14.6f, 12.5f, 14.66f)
            reflectiveQuadTo(12f, 14.73f)
            reflectiveQuadTo(11.5f, 14.66f)
            reflectiveQuadTo(11.03f, 14.48f)
            lineTo(7f, 12.25f)
            verticalLineToRelative(3.77f)
            lineToRelative(5f, 2.7f)
            close()
            moveTo(12f, 12.7f)
            close()
            moveToRelative(0f, 3.03f)
            close()
            moveToRelative(0f, 0f)
            close()
          }
        }
        .build()
    return _school!!
  }

private var _school: ImageVector? = null
