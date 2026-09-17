package com.example.flikky.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.LocalMotionScale
import kotlin.math.roundToInt

// Original keyframes from assets/web/flikky-logo-slow.svg: 1.5 s wobble, 5.5 s rest.
// Only the shape moves. The PNG mark and shape resource are copied from that SVG.
private data class LogoFrame(val millis: Int, val radians: Float, val scale: Float = 1f)
private val SlowLogoFrames = listOf(
    LogoFrame(0, 0f), LogoFrame(117, -0.014f), LogoFrame(233, -0.063f),
    LogoFrame(350, -0.159f), LogoFrame(467, -0.303f), LogoFrame(583, -0.461f),
    LogoFrame(700, -0.586f), LogoFrame(817, -0.661f), LogoFrame(933, -0.694f),
    LogoFrame(1000, -0.698f), LogoFrame(1021, -0.696f), LogoFrame(1050, -0.688f, 1.001f),
    LogoFrame(1167, -0.556f, 1.018f), LogoFrame(1283, -0.259f, 1.03f),
    LogoFrame(1400, -0.045f, 1.013f), LogoFrame(1500, 0f), LogoFrame(7000, 0f),
)

/** Native rendering of the web slow logo, with the current App surface and motion settings. */
@Composable
fun FlikkyLogo(modifier: Modifier = Modifier) {
    val motionScale = LocalMotionScale.current
    var rotation: State<Float>? = null
    var scale: State<Float>? = null
    if (motionScale > 0f) {
        val animation = rememberInfiniteTransition(label = "flikkySlowLogo")
        rotation = animation.animateFloat(
            initialValue = 0f, targetValue = 0f,
            animationSpec = infiniteRepeatable(keyframes {
                durationMillis = (7000 * motionScale).roundToInt()
                SlowLogoFrames.forEach { (it.radians * 180f / Math.PI.toFloat()) at (it.millis * motionScale).roundToInt() }
            }),
            label = "logoShapeRotation",
        )
        scale = animation.animateFloat(
            initialValue = 1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(keyframes {
                durationMillis = (7000 * motionScale).roundToInt()
                SlowLogoFrames.forEach { it.scale at (it.millis * motionScale).roundToInt() }
            }),
            label = "logoShapeScale",
        )
    }
    val mark = ImageBitmap.imageResource(R.drawable.flikky_logo_mark)
    Box(modifier.size(160.dp).testTag("flikky-logo")) {
        Icon(
            painter = painterResource(R.drawable.flikky_logo_shape),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationZ = rotation?.value ?: 0f
                scaleX = scale?.value ?: 1f
                scaleY = scale?.value ?: 1f
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                image = mark,
                dstOffset = IntOffset((size.width * 69 / 380).roundToInt(), (size.height * 79 / 380).roundToInt()),
                dstSize = IntSize((size.width * 242 / 380).roundToInt(), (size.height * 242 / 380).roundToInt()),
            )
        }
    }
}
