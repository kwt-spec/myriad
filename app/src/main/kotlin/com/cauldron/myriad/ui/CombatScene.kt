package com.cauldron.myriad.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode

private val HurtFlash = Color(0xFF5A1410)
private val StrikeFlash = Color(0xFF3A2410)
private val PanelBase = Color(0xFF0E0B08)

/**
 * The living fight (MASTER_PLAN M1a): enemy presence, telegraphed intent,
 * ATB timeline, damage popups, crit shake, haptics. Pure presentation —
 * every number here was already decided by the engine.
 */
@Composable
fun CombatScene(
    game: GameState,
    content: ContentPack,
    lastEvents: List<Event>,
    modifier: Modifier = Modifier,
) {
    val mode = game.mode as? Mode.Combat ?: return
    val monster = content.monsters.getValue(mode.monster)
    val monsterHp = game.rooms[game.currentRoom]?.monsterHp ?: 0
    val intentMove = monster.moves.firstOrNull { it.id == mode.monsterIntent } ?: monster.moves.first()

    val haptic = LocalHapticFeedback.current
    val shakeX = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val popups = remember { mutableStateListOf<DamagePopup>() }
    val popupCounter = remember { intArrayOf(0) }

    LaunchedEffect(game.turn) {
        var crit = false
        var hurt = false
        var struck = false
        for (event in lastEvents) {
            when (event) {
                is Event.PlayerStruckMonster -> {
                    struck = true
                    crit = crit || event.crit
                    popups += DamagePopup(popupCounter[0]++, "${event.damage}", event.crit, hostile = false)
                }
                is Event.MonsterStruckPlayer -> {
                    hurt = true
                    crit = crit || event.crit
                    popups += DamagePopup(popupCounter[0]++, "−${event.damage}", event.crit, hostile = true)
                }
                else -> {}
            }
        }
        if (struck || hurt) {
            haptic.performHapticFeedback(
                if (crit) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
            flash.snapTo(if (hurt) 1f else 0.6f)
            flash.animateTo(0f, tween(420))
        }
        if (crit) {
            repeat(3) {
                shakeX.animateTo(9f, tween(36))
                shakeX.animateTo(-9f, tween(36))
            }
            shakeX.animateTo(0f, tween(50))
        }
    }

    val hpFraction by animateFloatAsState(
        targetValue = (monsterHp.toFloat() / monster.maxHp).coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "monsterHp",
    )
    val hurtTint = lastEvents.any { it is Event.MonsterStruckPlayer }
    val flashColor by animateColorAsState(
        targetValue = if (hurtTint) HurtFlash else StrikeFlash,
        label = "flashColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .graphicsLayer { translationX = shakeX.value },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBase)
                .background(flashColor.copy(alpha = flash.value * 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monster.name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.width(12.dp))
                HealthBar(hpFraction, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            IntentLine(intentMove.telegraph)
            Spacer(Modifier.height(10.dp))
            TimelineLane("you", mode.playerGauge / Mode.Combat.GAUGE_MAX.toFloat(), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(5.dp))
            TimelineLane("foe", mode.monsterGauge / Mode.Combat.GAUGE_MAX.toFloat(), MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            StaminaRow(mode.playerStamina, mode.braced)
        }

        Box(Modifier.matchParentSize()) {
            for (popup in popups) {
                key(popup.id) {
                    DamagePopupText(popup) { popups.remove(popup) }
                }
            }
        }
    }
}

private data class DamagePopup(val id: Int, val text: String, val crit: Boolean, val hostile: Boolean)

@Composable
private fun DamagePopupText(popup: DamagePopup, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(popup.id) {
        progress.animateTo(1f, tween(durationMillis = if (popup.crit) 900 else 700))
        onDone()
    }
    val xBias = ((popup.id * 73) % 100) / 100f * 0.7f - 0.35f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (popup.crit) "${popup.text}!" else popup.text,
            color = if (popup.hostile) Color(0xFFFF6B5A) else Color(0xFFFFC46B),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = if (popup.crit) 26.sp else 19.sp,
            modifier = Modifier.graphicsLayer {
                translationY = -progress.value * 64.dp.toPx()
                translationX = xBias * size.width
                alpha = 1f - progress.value * progress.value
            },
        )
    }
}

@Composable
private fun HealthBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFF241A12)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    scaleX = fraction
                }
                .background(
                    if (fraction > 0.35f) Color(0xFFCC5A2E) else Color(0xFFE03B2E)
                ),
        )
    }
}

@Composable
private fun IntentLine(telegraph: String) {
    val pulse by rememberInfiniteTransition(label = "intent").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "intentPulse",
    )
    Text(
        text = "⚠ $telegraph",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = pulse),
    )
}

@Composable
private fun TimelineLane(label: String, fraction: Float, color: Color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(550), label = "lane-$label")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1C1610)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        scaleX = animated
                    }
                    .background(color.copy(alpha = if (animated >= 1f) 1f else 0.55f)),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (animated >= 1f) "ACT" else "…",
            style = MaterialTheme.typography.labelMedium,
            color = if (animated >= 1f) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaminaRow(stamina: Int, braced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "stamina $stamina",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (braced) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "🛡 braced",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
