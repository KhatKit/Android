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
public val power: ImageVector
  get() {
    if (_power != null) {
      return _power!!
    }
    _power =
      ImageVector.Builder(
          name = "power",
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
            moveTo(11.5f, 19f)
            horizontalLineToRelative(1f)
            verticalLineTo(17.15f)
            lineTo(16f, 13.65f)
            verticalLineTo(9f)
            horizontalLineTo(8f)
            verticalLineToRelative(4.65f)
            lineToRelative(3.5f, 3.5f)
            verticalLineTo(19f)
            close()
            moveToRelative(-2f, 1f)
            verticalLineTo(18f)
            lineTo(6.58f, 15.08f)
            quadTo(6.3f, 14.8f, 6.15f, 14.44f)
            reflectiveQuadTo(6f, 13.68f)
            verticalLineTo(9f)
            quadTo(6f, 8.17f, 6.59f, 7.59f)
            reflectiveQuadTo(8f, 7f)
            horizontalLineTo(9f)
            lineTo(8f, 8f)
            verticalLineTo(4f)
            quadTo(8f, 3.57f, 8.29f, 3.29f)
            quadTo(8.58f, 3f, 9f, 3f)
            quadTo(9.43f, 3f, 9.71f, 3.29f)
            reflectiveQuadTo(10f, 4f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(4f)
            quadTo(14f, 3.57f, 14.29f, 3.29f)
            reflectiveQuadTo(15f, 3f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(16f, 4f)
            verticalLineTo(8f)
            lineTo(15f, 7f)
            horizontalLineToRelative(1f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(18f, 9f)
            verticalLineToRelative(4.67f)
            quadToRelative(0f, 0.4f, -0.15f, 0.76f)
            reflectiveQuadToRelative(-0.43f, 0.64f)
            lineTo(14.5f, 18f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(13.5f, 21f)
            horizontalLineToRelative(-3f)
            quadTo(10.08f, 21f, 9.79f, 20.71f)
            quadTo(9.5f, 20.43f, 9.5f, 20f)
            close()
            moveTo(12f, 14f)
            close()
          }
        }
        .build()
    return _power!!
  }

private var _power: ImageVector? = null
