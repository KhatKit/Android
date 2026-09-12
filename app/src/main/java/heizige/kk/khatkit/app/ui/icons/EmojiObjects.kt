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
public val emojiObjects: ImageVector
  get() {
    if (_emoji_objects != null) {
      return _emoji_objects!!
    }
    _emoji_objects =
      ImageVector.Builder(
          name = "emojiObjects",
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
            moveTo(12f, 22f)
            quadToRelative(-0.65f, 0f, -1.17f, -0.31f)
            reflectiveQuadTo(10f, 20.85f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            quadTo(8f, 19.68f, 8f, 18.85f)
            verticalLineTo(15.3f)
            quadTo(6.53f, 14.33f, 5.64f, 12.73f)
            reflectiveQuadTo(4.75f, 9.25f)
            quadToRelative(0f, -3.03f, 2.11f, -5.14f)
            reflectiveQuadTo(12f, 2f)
            reflectiveQuadToRelative(5.14f, 2.11f)
            reflectiveQuadToRelative(2.11f, 5.14f)
            quadToRelative(0f, 1.92f, -0.89f, 3.5f)
            reflectiveQuadTo(16f, 15.3f)
            verticalLineToRelative(3.55f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(14f, 20.85f)
            quadToRelative(-0.3f, 0.52f, -0.82f, 0.84f)
            reflectiveQuadTo(12f, 22f)
            close()
            moveTo(10f, 18.85f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-0.9f)
            horizontalLineTo(10f)
            verticalLineToRelative(0.9f)
            close()
            moveToRelative(0f, -1.9f)
            horizontalLineToRelative(4f)
            verticalLineTo(16f)
            horizontalLineTo(10f)
            verticalLineToRelative(0.95f)
            close()
            moveTo(9.8f, 14f)
            horizontalLineToRelative(1.45f)
            verticalLineTo(11.3f)
            lineTo(9.58f, 9.63f)
            quadTo(9.35f, 9.4f, 9.35f, 9.1f)
            reflectiveQuadTo(9.58f, 8.57f)
            reflectiveQuadTo(10.1f, 8.35f)
            reflectiveQuadToRelative(0.52f, 0.22f)
            lineTo(12f, 9.95f)
            lineTo(13.38f, 8.57f)
            quadTo(13.6f, 8.35f, 13.9f, 8.35f)
            reflectiveQuadToRelative(0.53f, 0.22f)
            reflectiveQuadTo(14.65f, 9.1f)
            reflectiveQuadTo(14.43f, 9.63f)
            lineTo(12.75f, 11.3f)
            verticalLineTo(14f)
            horizontalLineTo(14.2f)
            quadToRelative(1.35f, -0.65f, 2.2f, -1.91f)
            quadToRelative(0.85f, -1.26f, 0.85f, -2.84f)
            quadToRelative(0f, -2.2f, -1.52f, -3.73f)
            reflectiveQuadTo(12f, 4f)
            reflectiveQuadTo(8.28f, 5.52f)
            reflectiveQuadTo(6.75f, 9.25f)
            quadToRelative(0f, 1.57f, 0.85f, 2.84f)
            reflectiveQuadTo(9.8f, 14f)
            close()
            moveTo(12f, 9.95f)
            close()
            moveTo(12f, 9f)
            close()
          }
        }
        .build()
    return _emoji_objects!!
  }

private var _emoji_objects: ImageVector? = null
