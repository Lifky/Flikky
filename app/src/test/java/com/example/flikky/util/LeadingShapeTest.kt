package com.example.flikky.util

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingShapeTest {
    @Test
    fun parseRecognizesAllSelectedIdsCaseInsensitively() {
        assertEquals(25, LeadingShape.entries.size)
        LeadingShape.entries.forEach { shape ->
            assertEquals(shape, LeadingShape.parse(shape.id.uppercase()))
        }
    }

    @Test
    fun unknownIdFallsBackToCookie9Sided() {
        assertEquals(LeadingShape.Cookie9Sided, LeadingShape.parse("not-a-shape"))
        assertEquals(LeadingShape.Cookie9Sided, LeadingShape.parse(null))
    }

    @Test
    fun everySelectedShapeMeetsMeasuredEligibility() {
        LeadingShape.entries.forEach { shape ->
            val polygon = LeadingShapeGeometry.polygonOf(shape)
            val metrics = LeadingShapeGeometry.measure(polygon)
            assertTrue("${shape.id} failed eligibility", LeadingShapeGeometry.isEligible(polygon))
            assertTrue("${shape.id} is stretched: ${metrics.stretch}", metrics.stretch <= LeadingShapeGeometry.MAX_STRETCH)
            assertTrue(
                "${shape.id} cannot hold 24dp: ${metrics.inscribedDiameterDp(LeadingShapeGeometry.CONTAINER_DP)}",
                metrics.inscribedDiameterDp(LeadingShapeGeometry.CONTAINER_DP) >= LeadingShapeGeometry.MIN_INSCRIBED_DP,
            )
        }
    }

    @Test
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun measuredDropListIsNotRegisteredOrEligible() {
        val dropped = mapOf(
            "semiCircle" to MaterialShapes.SemiCircle,
            "clamShell" to MaterialShapes.ClamShell,
            "pixelTriangle" to MaterialShapes.PixelTriangle,
            "puffy" to MaterialShapes.Puffy,
            "diamond" to MaterialShapes.Diamond,
            "heart" to MaterialShapes.Heart,
            "triangle" to MaterialShapes.Triangle,
            "arrow" to MaterialShapes.Arrow,
            "softBoom" to MaterialShapes.SoftBoom,
            "boom" to MaterialShapes.Boom,
        )
        assertTrue(dropped.keys.intersect(LeadingShape.entries.map { it.id }.toSet()).isEmpty())
        dropped.forEach { (id, polygon) ->
            assertFalse("$id unexpectedly meets the eligibility guard", LeadingShapeGeometry.isEligible(polygon))
        }
    }

    @Test
    fun defaultShapePreservesExistingVisual() {
        assertEquals(LeadingShape.Cookie9Sided, LeadingShape.Default)
    }
}
