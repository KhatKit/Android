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
public val deleteForever: ImageVector
  get() {
    if (_delete_forever != null) {
      return _delete_forever!!
    }
    _delete_forever =
      ImageVector.Builder(
          name = "deleteForever",
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
            moveTo(7f, 21f)
            quadTo(6.18f, 21f, 5.59f, 20.41f)
            reflectiveQuadTo(5f, 19f)
            verticalLineTo(6f)
            quadTo(4.58f, 6f, 4.29f, 5.71f)
            quadTo(4f, 5.43f, 4f, 5f)
            reflectiveQuadTo(4.29f, 4.29f)
            reflectiveQuadTo(5f, 4f)
            horizontalLineTo(9f)
            quadTo(9f, 3.57f, 9.29f, 3.29f)
            quadTo(9.58f, 3f, 10f, 3f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(15f, 4f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(20f, 5f)
            reflectiveQuadTo(19.71f, 5.71f)
            reflectiveQuadTo(19f, 6f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(17f, 21f)
            horizontalLineTo(7f)
            close()
            moveTo(17f, 6f)
            horizontalLineTo(7f)
            verticalLineTo(19f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            close()
            moveTo(7f, 6f)
            verticalLineTo(19f)
            verticalLineTo(6f)
            close()
            moveToRelative(5f, 7.9f)
            lineToRelative(1.9f, 1.9f)
            quadToRelative(0.28f, 0.28f, 0.7f, 0.28f)
            reflectiveQuadTo(15.3f, 15.8f)
            quadToRelative(0.28f, -0.27f, 0.28f, -0.7f)
            reflectiveQuadTo(15.3f, 14.4f)
            lineTo(13.4f, 12.5f)
            lineToRelative(1.9f, -1.9f)
            quadToRelative(0.28f, -0.28f, 0.28f, -0.7f)
            quadToRelative(0f, -0.42f, -0.28f, -0.7f)
            reflectiveQuadTo(14.6f, 8.92f)
            reflectiveQuadTo(13.9f, 9.2f)
            lineTo(12f, 11.1f)
            lineTo(10.1f, 9.2f)
            quadTo(9.83f, 8.92f, 9.4f, 8.92f)
            reflectiveQuadTo(8.7f, 9.2f)
            reflectiveQuadTo(8.43f, 9.9f)
            quadToRelative(0f, 0.43f, 0.28f, 0.7f)
            lineToRelative(1.9f, 1.9f)
            lineTo(8.7f, 14.4f)
            quadTo(8.43f, 14.68f, 8.43f, 15.1f)
            reflectiveQuadTo(8.7f, 15.8f)
            quadToRelative(0.27f, 0.28f, 0.7f, 0.28f)
            reflectiveQuadTo(10.1f, 15.8f)
            lineTo(12f, 13.9f)
            close()
          }
        }
        .build()
    return _delete_forever!!
  }

private var _delete_forever: ImageVector? = null
