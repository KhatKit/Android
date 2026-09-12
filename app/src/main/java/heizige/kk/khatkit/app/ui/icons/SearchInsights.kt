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
public val searchInsights: ImageVector
  get() {
    if (_search_insights != null) {
      return _search_insights!!
    }
    _search_insights =
      ImageVector.Builder(
          name = "searchInsights",
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
            moveTo(10f, 16f)
            quadToRelative(2.5f, 0f, 4.25f, -1.75f)
            reflectiveQuadTo(16f, 10f)
            reflectiveQuadTo(14.25f, 5.75f)
            reflectiveQuadTo(10f, 4f)
            reflectiveQuadTo(5.75f, 5.75f)
            reflectiveQuadTo(4f, 10f)
            reflectiveQuadToRelative(1.75f, 4.25f)
            reflectiveQuadTo(10f, 16f)
            close()
            moveTo(9.29f, 12.71f)
            quadTo(9f, 12.43f, 9f, 12f)
            verticalLineTo(7f)
            quadTo(9f, 6.57f, 9.29f, 6.29f)
            quadTo(9.58f, 6f, 10f, 6f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(11f, 7f)
            verticalLineToRelative(5f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(10f, 13f)
            quadTo(9.58f, 13f, 9.29f, 12.71f)
            close()
            moveToRelative(-3.5f, 0f)
            quadTo(5.5f, 12.43f, 5.5f, 12f)
            verticalLineTo(9f)
            quadTo(5.5f, 8.57f, 5.79f, 8.29f)
            reflectiveQuadTo(6.5f, 8f)
            reflectiveQuadTo(7.21f, 8.29f)
            reflectiveQuadTo(7.5f, 9f)
            verticalLineToRelative(3f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(6.5f, 13f)
            quadTo(6.08f, 13f, 5.79f, 12.71f)
            close()
            moveToRelative(7f, 0f)
            quadTo(12.5f, 12.43f, 12.5f, 12f)
            verticalLineTo(10f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(13.5f, 9f)
            reflectiveQuadToRelative(0.71f, 0.29f)
            reflectiveQuadTo(14.5f, 10f)
            verticalLineToRelative(2f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(13.5f, 13f)
            reflectiveQuadTo(12.79f, 12.71f)
            close()
            moveTo(10f, 18f)
            quadTo(6.65f, 18f, 4.33f, 15.68f)
            reflectiveQuadTo(2f, 10f)
            reflectiveQuadTo(4.33f, 4.32f)
            reflectiveQuadTo(10f, 2f)
            reflectiveQuadToRelative(5.68f, 2.32f)
            reflectiveQuadTo(18f, 10f)
            quadToRelative(0f, 1.4f, -0.44f, 2.65f)
            reflectiveQuadToRelative(-1.24f, 2.28f)
            lineTo(21.3f, 19.9f)
            quadToRelative(0.28f, 0.28f, 0.28f, 0.7f)
            reflectiveQuadTo(21.3f, 21.3f)
            reflectiveQuadToRelative(-0.7f, 0.27f)
            reflectiveQuadTo(19.9f, 21.3f)
            lineTo(14.93f, 16.33f)
            quadToRelative(-1.03f, 0.8f, -2.28f, 1.24f)
            reflectiveQuadTo(10f, 18f)
            close()
          }
        }
        .build()
    return _search_insights!!
  }

private var _search_insights: ImageVector? = null
