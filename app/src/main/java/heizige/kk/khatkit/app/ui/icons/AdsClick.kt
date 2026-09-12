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
public val adsClick: ImageVector
  get() {
    if (_ads_click != null) {
      return _ads_click!!
    }
    _ads_click =
      ImageVector.Builder(
          name = "adsClick",
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
            moveTo(12f, 8f)
            quadTo(10.35f, 8f, 9.18f, 9.17f)
            reflectiveQuadTo(8f, 12f)
            quadToRelative(0f, 1.13f, 0.56f, 2.07f)
            quadToRelative(0.56f, 0.95f, 1.56f, 1.47f)
            quadToRelative(0.4f, 0.2f, 0.56f, 0.59f)
            reflectiveQuadToRelative(-0.01f, 0.79f)
            quadToRelative(-0.18f, 0.35f, -0.53f, 0.52f)
            reflectiveQuadToRelative(-0.7f, 0f)
            quadTo(7.88f, 16.7f, 6.94f, 15.23f)
            reflectiveQuadTo(6f, 12f)
            quadTo(6f, 9.5f, 7.75f, 7.75f)
            reflectiveQuadTo(12f, 6f)
            quadToRelative(1.78f, 0f, 3.26f, 0.94f)
            reflectiveQuadTo(17.48f, 9.5f)
            quadToRelative(0.15f, 0.35f, -0.01f, 0.7f)
            reflectiveQuadToRelative(-0.51f, 0.5f)
            quadToRelative(-0.4f, 0.18f, -0.8f, 0f)
            reflectiveQuadToRelative(-0.6f, -0.57f)
            quadToRelative(-0.53f, -1f, -1.47f, -1.56f)
            reflectiveQuadTo(12f, 8f)
            close()
            moveTo(12f, 4f)
            quadTo(8.65f, 4f, 6.33f, 6.32f)
            reflectiveQuadTo(4f, 12f)
            quadToRelative(0f, 3.15f, 2.08f, 5.4f)
            reflectiveQuadToRelative(5.2f, 2.55f)
            quadTo(11.7f, 20f, 12.01f, 20.33f)
            reflectiveQuadToRelative(0.29f, 0.75f)
            reflectiveQuadToRelative(-0.31f, 0.7f)
            reflectiveQuadToRelative(-0.71f, 0.25f)
            quadTo(9.33f, 21.9f, 7.64f, 21.05f)
            reflectiveQuadTo(4.69f, 18.84f)
            quadTo(3.43f, 17.48f, 2.71f, 15.71f)
            reflectiveQuadTo(2f, 12f)
            quadTo(2f, 9.92f, 2.79f, 8.1f)
            quadTo(3.58f, 6.27f, 4.93f, 4.93f)
            quadTo(6.28f, 3.57f, 8.1f, 2.79f)
            quadTo(9.93f, 2f, 12f, 2f)
            quadToRelative(3.93f, 0f, 6.84f, 2.67f)
            reflectiveQuadToRelative(3.19f, 6.6f)
            quadToRelative(0.05f, 0.4f, -0.24f, 0.69f)
            reflectiveQuadToRelative(-0.71f, 0.31f)
            reflectiveQuadTo(20.31f, 12f)
            quadTo(19.98f, 11.7f, 19.93f, 11.27f)
            quadToRelative(-0.38f, -3f, -2.61f, -5.14f)
            reflectiveQuadTo(12f, 4f)
            close()
            moveToRelative(7.55f, 17.5f)
            lineToRelative(-3.3f, -3.27f)
            lineTo(15.5f, 20.5f)
            quadToRelative(-0.13f, 0.35f, -0.47f, 0.34f)
            quadTo(14.68f, 20.83f, 14.55f, 20.48f)
            lineTo(12.28f, 12.9f)
            quadToRelative(-0.1f, -0.27f, 0.13f, -0.5f)
            quadToRelative(0.22f, -0.22f, 0.5f, -0.13f)
            lineToRelative(7.58f, 2.28f)
            quadToRelative(0.35f, 0.13f, 0.36f, 0.48f)
            reflectiveQuadTo(20.5f, 15.5f)
            lineToRelative(-2.27f, 0.75f)
            lineToRelative(3.3f, 3.3f)
            quadToRelative(0.43f, 0.43f, 0.43f, 0.97f)
            quadToRelative(0f, 0.55f, -0.43f, 0.98f)
            reflectiveQuadToRelative(-0.99f, 0.43f)
            reflectiveQuadTo(19.55f, 21.5f)
            close()
          }
        }
        .build()
    return _ads_click!!
  }

private var _ads_click: ImageVector? = null
