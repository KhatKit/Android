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
public val cleaningServices: ImageVector
  get() {
    if (_cleaning_services != null) {
      return _cleaning_services!!
    }
    _cleaning_services =
      ImageVector.Builder(
          name = "cleaningServices",
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
            moveTo(5f, 23f)
            quadTo(4.18f, 23f, 3.59f, 22.41f)
            reflectiveQuadTo(3f, 21f)
            verticalLineTo(16f)
            quadTo(3f, 13.93f, 4.46f, 12.46f)
            reflectiveQuadTo(8f, 11f)
            horizontalLineTo(9f)
            verticalLineTo(3f)
            quadTo(9f, 2.17f, 9.59f, 1.59f)
            reflectiveQuadTo(11f, 1f)
            horizontalLineToRelative(2f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(15f, 3f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(1f)
            quadToRelative(2.07f, 0f, 3.54f, 1.46f)
            quadTo(21f, 13.93f, 21f, 16f)
            verticalLineToRelative(5f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 23f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 21f)
            horizontalLineTo(7f)
            verticalLineTo(18f)
            quadTo(7f, 17.58f, 7.29f, 17.29f)
            reflectiveQuadTo(8f, 17f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(9f, 18f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(18f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(12f, 17f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(13f, 18f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(18f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(16f, 17f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(17f, 18f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineTo(16f)
            quadToRelative(0f, -1.25f, -0.88f, -2.13f)
            reflectiveQuadTo(16f, 13f)
            horizontalLineTo(8f)
            quadTo(6.75f, 13f, 5.88f, 13.88f)
            reflectiveQuadTo(5f, 16f)
            verticalLineToRelative(5f)
            close()
            moveTo(13f, 11f)
            verticalLineTo(3f)
            horizontalLineTo(11f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(2f)
            close()
            moveToRelative(0f, 0f)
            horizontalLineTo(11f)
            horizontalLineToRelative(2f)
            close()
          }
        }
        .build()
    return _cleaning_services!!
  }

private var _cleaning_services: ImageVector? = null
