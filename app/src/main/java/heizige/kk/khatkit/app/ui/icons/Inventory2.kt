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
public val inventory2: ImageVector
  get() {
    if (_inventory_2 != null) {
      return _inventory_2!!
    }
    _inventory_2 =
      ImageVector.Builder(
          name = "inventory2",
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
            moveTo(5f, 22f)
            quadTo(4.18f, 22f, 3.59f, 21.41f)
            reflectiveQuadTo(3f, 20f)
            verticalLineTo(8.73f)
            quadTo(2.55f, 8.45f, 2.28f, 8.01f)
            reflectiveQuadTo(2f, 7f)
            verticalLineTo(4f)
            quadTo(2f, 3.17f, 2.59f, 2.59f)
            reflectiveQuadTo(4f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            verticalLineTo(7f)
            quadToRelative(0f, 0.57f, -0.27f, 1.01f)
            reflectiveQuadTo(21f, 8.73f)
            verticalLineTo(20f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 22f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 9f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            close()
            moveTo(4f, 7f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(7f)
            close()
            moveToRelative(6f, 7f)
            horizontalLineToRelative(4f)
            quadToRelative(0.43f, 0f, 0.71f, -0.29f)
            quadTo(15f, 13.43f, 15f, 13f)
            reflectiveQuadTo(14.71f, 12.29f)
            reflectiveQuadTo(14f, 12f)
            horizontalLineTo(10f)
            quadTo(9.58f, 12f, 9.29f, 12.29f)
            reflectiveQuadTo(9f, 13f)
            reflectiveQuadToRelative(0.29f, 0.71f)
            quadTo(9.58f, 14f, 10f, 14f)
            close()
            moveToRelative(2f, 0.5f)
            close()
          }
        }
        .build()
    return _inventory_2!!
  }

private var _inventory_2: ImageVector? = null
