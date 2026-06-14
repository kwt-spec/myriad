package com.cauldron.myriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

private val Better = Color(0xFF6FCF7A)
private val Worse = Color(0xFFE0716B)
private val EquippedAccent = Color(0xFFFF9A5C)

@Composable
fun InventoryScreen(
    playing: GameViewModel.UiState.Playing,
    onEquip: (com.cauldron.myriad.engine.model.ItemId) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pack", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("◈ ${playing.game.player.gold}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        if (playing.inventory.isEmpty()) {
            Text(
                "Your hands are empty. The dark below keeps its iron close — search the ash, win it from the things that live here, or pry it from the doors no one opened.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playing.inventory, key = { it.item.value }) { row -> InvCard(row, onEquip) }
        }

        Spacer(Modifier.height(10.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Back", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InvCard(row: GameViewModel.InvRow, onEquip: (com.cauldron.myriad.engine.model.ItemId) -> Unit) {
    val accent = if (row.equipped) EquippedAccent else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E0B08))
            .border(1.dp, accent.copy(alpha = if (row.equipped) 0.8f else 0.5f), RoundedCornerShape(12.dp))
            .then(if (row.equippable) Modifier.clickable { onEquip(row.item) } else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            when {
                row.equipped -> Text("equipped", style = MaterialTheme.typography.labelMedium, color = EquippedAccent)
                row.equippable -> ComparisonDelta(row.deltaAttack, row.deltaDefense)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(row.detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun ComparisonDelta(dAtk: Int, dDef: Int) {
    val parts = buildList {
        if (dAtk != 0) add((if (dAtk > 0) "+$dAtk" else "$dAtk") + " atk")
        if (dDef != 0) add((if (dDef > 0) "+$dDef" else "$dDef") + " def")
    }
    if (parts.isEmpty()) {
        Text("equip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val better = dAtk + dDef >= 0
    Text(
        parts.joinToString(" "),
        style = MaterialTheme.typography.labelMedium,
        color = if (better) Better else Worse,
        fontWeight = FontWeight.Bold,
    )
}
