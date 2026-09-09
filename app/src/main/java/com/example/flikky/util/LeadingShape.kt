package com.example.flikky.util

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.hypot
import kotlin.math.min

/**
 * The 25 shapes that can safely contain the standard 24dp leading icon.
 * The remaining official shapes are omitted because measured stretch or
 * inscribed diameter would make the icon visibly distorted or clipped.
 */
enum class LeadingShape(val id: String) {
    Arch("arch"), Circle("circle"), Square("square"), PixelCircle("pixelCircle"),
    Slanted("slanted"), Clover8Leaf("clover8Leaf"), Cookie6Sided("cookie6Sided"),
    Cookie12Sided("cookie12Sided"), Cookie9Sided("cookie9Sided"), Gem("gem"),
    Cookie7Sided("cookie7Sided"), Cookie4Sided("cookie4Sided"), Pentagon("pentagon"),
    Pill("pill"), Clover4Leaf("clover4Leaf"), Sunny("sunny"), Ghostish("ghostish"),
    SoftBurst("softBurst"), VerySunny("verySunny"), Oval("oval"), Fan("fan"),
    Burst("burst"), PuffyDiamond("puffyDiamond"), Flower("flower"), Bun("bun");

    companion object {
        val Default: LeadingShape = Cookie9Sided

        fun parse(raw: String?): LeadingShape =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: Default
    }
}

data class ShapeMetrics(
    val stretch: Double,
    val innerRadius: Double,
) {
    fun inscribedDiameterDp(containerDp: Int): Double = innerRadius * 2 * containerDp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object LeadingShapeGeometry {
    const val CONTAINER_DP = 40
    const val ICON_DP = 24
    const val MAX_STRETCH = 1.10
    const val MIN_INSCRIBED_DP = 24.0

    fun isEligible(polygon: RoundedPolygon): Boolean {
        val metrics = measure(polygon)
        return metrics.stretch <= MAX_STRETCH &&
            metrics.inscribedDiameterDp(CONTAINER_DP) >= MIN_INSCRIBED_DP
    }

    fun polygonOf(shape: LeadingShape): RoundedPolygon = when (shape) {
        LeadingShape.Arch -> MaterialShapes.Arch
        LeadingShape.Circle -> MaterialShapes.Circle
        LeadingShape.Square -> MaterialShapes.Square
        LeadingShape.PixelCircle -> MaterialShapes.PixelCircle
        LeadingShape.Slanted -> MaterialShapes.Slanted
        LeadingShape.Clover8Leaf -> MaterialShapes.Clover8Leaf
        LeadingShape.Cookie6Sided -> MaterialShapes.Cookie6Sided
        LeadingShape.Cookie12Sided -> MaterialShapes.Cookie12Sided
        LeadingShape.Cookie9Sided -> MaterialShapes.Cookie9Sided
        LeadingShape.Gem -> MaterialShapes.Gem
        LeadingShape.Cookie7Sided -> MaterialShapes.Cookie7Sided
        LeadingShape.Cookie4Sided -> MaterialShapes.Cookie4Sided
        LeadingShape.Pentagon -> MaterialShapes.Pentagon
        LeadingShape.Pill -> MaterialShapes.Pill
        LeadingShape.Clover4Leaf -> MaterialShapes.Clover4Leaf
        LeadingShape.Sunny -> MaterialShapes.Sunny
        LeadingShape.Ghostish -> MaterialShapes.Ghostish
        LeadingShape.SoftBurst -> MaterialShapes.SoftBurst
        LeadingShape.VerySunny -> MaterialShapes.VerySunny
        LeadingShape.Oval -> MaterialShapes.Oval
        LeadingShape.Fan -> MaterialShapes.Fan
        LeadingShape.Burst -> MaterialShapes.Burst
        LeadingShape.PuffyDiamond -> MaterialShapes.PuffyDiamond
        LeadingShape.Flower -> MaterialShapes.Flower
        LeadingShape.Bun -> MaterialShapes.Bun
    }

    /** Samples the official cubic geometry; keep this as the single measurement source. */
    fun measure(p: RoundedPolygon): ShapeMetrics {
        val cubics = p.cubics
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        cubics.forEach { cubic ->
            for (t in 0..40) {
                val u = t / 40.0
                val x = bezier(cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X, u)
                val y = bezier(cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y, u)
                minX = min(minX, x)
                maxX = maxOf(maxX, x)
                minY = min(minY, y)
                maxY = maxOf(maxY, y)
            }
        }
        val width = maxX - minX
        val height = maxY - minY
        var minRadius = Double.MAX_VALUE
        cubics.forEach { cubic ->
            for (t in 0..120) {
                val u = t / 120.0
                val x = bezier(cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X, u)
                val y = bezier(cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y, u)
                minRadius = min(
                    minRadius,
                    hypot((x - minX) / width - 0.5, (y - minY) / height - 0.5),
                )
            }
        }
        return ShapeMetrics(
            stretch = maxOf(width, height) / minOf(width, height),
            innerRadius = minRadius,
        )
    }

    private fun bezier(p0: Float, p1: Float, p2: Float, p3: Float, t: Double): Double {
        val m = 1 - t
        return m * m * m * p0 + 3 * m * m * t * p1 + 3 * m * t * t * p2 + t * t * t * p3
    }
}
