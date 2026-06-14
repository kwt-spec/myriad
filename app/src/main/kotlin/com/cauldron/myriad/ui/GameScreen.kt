package com.cauldron.myriad.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cauldron.myriad.GameViewModel
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedEntry
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.meterFor
import kotlinx.coroutines.delay

private val CombatText = Color(0xFFE0916B)
private val LootText = Color(0xFFD9C36B)

@Composable
fun GameScreen(
    playing: GameViewModel.UiState.Playing,
    content: ContentPack,
    onAct: (Action) -> Unit,
    onNewRun: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenInventory: () -> Unit,
) {
    val game = playing.game
    val listState = rememberLazyListState()
    val inCombat = game.mode is Mode.Combat

    LaunchedEffect(game.feed.lastOrNull()?.id) {
        if (game.feed.isNotEmpty()) listState.animateScrollToItem(game.feed.size - 1)
    }

    // Pause-on-ready: while the timeline sweep plays out, chips sleep.
    var inputLocked by remember { mutableStateOf(false) }
    LaunchedEffect(game.turn) {
        val combatMotion = playing.lastEvents.any {
            it is Event.CombatTicked || it is Event.MonsterStruckPlayer ||
                it is Event.PlayerStruckMonster || it is Event.CombatStarted
        }
        if (combatMotion) {
            inputLocked = true
            delay(620)
            inputLocked = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        EmberField(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            StatusStrip(game, content, onOpenSkills, onOpenInventory)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (inCombat) {
                CombatScene(game, content, playing.lastEvents)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(game.feed, key = { it.id }) { entry ->
                    FeedBlock(entry)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionBar(playing, game, enabled = !inputLocked, onAct, onNewRun)
        }
    }
}

@Composable
private fun StoryChoices(playing: GameViewModel.UiState.Playing, enabled: Boolean, onAct: (Action) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (chip in playing.chips) {
            FilledTonalButton(
                onClick = { onAct(chip.action) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Text(chip.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusStrip(game: GameState, content: ContentPack, onOpenSkills: () -> Unit, onOpenInventory: () -> Unit) {
    val animatedHp by animateIntAsState(game.player.hp, tween(450), label = "hp")
    val animatedGold by animateIntAsState(game.player.gold, tween(450), label = "gold")
    val lowHp = game.player.hp <= game.player.maxHp / 4

    val heartbeat by rememberInfiniteTransition(label = "heartbeat").animateFloat(
        initialValue = 1f,
        targetValue = if (lowHp) 1.25f else 1f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "heartbeatScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val style = MaterialTheme.typography.labelMedium
        Text(
            text = "♥",
            style = style,
            color = if (lowHp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                scaleX = heartbeat
                scaleY = heartbeat
            },
        )
        Text(
            text = "$animatedHp/${game.player.maxHp}",
            style = style,
            color = if (lowHp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "◈ $animatedGold",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for ((meterId, def) in content.meters) {
            val value = game.meterFor(meterId, content)
            val lowMeter = value <= def.cap / 4
            Text(
                text = "${def.glyph}$value",
                style = style,
                color = if (lowMeter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = content.rooms.getValue(game.currentRoom).name,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Pack — the inventory lives one tap away; the glyph carries the count.
        Text(
            text = "▣${game.player.inventory.size}",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenInventory)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        // Level + skill button — the constellations live one tap away.
        val hasPoints = game.player.skillPoints > 0
        Text(
            text = "★${game.player.level}" + if (hasPoints) " ✦${game.player.skillPoints}" else "",
            style = style,
            color = if (hasPoints) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpenSkills)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FeedBlock(entry: FeedEntry) {
    // Kinetic feed: each new entry breathes in once.
    val appeared = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appeared.animateTo(1f, tween(250)) }

    val modifier = Modifier.graphicsLayer {
        alpha = appeared.value
        translationY = (1f - appeared.value) * 24f
    }
    when (entry.kind) {
        FeedKind.NARRATION -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier,
        )
        FeedKind.COMBAT -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = CombatText,
            modifier = modifier,
        )
        FeedKind.LOOT -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = LootText,
            modifier = modifier,
        )
        FeedKind.SYSTEM -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionBar(
    playing: GameViewModel.UiState.Playing,
    game: GameState,
    enabled: Boolean,
    onAct: (Action) -> Unit,
    onNewRun: () -> Unit,
) {
    if (playing.isStory) {
        StoryChoices(playing, enabled, onAct)
    } else if (playing.chips.isEmpty()) {
        Column(Modifier.padding(20.dp)) {
            Button(
                onClick = onNewRun,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    text = if (game.mode is Mode.Dead) "Rise again" else "Descend again",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    } else {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (chip in playing.chips) {
                FilledTonalButton(
                    onClick = { onAct(chip.action) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(chip.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
