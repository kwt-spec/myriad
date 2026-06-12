package com.cauldron.myriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cauldron.myriad.GameViewModel
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.FeedEntry
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode

private val CombatText = Color(0xFFE0916B)
private val LootText = Color(0xFFD9C36B)

@Composable
fun GameScreen(
    playing: GameViewModel.UiState.Playing,
    content: ContentPack,
    onAct: (Action) -> Unit,
    onNewRun: () -> Unit,
) {
    val game = playing.game
    val listState = rememberLazyListState()

    LaunchedEffect(game.feed.lastOrNull()?.id) {
        if (game.feed.isNotEmpty()) listState.animateScrollToItem(game.feed.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        StatusStrip(game, content)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
        ActionBar(playing, game, onAct, onNewRun)
    }
}

@Composable
private fun StatusStrip(game: GameState, content: ContentPack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val style = MaterialTheme.typography.labelMedium
        val lowHp = game.player.hp <= game.player.maxHp / 4
        Text(
            text = "♥ ${game.player.hp}/${game.player.maxHp}",
            style = style,
            color = if (lowHp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "◈ ${game.player.gold}",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = content.rooms.getValue(game.currentRoom).name,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "t${game.turn}",
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedBlock(entry: FeedEntry) {
    when (entry.kind) {
        FeedKind.NARRATION -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FeedKind.COMBAT -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = CombatText,
        )
        FeedKind.LOOT -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = LootText,
        )
        FeedKind.SYSTEM -> Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionBar(
    playing: GameViewModel.UiState.Playing,
    game: GameState,
    onAct: (Action) -> Unit,
    onNewRun: () -> Unit,
) {
    if (playing.chips.isEmpty()) {
        // Terminal state: death or victory.
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
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(chip.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
