package com.cauldron.myriad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

/**
 * The Living Dark's ambient layer: ~36 embers drifting up the black.
 * Particles are pure functions of time — zero per-frame allocation, and the
 * frame-time state is read only inside the draw lambda, so each frame
 * invalidates the draw phase, never composition. withFrameNanos parks with
 * the choreographer when the app leaves the foreground (MASTER_PLAN §7
 * ambient-budget exception).
 */
@Composable
fun EmberField(modifier: Modifier = Modifier, particleCount: Int = 36) {
    val particles = remember(particleCount) {
        List(particleCount) { index ->
            val h = index * 2654435761L // Knuth hash for stable pseudo-random spread
            Particle(
                x = ((h ushr 8) % 1000) / 1000f,
                startY = ((h ushr 18) % 1000) / 1000f,
                riseSpeed = 0.008f + ((h ushr 28) % 100) / 100f * 0.022f,
                sway = 0.002f + ((h ushr 36) % 100) / 100f * 0.006f,
                swayFreq = 0.3f + ((h ushr 44) % 100) / 100f * 0.5f,
                twinkle = 0.5f + ((h ushr 50) % 100) / 100f * 1.5f,
                phase = ((h ushr 56) % 628) / 100f,
                radius = 1.2f + ((h ushr 12) % 100) / 100f * 1.8f,
                baseAlpha = 0.05f + ((h ushr 22) % 100) / 100f * 0.10f,
            )
        }
    }

    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frameNanos = it }
    }

    Canvas(modifier) {
        val t = frameNanos / 1_000_000_000f
        for (p in particles) {
            val y = 1f - ((p.startY + t * p.riseSpeed) % 1f)
            val x = p.x + sin(t * p.swayFreq + p.phase) * p.sway
            val alpha = p.baseAlpha * (0.6f + 0.4f * sin(t * p.twinkle + p.phase))
            drawCircle(
                color = EmberGlow,
                radius = p.radius * density,
                center = Offset(x * size.width, y * size.height),
                alpha = alpha.coerceIn(0f, 0.2f),
            )
        }
    }
}

private val EmberGlow = Color(0xFFFF9A5C)

private class Particle(
    val x: Float,
    val startY: Float,
    val riseSpeed: Float,
    val sway: Float,
    val swayFreq: Float,
    val twinkle: Float,
    val phase: Float,
    val radius: Float,
    val baseAlpha: Float,
)
