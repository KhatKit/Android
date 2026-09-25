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
public val editDocument: ImageVector
  get() {
    if (_edit_document != null) {
      return _edit_document!!
    }
    _edit_document =
      ImageVector.Builder(
          name = "editDocument",
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
            moveTo(14f, 21f)
            verticalLineTo(19.35f)
            quadToRelative(0f, -0.2f, 0.08f, -0.39f)
            reflectiveQuadTo(14.3f, 18.63f)
            lineToRelative(5.23f, -5.2f)
            quadToRelative(0.22f, -0.22f, 0.5f, -0.32f)
            reflectiveQuadTo(20.58f, 13f)
            quadToRelative(0.3f, 0f, 0.57f, 0.11f)
            quadToRelative(0.27f, 0.11f, 0.5f, 0.34f)
            lineToRelative(0.93f, 0.93f)
            quadToRelative(0.2f, 0.22f, 0.31f, 0.5f)
            reflectiveQuadTo(23f, 15.43f)
            reflectiveQuadToRelative(-0.1f, 0.56f)
            reflectiveQuadTo(22.58f, 16.5f)
            lineToRelative(-5.2f, 5.2f)
            quadToRelative(-0.15f, 0.15f, -0.34f, 0.23f)
            reflectiveQuadTo(16.65f, 22f)
            horizontalLineTo(15f)
            quadToRelative(-0.42f, 0f, -0.71f, -0.29f)
            quadTo(14f, 21.43f, 14f, 21f)
            close()
            moveToRelative(7.5f, -5.58f)
            lineTo(20.58f, 14.5f)
            lineToRelative(0.92f, 0.92f)
            close()
            moveToRelative(-6f, 5.08f)
            horizontalLineToRelative(0.95f)
            lineToRelative(3.03f, -3.05f)
            lineTo(18.55f, 16.52f)
            lineTo(15.5f, 19.55f)
            verticalLineTo(20.5f)
            close()
            moveTo(6f, 22f)
            quadTo(5.18f, 22f, 4.59f, 21.41f)
            reflectiveQuadTo(4f, 20f)
            verticalLineTo(4f)
            quadTo(4f, 3.17f, 4.59f, 2.59f)
            reflectiveQuadTo(6f, 2f)
            horizontalLineToRelative(7.18f)
            quadToRelative(0.4f, 0f, 0.76f, 0.15f)
            reflectiveQuadToRelative(0.64f, 0.43f)
            lineToRelative(4.85f, 4.85f)
            quadTo(19.7f, 7.7f, 19.85f, 8.06f)
            quadTo(20f, 8.42f, 20f, 8.82f)
            verticalLineToRelative(1.43f)
            quadToRelative(0f, 0.42f, -0.29f, 0.71f)
            reflectiveQuadTo(19f, 11.25f)
            reflectiveQuadTo(18.29f, 10.96f)
            quadTo(18f, 10.68f, 18f, 10.25f)
            verticalLineTo(9f)
            horizontalLineTo(14f)
            quadTo(13.58f, 9f, 13.29f, 8.71f)
            reflectiveQuadTo(13f, 8f)
            verticalLineTo(4f)
            horizontalLineTo(6f)
            verticalLineTo(20f)
            horizontalLineToRelative(5f)
            quadToRelative(0.43f, 0f, 0.71f, 0.29f)
            reflectiveQuadTo(12f, 21f)
            reflectiveQuadToRelative(-0.29f, 0.71f)
            reflectiveQuadTo(11f, 22f)
            horizontalLineTo(6f)
            close()
            moveTo(6f, 20f)
            verticalLineTo(18.9f)
            quadTo(6f, 18.5f, 6f, 18.14f)
            reflectiveQuadTo(6f, 17.5f)
            verticalLineTo(11f)
            verticalLineTo(9f)
            verticalLineTo(4f)
            verticalLineTo(20f)
            close()
            moveTo(19.03f, 16.98f)
            lineTo(18.55f, 16.52f)
            lineToRelative(0.93f, 0.93f)
            lineTo(19.03f, 16.98f)
            close()
          }
        }
        .build()
    return _edit_document!!
  }

private var _edit_document: ImageVector? = null
