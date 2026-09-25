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
public val settingsBackupRestore: ImageVector
  get() {
    if (_settings_backup_restore != null) {
      return _settings_backup_restore!!
    }
    _settings_backup_restore =
      ImageVector.Builder(
          name = "settingsBackupRestore",
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
            moveTo(12f, 21f)
            quadTo(8.85f, 21f, 6.43f, 19.09f)
            quadTo(4f, 17.18f, 3.28f, 14.2f)
            quadTo(3.18f, 13.83f, 3.43f, 13.51f)
            reflectiveQuadTo(4.1f, 13.15f)
            quadTo(4.5f, 13.1f, 4.83f, 13.3f)
            reflectiveQuadToRelative(0.45f, 0.6f)
            quadToRelative(0.6f, 2.25f, 2.47f, 3.68f)
            reflectiveQuadTo(12f, 19f)
            quadToRelative(2.93f, 0f, 4.96f, -2.04f)
            quadTo(19f, 14.93f, 19f, 12f)
            quadTo(19f, 9.07f, 16.96f, 7.04f)
            reflectiveQuadTo(12f, 5f)
            quadTo(10.28f, 5f, 8.78f, 5.8f)
            reflectiveQuadTo(6.25f, 8f)
            horizontalLineTo(8f)
            quadTo(8.43f, 8f, 8.71f, 8.29f)
            reflectiveQuadTo(9f, 9f)
            quadTo(9f, 9.42f, 8.71f, 9.71f)
            reflectiveQuadTo(8f, 10f)
            horizontalLineTo(4f)
            quadTo(3.58f, 10f, 3.29f, 9.71f)
            reflectiveQuadTo(3f, 9f)
            verticalLineTo(5f)
            quadTo(3f, 4.57f, 3.29f, 4.29f)
            reflectiveQuadTo(4f, 4f)
            reflectiveQuadTo(4.71f, 4.29f)
            reflectiveQuadTo(5f, 5f)
            verticalLineTo(6.35f)
            quadTo(6.28f, 4.75f, 8.11f, 3.88f)
            reflectiveQuadTo(12f, 3f)
            quadToRelative(1.88f, 0f, 3.51f, 0.71f)
            reflectiveQuadToRelative(2.85f, 1.93f)
            reflectiveQuadToRelative(1.93f, 2.85f)
            reflectiveQuadTo(21f, 12f)
            reflectiveQuadToRelative(-0.71f, 3.51f)
            reflectiveQuadToRelative(-1.93f, 2.85f)
            reflectiveQuadToRelative(-2.85f, 1.93f)
            reflectiveQuadTo(12f, 21f)
            close()
            moveToRelative(0f, -7f)
            quadToRelative(-0.82f, 0f, -1.41f, -0.59f)
            reflectiveQuadTo(10f, 12f)
            reflectiveQuadToRelative(0.59f, -1.41f)
            reflectiveQuadTo(12f, 10f)
            reflectiveQuadToRelative(1.41f, 0.59f)
            quadTo(14f, 11.18f, 14f, 12f)
            reflectiveQuadToRelative(-0.59f, 1.41f)
            reflectiveQuadTo(12f, 14f)
            close()
          }
        }
        .build()
    return _settings_backup_restore!!
  }

private var _settings_backup_restore: ImageVector? = null
