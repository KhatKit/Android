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
public val sms: ImageVector
  get() {
    if (_sms != null) {
      return _sms!!
    }
    _sms =
      ImageVector.Builder(
          name = "sms",
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
            moveTo(6f, 18f)
            lineTo(3.7f, 20.3f)
            quadTo(3.23f, 20.78f, 2.61f, 20.51f)
            reflectiveQuadTo(2f, 19.58f)
            verticalLineTo(4f)
            quadTo(2f, 3.17f, 2.59f, 2.59f)
            reflectiveQuadTo(4f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(16f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(20f, 18f)
            horizontalLineTo(6f)
            close()
            moveTo(5.15f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(17.13f)
            lineTo(5.15f, 16f)
            close()
            moveTo(4f, 16f)
            verticalLineTo(4f)
            verticalLineTo(16f)
            close()
            moveTo(8.71f, 10.71f)
            quadTo(9f, 10.43f, 9f, 10f)
            quadTo(9f, 9.57f, 8.71f, 9.29f)
            reflectiveQuadTo(8f, 9f)
            quadTo(7.58f, 9f, 7.29f, 9.29f)
            reflectiveQuadTo(7f, 10f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(8f, 11f)
            reflectiveQuadTo(8.71f, 10.71f)
            close()
            moveToRelative(4f, 0f)
            quadTo(13f, 10.43f, 13f, 10f)
            quadTo(13f, 9.57f, 12.71f, 9.29f)
            reflectiveQuadTo(12f, 9f)
            reflectiveQuadTo(11.29f, 9.29f)
            reflectiveQuadTo(11f, 10f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(12f, 11f)
            reflectiveQuadToRelative(0.71f, -0.29f)
            close()
            moveToRelative(4f, 0f)
            quadTo(17f, 10.43f, 17f, 10f)
            quadTo(17f, 9.57f, 16.71f, 9.29f)
            reflectiveQuadTo(16f, 9f)
            reflectiveQuadTo(15.29f, 9.29f)
            reflectiveQuadTo(15f, 10f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            reflectiveQuadTo(16f, 11f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            close()
          }
        }
        .build()
    return _sms!!
  }

private var _sms: ImageVector? = null
