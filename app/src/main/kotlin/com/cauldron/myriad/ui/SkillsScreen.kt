package com.cauldron.myriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cauldron.myriad.GameViewModel

private val OwnedColor = Color(0xFF6FCF7A)
private val AffordableColor = Color(0xFFFF9A5C)
private val LockedColor = Color(0xFF5A5048)

@Composable
fun SkillsScreen(
    playing: GameViewModel.UiState.Playing,
    onUnlock: (com.cauldron.myriad.engine.model.NodeId) -> Unit,
    onRespec: () -> Unit,
    onClose: () -> Unit,
) {
    val player = playing.game.player
    val xpHere = 18L * (player.level - 1) * player.level
    val xpNext = 18L * player.level * (player.level + 1)
    val span = (xpNext - xpHere).coerceAtLeast(1)
    val progress = ((player.xp - xpHere).toFloat() / span).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Constellations",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "✦ ${player.skillPoints} pts",
                style = MaterialTheme.typography.labelLarge,
                color = if (player.skillPoints > 0) AffordableColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "★ Level ${player.level}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF1C1610)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(playing.nodes, key = { it.id.value }) { node ->
                NodeCard(node, onUnlock)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (playing.canRespec) {
            OutlinedButton(
                onClick = onRespec,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Text("Reflect — respec for ${playing.respecCost} gold", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text("Back to the dark", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NodeCard(node: GameViewModel.NodeRow, onUnlock: (com.cauldron.myriad.engine.model.NodeId) -> Unit) {
    val accent = when (node.status) {
        GameViewModel.NodeStatus.OWNED -> OwnedColor
        GameViewModel.NodeStatus.AFFORDABLE -> AffordableColor
        GameViewModel.NodeStatus.LOCKED -> LockedColor
    }
    val clickable = node.status == GameViewModel.NodeStatus.AFFORDABLE
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E0B08))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .then(if (clickable) Modifier.clickable { onUnlock(node.id) } else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (node.status) {
                    GameViewModel.NodeStatus.OWNED -> "●"
                    GameViewModel.NodeStatus.AFFORDABLE -> "○"
                    GameViewModel.NodeStatus.LOCKED -> "·"
                },
                color = accent,
                modifier = Modifier.width(20.dp),
            )
            Text(
                node.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                when (node.status) {
                    GameViewModel.NodeStatus.OWNED -> "owned"
                    GameViewModel.NodeStatus.AFFORDABLE -> "✦ ${node.cost}"
                    GameViewModel.NodeStatus.LOCKED -> "✦ ${node.cost} · locked"
                },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            node.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}
