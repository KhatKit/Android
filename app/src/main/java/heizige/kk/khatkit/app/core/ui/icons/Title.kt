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
public val title: ImageVector
  get() {
    if (_title != null) {
      return _title!!
    }
    _title =
      ImageVector.Builder(
          name = "title",
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
            moveTo(10.5f, 7f)
            horizontalLineToRelative(-4f)
            quadTo(5.88f, 7f, 5.44f, 6.56f)
            reflectiveQuadTo(5f, 5.5f)
            reflectiveQuadTo(5.44f, 4.44f)
            reflectiveQuadTo(6.5f, 4f)
            horizontalLineToRelative(11f)
            quadToRelative(0.63f, 0f, 1.06f, 0.44f)
            reflectiveQuadTo(19f, 5.5f)
            reflectiveQuadTo(18.56f, 6.56f)
            reflectiveQuadTo(17.5f, 7f)
            horizontalLineToRelative(-4f)
            verticalLineTo(18.5f)
            quadToRelative(0f, 0.63f, -0.44f, 1.06f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadTo(10.94f, 19.56f)
            reflectiveQuadTo(10.5f, 18.5f)
            verticalLineTo(7f)
            close()
          }
        }
        .build()
    return _title!!
  }

private var _title: ImageVector? = null
