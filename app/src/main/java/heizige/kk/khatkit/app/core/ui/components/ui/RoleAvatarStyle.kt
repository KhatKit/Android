package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import kotlin.math.abs

/** The same stable palette used by market cards, so a role keeps one visual identity everywhere. */
val roleAvatarPalette = listOf(
    Color(0xFFC27B7B), Color(0xFFA98BC4), Color(0xFF7FA6C9), Color(0xFF7FB5AB),
    Color(0xFF9CB07C), Color(0xFFC9A06A), Color(0xFFB09287), Color(0xFF8B9BAA),
)

fun roleAvatarColor(seed: String): Color =
    roleAvatarPalette[abs(seed.hashCode()) % roleAvatarPalette.size]

fun roleAvatarColor(seed: String, override: String?): Color {
    val value = override?.trim()?.takeIf { it.isNotEmpty() }
        ?: return roleAvatarColor(seed)
    return runCatching {
        Color(android.graphics.Color.parseColor(value))
    }.getOrElse { roleAvatarColor(seed) }
}

val roleAvatarShapeNames = listOf(
    "Circle", "Arch", "Arrow", "Boom", "Bun", "Burst", "ClamShell",
    "Clover4Leaf", "Clover8Leaf", "Cookie12Sided", "Cookie4Sided",
    "Cookie6Sided", "Cookie7Sided", "Cookie9Sided", "Diamond", "Fan",
    "Flower", "Gem", "Ghostish", "Heart", "Oval", "Pentagon", "Pill",
    "PixelCircle", "PixelTriangle", "Puffy", "PuffyDiamond", "SemiCircle",
    "Slanted", "SoftBoom", "SoftBurst", "Square", "Sunny", "Triangle",
    "VerySunny",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun roleAvatarShape(name: String): Shape = when (name) {
    "Arch" -> MaterialShapes.Arch.toShape()
    "Arrow" -> MaterialShapes.Arrow.toShape()
    "Boom" -> MaterialShapes.Boom.toShape()
    "Bun" -> MaterialShapes.Bun.toShape()
    "Burst" -> MaterialShapes.Burst.toShape()
    "Circle" -> CircleShape
    "ClamShell" -> MaterialShapes.ClamShell.toShape()
    "Clover4Leaf" -> MaterialShapes.Clover4Leaf.toShape()
    "Clover8Leaf" -> MaterialShapes.Clover8Leaf.toShape()
    "Cookie12Sided" -> MaterialShapes.Cookie12Sided.toShape()
    "Cookie4Sided" -> MaterialShapes.Cookie4Sided.toShape()
    "Cookie6Sided" -> MaterialShapes.Cookie6Sided.toShape()
    "Cookie7Sided" -> MaterialShapes.Cookie7Sided.toShape()
    "Cookie9Sided" -> MaterialShapes.Cookie9Sided.toShape()
    "Diamond" -> MaterialShapes.Diamond.toShape()
    "Fan" -> MaterialShapes.Fan.toShape()
    "Flower" -> MaterialShapes.Flower.toShape()
    "Gem" -> MaterialShapes.Gem.toShape()
    "Ghostish" -> MaterialShapes.Ghostish.toShape()
    "Heart" -> MaterialShapes.Heart.toShape()
    "Oval" -> MaterialShapes.Oval.toShape()
    "Pentagon" -> MaterialShapes.Pentagon.toShape()
    "Pill" -> MaterialShapes.Pill.toShape()
    "PixelCircle" -> MaterialShapes.PixelCircle.toShape()
    "PixelTriangle" -> MaterialShapes.PixelTriangle.toShape()
    "Puffy" -> MaterialShapes.Puffy.toShape()
    "PuffyDiamond" -> MaterialShapes.PuffyDiamond.toShape()
    "SemiCircle" -> MaterialShapes.SemiCircle.toShape()
    "Slanted" -> MaterialShapes.Slanted.toShape()
    "SoftBoom" -> MaterialShapes.SoftBoom.toShape()
    "SoftBurst" -> MaterialShapes.SoftBurst.toShape()
    "Square" -> MaterialShapes.Square.toShape()
    "Sunny" -> MaterialShapes.Sunny.toShape()
    "Triangle" -> MaterialShapes.Triangle.toShape()
    "VerySunny" -> MaterialShapes.VerySunny.toShape()
    else -> CircleShape
}
