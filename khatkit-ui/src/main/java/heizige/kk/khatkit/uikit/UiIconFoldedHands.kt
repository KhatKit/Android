package heizige.kk.khatkit.uikit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
/** Material Symbols Rounded `folded_hands`（求重置用，替代 🙏 emoji）。 */
public val foldedHands: ImageVector
  get() {
    if (_foldedHands != null) {
      return _foldedHands!!
    }
    _foldedHands =
      ImageVector.Builder(
          name = "folded_hands",
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
            moveTo(17.5f, 12.75f)
            verticalLineTo(15f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(16.5f, 16f)
            reflectiveQuadTo(15.79f, 15.71f)
            reflectiveQuadTo(15.5f, 15f)
            verticalLineTo(13.27f)
            lineTo(14.38f, 11.25f)
            quadTo(14.2f, 11.38f, 14.1f, 11.58f)
            reflectiveQuadTo(14f, 12f)
            verticalLineToRelative(5.73f)
            lineToRelative(1.68f, 2.77f)
            quadToRelative(0.3f, 0.5f, 0f, 1f)
            reflectiveQuadTo(14.8f, 22f)
            quadToRelative(-0.28f, 0f, -0.5f, -0.13f)
            reflectiveQuadTo(13.95f, 21.53f)
            lineTo(12f, 18.3f)
            verticalLineTo(12f)
            quadToRelative(0f, -0.78f, 0.38f, -1.43f)
            reflectiveQuadTo(13.4f, 9.5f)
            lineTo(12f, 7.02f)
            quadTo(11.5f, 6.07f, 11.56f, 5.01f)
            reflectiveQuadTo(12.38f, 3.2f)
            lineTo(13.33f, 2.27f)
            quadTo(13.65f, 1.95f, 14.08f, 1.96f)
            reflectiveQuadToRelative(0.7f, 0.36f)
            lineTo(20.55f, 9.1f)
            quadToRelative(0.2f, 0.25f, 0.32f, 0.54f)
            reflectiveQuadToRelative(0.15f, 0.61f)
            lineTo(21.9f, 20.93f)
            quadToRelative(0.05f, 0.43f, -0.25f, 0.75f)
            reflectiveQuadTo(20.93f, 22f)
            quadToRelative(-0.4f, 0f, -0.68f, -0.26f)
            quadTo(19.98f, 21.48f, 19.93f, 21.08f)
            lineTo(19.03f, 10.4f)
            lineTo(13.95f, 4.45f)
            lineTo(13.8f, 4.6f)
            quadTo(13.55f, 4.85f, 13.51f, 5.18f)
            reflectiveQuadTo(13.63f, 5.8f)
            lineToRelative(3.88f, 6.95f)
            close()
            moveToRelative(-11f, 0f)
            lineTo(10.38f, 5.8f)
            quadTo(10.53f, 5.5f, 10.49f, 5.18f)
            reflectiveQuadTo(10.2f, 4.6f)
            lineTo(10.05f, 4.45f)
            lineTo(4.98f, 10.4f)
            lineTo(4.08f, 21.1f)
            quadTo(4.03f, 21.5f, 3.75f, 21.75f)
            quadTo(3.48f, 22f, 3.08f, 22f)
            quadTo(2.65f, 22f, 2.35f, 21.68f)
            reflectiveQuadTo(2.1f, 20.93f)
            lineTo(2.98f, 10.25f)
            quadTo(3f, 9.92f, 3.13f, 9.64f)
            reflectiveQuadTo(3.45f, 9.1f)
            lineTo(9.23f, 2.32f)
            quadTo(9.5f, 1.97f, 9.94f, 1.96f)
            reflectiveQuadTo(10.7f, 2.27f)
            lineTo(11.63f, 3.2f)
            quadToRelative(0.75f, 0.75f, 0.81f, 1.81f)
            reflectiveQuadTo(12f, 7.02f)
            lineTo(10.6f, 9.5f)
            quadToRelative(0.65f, 0.42f, 1.02f, 1.07f)
            reflectiveQuadTo(12f, 12f)
            verticalLineToRelative(6.3f)
            lineToRelative(-1.95f, 3.22f)
            quadTo(9.93f, 21.75f, 9.69f, 21.88f)
            reflectiveQuadTo(9.18f, 22f)
            quadTo(8.6f, 22f, 8.31f, 21.5f)
            reflectiveQuadToRelative(0.01f, -1f)
            lineTo(10f, 17.73f)
            verticalLineTo(12f)
            quadTo(10f, 11.77f, 9.9f, 11.58f)
            reflectiveQuadTo(9.63f, 11.25f)
            lineTo(8.5f, 13.27f)
            verticalLineTo(15f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(7.5f, 16f)
            quadTo(7.08f, 16f, 6.79f, 15.71f)
            reflectiveQuadTo(6.5f, 15f)
            verticalLineTo(12.75f)
            close()
          }
        }
        .build()
    return _foldedHands!!
  }

private var _foldedHands: ImageVector? = null
