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
public val commentsDisabled: ImageVector
  get() {
    if (_comments_disabled != null) {
      return _comments_disabled!!
    }
    _comments_disabled =
      ImageVector.Builder(
          name = "commentsDisabled",
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
            moveTo(4f, 18f)
            quadTo(3.18f, 18f, 2.59f, 17.41f)
            reflectiveQuadTo(2f, 16f)
            verticalLineTo(4.82f)
            lineTo(1.38f, 4.2f)
            quadTo(1.08f, 3.9f, 1.08f, 3.49f)
            reflectiveQuadTo(1.38f, 2.77f)
            reflectiveQuadTo(2.09f, 2.47f)
            reflectiveQuadTo(2.8f, 2.77f)
            lineToRelative(18.4f, 18.4f)
            quadToRelative(0.3f, 0.3f, 0.3f, 0.7f)
            reflectiveQuadToRelative(-0.3f, 0.7f)
            reflectiveQuadToRelative(-0.71f, 0.3f)
            reflectiveQuadToRelative(-0.71f, -0.3f)
            lineTo(15.18f, 18f)
            horizontalLineTo(4f)
            close()
            moveTo(22f, 4f)
            verticalLineTo(17.93f)
            quadToRelative(0f, 0.35f, -0.3f, 0.47f)
            reflectiveQuadTo(21.15f, 18.27f)
            lineTo(18.88f, 16f)
            horizontalLineTo(20f)
            verticalLineTo(4f)
            horizontalLineTo(7f)
            quadTo(6.5f, 4f, 6.25f, 3.69f)
            reflectiveQuadTo(6f, 3f)
            reflectiveQuadTo(6.25f, 2.31f)
            reflectiveQuadTo(7f, 2f)
            horizontalLineTo(20f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 4f)
            close()
            moveTo(4f, 16f)
            horizontalLineToRelative(9.18f)
            lineToRelative(-2f, -2f)
            horizontalLineTo(7f)
            quadTo(6.58f, 14f, 6.29f, 13.71f)
            quadTo(6f, 13.43f, 6f, 13f)
            reflectiveQuadTo(6.29f, 12.29f)
            reflectiveQuadTo(7f, 12f)
            horizontalLineTo(9.18f)
            lineToRelative(-1f, -1f)
            horizontalLineTo(7f)
            quadTo(6.58f, 11f, 6.29f, 10.71f)
            quadTo(6f, 10.43f, 6f, 10f)
            quadTo(6f, 9.57f, 6.29f, 9.29f)
            reflectiveQuadTo(7f, 9f)
            horizontalLineTo(7.63f)
            verticalLineToRelative(1.45f)
            lineTo(4f, 6.82f)
            verticalLineTo(16f)
            close()
            moveTo(18f, 13f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(17f, 14f)
            quadToRelative(-0.43f, 0f, -0.71f, -0.29f)
            quadTo(16f, 13.43f, 16f, 13f)
            quadToRelative(0f, -0.43f, 0.29f, -0.71f)
            reflectiveQuadTo(17f, 12f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(18f, 13f)
            close()
            moveTo(17f, 11f)
            horizontalLineTo(14.3f)
            quadToRelative(-0.5f, 0f, -0.75f, -0.31f)
            reflectiveQuadTo(13.3f, 10f)
            quadToRelative(0f, -0.38f, 0.25f, -0.69f)
            reflectiveQuadTo(14.3f, 9f)
            horizontalLineTo(17f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(18f, 10f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(17f, 11f)
            close()
            moveTo(17f, 8f)
            horizontalLineTo(11.3f)
            quadTo(10.8f, 8f, 10.55f, 7.69f)
            reflectiveQuadTo(10.3f, 7f)
            quadToRelative(0f, -0.38f, 0.25f, -0.69f)
            reflectiveQuadTo(11.3f, 6f)
            horizontalLineTo(17f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(18f, 7f)
            reflectiveQuadTo(17.71f, 7.71f)
            reflectiveQuadTo(17f, 8f)
            close()
            moveTo(8.6f, 11.4f)
            close()
            moveTo(12.88f, 10f)
            close()
          }
        }
        .build()
    return _comments_disabled!!
  }

private var _comments_disabled: ImageVector? = null
