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
public val extension: ImageVector
  get() {
    if (_extension != null) {
      return _extension!!
    }
    _extension =
      ImageVector.Builder(
          name = "extension",
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
            moveTo(5f, 19f)
            horizontalLineTo(18f)
            verticalLineTo(15.03f)
            quadToRelative(0f, -0.28f, 0.14f, -0.53f)
            quadToRelative(0.14f, -0.25f, 0.41f, -0.38f)
            lineToRelative(0.57f, -0.28f)
            quadToRelative(0.4f, -0.2f, 0.64f, -0.55f)
            reflectiveQuadTo(20f, 12.5f)
            quadToRelative(0f, -0.43f, -0.24f, -0.79f)
            reflectiveQuadTo(19.13f, 11.15f)
            lineTo(18.58f, 10.9f)
            quadTo(18.3f, 10.77f, 18.15f, 10.54f)
            reflectiveQuadTo(18f, 10f)
            verticalLineTo(6f)
            horizontalLineTo(14.08f)
            quadTo(13.7f, 6f, 13.41f, 5.76f)
            reflectiveQuadTo(13.08f, 5.15f)
            lineTo(12.95f, 4.3f)
            quadTo(12.88f, 3.75f, 12.46f, 3.38f)
            reflectiveQuadTo(11.5f, 3f)
            quadTo(10.93f, 3f, 10.51f, 3.38f)
            reflectiveQuadTo(10.03f, 4.3f)
            lineTo(9.9f, 5.15f)
            quadTo(9.85f, 5.52f, 9.56f, 5.76f)
            quadTo(9.28f, 6f, 8.9f, 6f)
            horizontalLineTo(5f)
            verticalLineTo(8.15f)
            quadToRelative(1.4f, 0.53f, 2.2f, 1.7f)
            quadTo(8f, 11.02f, 8f, 12.5f)
            quadTo(8f, 14f, 7.2f, 15.18f)
            reflectiveQuadTo(5f, 16.88f)
            verticalLineTo(19f)
            close()
            moveToRelative(0f, 2f)
            quadTo(4.15f, 21f, 3.58f, 20.43f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(15.88f)
            quadTo(3f, 15.6f, 3.2f, 15.39f)
            reflectiveQuadTo(3.68f, 15.13f)
            quadToRelative(0.98f, -0.2f, 1.65f, -0.91f)
            reflectiveQuadTo(6f, 12.5f)
            quadTo(6f, 11.52f, 5.33f, 10.83f)
            reflectiveQuadTo(3.68f, 9.9f)
            quadTo(3.4f, 9.82f, 3.2f, 9.61f)
            reflectiveQuadTo(3f, 9.13f)
            verticalLineTo(6f)
            quadTo(3f, 5.18f, 3.59f, 4.59f)
            reflectiveQuadTo(5f, 4f)
            horizontalLineTo(8.05f)
            quadTo(8.23f, 2.72f, 9.2f, 1.86f)
            reflectiveQuadTo(11.5f, 1f)
            quadToRelative(1.3f, 0f, 2.28f, 0.86f)
            reflectiveQuadTo(14.95f, 4f)
            horizontalLineTo(18f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(20f, 5.18f, 20f, 6f)
            verticalLineTo(9.35f)
            quadToRelative(0.9f, 0.45f, 1.45f, 1.3f)
            reflectiveQuadTo(22f, 12.5f)
            quadToRelative(0f, 1.02f, -0.55f, 1.88f)
            reflectiveQuadTo(20f, 15.65f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.85f, -0.59f, 1.43f)
            reflectiveQuadTo(18f, 21f)
            horizontalLineTo(5f)
            close()
            moveToRelative(7.5f, -8.5f)
            close()
          }
        }
        .build()
    return _extension!!
  }

private var _extension: ImageVector? = null
