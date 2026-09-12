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
public val autoFixHigh: ImageVector
  get() {
    if (_auto_fix_high != null) {
      return _auto_fix_high!!
    }
    _auto_fix_high =
      ImageVector.Builder(
          name = "autoFixHigh",
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
            moveTo(19.05f, 4.95f)
            lineTo(17.5f, 4.22f)
            quadTo(17.35f, 4.15f, 17.35f, 4f)
            reflectiveQuadTo(17.5f, 3.77f)
            lineTo(19.05f, 3.05f)
            lineTo(19.78f, 1.5f)
            quadTo(19.85f, 1.35f, 20f, 1.35f)
            reflectiveQuadTo(20.23f, 1.5f)
            lineToRelative(0.73f, 1.55f)
            lineTo(22.5f, 3.77f)
            quadTo(22.65f, 3.85f, 22.65f, 4f)
            reflectiveQuadTo(22.5f, 4.22f)
            lineTo(20.95f, 4.95f)
            lineTo(20.23f, 6.5f)
            quadTo(20.15f, 6.65f, 20f, 6.65f)
            reflectiveQuadTo(19.78f, 6.5f)
            lineTo(19.05f, 4.95f)
            close()
            moveToRelative(-11.5f, 0f)
            lineTo(6f, 4.22f)
            quadTo(5.85f, 4.15f, 5.85f, 4f)
            reflectiveQuadTo(6f, 3.77f)
            lineTo(7.55f, 3.05f)
            lineTo(8.28f, 1.5f)
            quadTo(8.35f, 1.35f, 8.5f, 1.35f)
            reflectiveQuadTo(8.73f, 1.5f)
            lineTo(9.45f, 3.05f)
            lineTo(11f, 3.77f)
            quadTo(11.15f, 3.85f, 11.15f, 4f)
            reflectiveQuadTo(11f, 4.22f)
            lineTo(9.45f, 4.95f)
            lineTo(8.73f, 6.5f)
            quadTo(8.65f, 6.65f, 8.5f, 6.65f)
            reflectiveQuadTo(8.28f, 6.5f)
            lineTo(7.55f, 4.95f)
            close()
            moveToRelative(11.5f, 11.5f)
            lineTo(17.5f, 15.73f)
            quadTo(17.35f, 15.65f, 17.35f, 15.5f)
            reflectiveQuadTo(17.5f, 15.28f)
            lineToRelative(1.55f, -0.73f)
            lineTo(19.78f, 13f)
            quadTo(19.85f, 12.85f, 20f, 12.85f)
            reflectiveQuadTo(20.23f, 13f)
            lineToRelative(0.73f, 1.55f)
            lineToRelative(1.55f, 0.73f)
            quadToRelative(0.15f, 0.07f, 0.15f, 0.22f)
            reflectiveQuadTo(22.5f, 15.73f)
            lineToRelative(-1.55f, 0.72f)
            lineTo(20.23f, 18f)
            quadTo(20.15f, 18.15f, 20f, 18.15f)
            reflectiveQuadTo(19.78f, 18f)
            lineTo(19.05f, 16.45f)
            close()
            moveTo(5.1f, 21.7f)
            lineTo(2.3f, 18.9f)
            quadTo(2f, 18.6f, 2f, 18.18f)
            reflectiveQuadTo(2.3f, 17.45f)
            lineTo(13.45f, 6.3f)
            quadTo(13.75f, 6f, 14.18f, 6f)
            reflectiveQuadTo(14.9f, 6.3f)
            lineToRelative(2.8f, 2.8f)
            quadTo(18f, 9.4f, 18f, 9.82f)
            reflectiveQuadToRelative(-0.3f, 0.72f)
            lineTo(6.55f, 21.7f)
            quadTo(6.25f, 22f, 5.83f, 22f)
            reflectiveQuadTo(5.1f, 21.7f)
            close()
            moveTo(5.85f, 19.6f)
            lineTo(13f, 12.4f)
            lineTo(11.6f, 11f)
            lineTo(4.4f, 18.15f)
            lineTo(5.85f, 19.6f)
            close()
          }
        }
        .build()
    return _auto_fix_high!!
  }

private var _auto_fix_high: ImageVector? = null
