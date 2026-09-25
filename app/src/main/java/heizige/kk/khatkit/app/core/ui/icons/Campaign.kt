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
public val campaign: ImageVector
  get() {
    if (_campaign != null) {
      return _campaign!!
    }
    _campaign =
      ImageVector.Builder(
          name = "campaign",
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
            moveTo(21f, 13f)
            horizontalLineTo(19f)
            quadToRelative(-0.43f, 0f, -0.71f, -0.29f)
            quadTo(18f, 12.43f, 18f, 12f)
            reflectiveQuadToRelative(0.29f, -0.71f)
            reflectiveQuadTo(19f, 11f)
            horizontalLineToRelative(2f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(22f, 12f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(21f, 13f)
            close()
            moveToRelative(-4.4f, 3.8f)
            quadToRelative(0.25f, -0.35f, 0.65f, -0.4f)
            reflectiveQuadTo(18f, 16.6f)
            lineToRelative(1.6f, 1.2f)
            quadToRelative(0.35f, 0.25f, 0.4f, 0.65f)
            reflectiveQuadTo(19.8f, 19.2f)
            reflectiveQuadToRelative(-0.65f, 0.4f)
            reflectiveQuadTo(18.4f, 19.4f)
            lineTo(16.8f, 18.2f)
            quadTo(16.45f, 17.95f, 16.4f, 17.55f)
            reflectiveQuadTo(16.6f, 16.8f)
            close()
            moveToRelative(3f, -10.6f)
            lineTo(18f, 7.4f)
            quadTo(17.65f, 7.65f, 17.25f, 7.6f)
            reflectiveQuadTo(16.6f, 7.2f)
            reflectiveQuadTo(16.4f, 6.45f)
            reflectiveQuadTo(16.8f, 5.8f)
            lineTo(18.4f, 4.6f)
            quadTo(18.75f, 4.35f, 19.15f, 4.4f)
            reflectiveQuadTo(19.8f, 4.8f)
            reflectiveQuadTo(20f, 5.55f)
            reflectiveQuadTo(19.6f, 6.2f)
            close()
            moveTo(5f, 15f)
            horizontalLineTo(4f)
            quadTo(3.18f, 15f, 2.59f, 14.41f)
            reflectiveQuadTo(2f, 13f)
            verticalLineTo(11f)
            quadTo(2f, 10.17f, 2.59f, 9.59f)
            reflectiveQuadTo(4f, 9f)
            horizontalLineTo(8f)
            lineTo(11.48f, 6.9f)
            quadToRelative(0.5f, -0.3f, 1.01f, 0f)
            reflectiveQuadTo(13f, 7.77f)
            verticalLineToRelative(8.45f)
            quadToRelative(0f, 0.57f, -0.51f, 0.88f)
            reflectiveQuadToRelative(-1.01f, 0f)
            lineTo(8f, 15f)
            horizontalLineTo(7f)
            verticalLineToRelative(3f)
            quadToRelative(0f, 0.43f, -0.29f, 0.71f)
            reflectiveQuadTo(6f, 19f)
            quadTo(5.58f, 19f, 5.29f, 18.71f)
            quadTo(5f, 18.43f, 5f, 18f)
            verticalLineTo(15f)
            close()
            moveToRelative(6f, -0.55f)
            verticalLineTo(9.55f)
            lineTo(8.55f, 11f)
            horizontalLineTo(4f)
            verticalLineToRelative(2f)
            horizontalLineTo(8.55f)
            lineTo(11f, 14.45f)
            close()
            moveToRelative(3f, 0.9f)
            verticalLineTo(8.65f)
            quadToRelative(0.68f, 0.6f, 1.09f, 1.46f)
            quadTo(15.5f, 10.98f, 15.5f, 12f)
            quadToRelative(0f, 1.02f, -0.41f, 1.89f)
            reflectiveQuadTo(14f, 15.35f)
            close()
            moveTo(7.5f, 12f)
            close()
          }
        }
        .build()
    return _campaign!!
  }

private var _campaign: ImageVector? = null
