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
public val calendarAddOn: ImageVector
  get() {
    if (_calendar_add_on != null) {
      return _calendar_add_on!!
    }
    _calendar_add_on =
      ImageVector.Builder(
          name = "calendarAddOn",
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
            moveTo(17f, 19f)
            horizontalLineTo(15f)
            quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
            quadTo(14f, 18.43f, 14f, 18f)
            reflectiveQuadToRelative(0.29f, -0.71f)
            reflectiveQuadTo(15f, 17f)
            horizontalLineToRelative(2f)
            verticalLineTo(15f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(18f, 14f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(19f, 15f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 18f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(21f, 19f)
            horizontalLineTo(19f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(18f, 22f)
            reflectiveQuadTo(17.29f, 21.71f)
            quadTo(17f, 21.43f, 17f, 21f)
            verticalLineTo(19f)
            close()
            moveTo(5f, 20f)
            quadTo(4.18f, 20f, 3.59f, 19.41f)
            reflectiveQuadTo(3f, 18f)
            verticalLineTo(6f)
            quadTo(3f, 5.18f, 3.59f, 4.59f)
            reflectiveQuadTo(5f, 4f)
            horizontalLineTo(6f)
            verticalLineTo(3f)
            quadTo(6f, 2.57f, 6.29f, 2.29f)
            reflectiveQuadTo(7f, 2f)
            reflectiveQuadTo(7.71f, 2.29f)
            reflectiveQuadTo(8f, 3f)
            verticalLineTo(4f)
            horizontalLineToRelative(6f)
            verticalLineTo(3f)
            quadTo(14f, 2.57f, 14.29f, 2.29f)
            reflectiveQuadTo(15f, 2f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(16f, 3f)
            verticalLineTo(4f)
            horizontalLineToRelative(1f)
            quadToRelative(0.82f, 0f, 1.41f, 0.59f)
            quadTo(19f, 5.18f, 19f, 6f)
            verticalLineToRelative(5f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(18f, 12f)
            reflectiveQuadTo(17.29f, 11.71f)
            quadTo(17f, 11.43f, 17f, 11f)
            verticalLineTo(10f)
            horizontalLineTo(5f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(6f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(12f, 19f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(11f, 20f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 8f)
            horizontalLineTo(17f)
            verticalLineTo(6f)
            horizontalLineTo(5f)
            verticalLineTo(8f)
            close()
            moveTo(5f, 8f)
            verticalLineTo(6f)
            verticalLineTo(8f)
            close()
          }
        }
        .build()
    return _calendar_add_on!!
  }

private var _calendar_add_on: ImageVector? = null
