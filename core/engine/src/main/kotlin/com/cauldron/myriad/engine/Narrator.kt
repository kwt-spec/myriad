package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.Senses
import com.cauldron.myriad.engine.model.roomStateFor

/**
 * Tier-0 narration (MASTER_PLAN §5): deterministic templates, always available,
 * zero RNG. Prose variety (Tracery grammars) and AI tiers layer on top later —
 * they replace these strings, never the events behind them.
 */
object Narrator {

    fun describeRoom(state: GameState, content: ContentPack): String {
        val def = content.rooms.getValue(state.currentRoom)
        val roomState = state.roomStateFor(state.currentRoom, content)
        return buildString {
            append("— ").append(def.name).append(" —\n")
            append(def.description)
            if (roomState.itemsOnFloor.isNotEmpty()) {
                append("\n\nOn the ground: ")
                append(roomState.itemsOnFloor.joinToString { content.items.getValue(it).name })
                append('.')
            }
            if (def.exits.isNotEmpty()) {
                append("\n\n")
                append(def.exits.joinToString("\n") { "· ${it.label}" })
            }
        }
    }

    fun kindOf(event: Event): FeedKind = when (event) {
        is Event.LookedAround, is Event.MovedTo, is Event.FleeSucceeded -> FeedKind.NARRATION
        is Event.CombatStarted, is Event.PlayerStruckMonster, is Event.MonsterStruckPlayer,
        Event.PlayerBraced, is Event.FleeFailed, is Event.MonsterSlain,
        is Event.MonsterIntentDrawn, is Event.CombatTicked,
        is Event.AbilityUsed, is Event.PlayerHealed, is Event.MonsterRouted -> FeedKind.COMBAT
        is Event.ItemFound, is Event.ItemTaken, is Event.Equipped, is Event.ItemDropped -> FeedKind.LOOT
        is Event.Camped, is Event.Foraged -> FeedKind.NARRATION
        is Event.MetersTicked, is Event.XpGained, is Event.LeveledUp,
        is Event.NodeUnlocked, is Event.Respecced -> FeedKind.SYSTEM
        Event.PlayerDied, Event.GameWon -> FeedKind.SYSTEM
    }

    /** Generic perception layer — each owned sense adds one line of intel about the foe. */
    private fun senseLines(state: GameState, content: ContentPack, monsterId: com.cauldron.myriad.engine.model.MonsterId, moveId: MoveId): List<String> {
        val owned = state.player.unlockedNodes.mapNotNull {
            (content.nodes[it]?.effect as? com.cauldron.myriad.engine.model.NodeEffect.GrantSense)?.sense
        }.toSet()
        if (owned.isEmpty()) return emptyList()
        val monster = content.monsters.getValue(monsterId)
        val move = monster.moves.firstOrNull { it.id == moveId } ?: monster.moves.first()
        val hp = state.roomStateFor(state.currentRoom, content).monsterHp ?: 0
        return content.senses.values.filter { it.id in owned }.map { sense ->
            val intel = when (sense.hint) {
                com.cauldron.myriad.engine.model.SenseHint.EXACT_HP -> "${monster.name} has $hp HP"
                com.cauldron.myriad.engine.model.SenseHint.DAMAGE_FORECAST -> {
                    val sev = when {
                        move.powerNum * 10 >= move.powerDen * 16 -> "a heavy"
                        move.powerNum >= move.powerDen -> "a solid"
                        else -> "a glancing"
                    }
                    "${sev} blow is coming"
                }
                com.cauldron.myriad.engine.model.SenseHint.WEAKNESS ->
                    if (monster.defense >= 4) "it is armoured — heavy blows and Sunder bite deepest"
                    else "its guard is thin — fast strikes will serve"
                com.cauldron.myriad.engine.model.SenseHint.LOOT_SCENT ->
                    if (monster.loot != null) "it carries something worth taking" else "it carries nothing of worth"
                com.cauldron.myriad.engine.model.SenseHint.SPEED_READ ->
                    if (monster.speed > 130) "it is fast — it will act often" else "it is slow — you have time"
                com.cauldron.myriad.engine.model.SenseHint.SOUL_COUNT -> "its ember will yield ${xpHint(monster)} insight"
            }
            "  ${sense.name}: $intel."
        }
    }

    private fun xpHint(monster: com.cauldron.myriad.engine.model.MonsterDef): Long =
        (monster.maxHp / 3L) + monster.attack * 4L + monster.defense * 2L + 8L

    /** Renders one event against the post-reduce state. Blank = no feed line. */
    fun narrate(event: Event, state: GameState, content: ContentPack): String = when (event) {
        is Event.LookedAround -> describeRoom(state, content)

        is Event.MovedTo -> describeRoom(state, content)

        is Event.CombatStarted -> {
            val def = content.monsters.getValue(event.monster)
            "${def.description}\n\nThe ${def.name} attacks!"
        }

        is Event.MonsterIntentDrawn -> {
            val base = "⚠ ${telegraphOf(event.monster, event.move, content)}"
            val senses = senseLines(state, content, event.monster, event.move)
            if (senses.isEmpty()) base else "$base\n" + senses.joinToString("\n")
        }

        is Event.PlayerStruckMonster -> {
            val name = content.monsters.getValue(event.monster).name
            when {
                event.crit && event.heavy -> "Your heavy blow lands true — the $name takes ${event.damage}!"
                event.heavy -> "Your heavy blow crashes into the $name for ${event.damage}."
                event.crit -> "A perfect cut — you hit the $name for ${event.damage}!"
                else -> "You snap a quick cut at the $name — ${event.damage}."
            }
        }

        is Event.MonsterStruckPlayer -> {
            val monster = content.monsters.getValue(event.monster)
            val move = monster.moves.firstOrNull { it.id == event.move } ?: monster.moves.first()
            when {
                event.braced -> "You take the ${move.name} on your guard — only ${event.damage}."
                event.crit -> "The ${monster.name}'s ${move.name} lands savagely — ${event.damage}!"
                else -> "The ${monster.name}'s ${move.name} hits for ${event.damage}."
            }
        }

        Event.PlayerBraced -> "You plant your feet and raise your guard."

        is Event.AbilityUsed -> "You unleash ${content.abilities.getValue(event.ability).name}!"

        is Event.MonsterRouted ->
            "The ${content.monsters.getValue(event.monster).name} breaks and flees into the dark."

        is Event.Foraged -> "You scrape together what fuel you can; the cold eases its grip a little."

        is Event.PlayerHealed ->
            if (event.amount <= 0) "" else "Warmth floods back into you — you recover ${event.amount} HP."

        is Event.CombatTicked -> "" // bookkeeping only — no feed line

        is Event.XpGained -> "" // shown in the status strip, not the feed

        is Event.LeveledUp ->
            "★ You reach level ${event.level}. (+${event.hpGain} vigour, +${event.attackGain} might, " +
                "+${event.defenseGain} guard, +${event.skillPoints} skill point) — and your wounds close."

        is Event.NodeUnlocked ->
            "A new star kindles in your constellation: ${content.nodes.getValue(event.node).name}."

        is Event.Respecced ->
            "You unmake your constellation, reclaiming ${event.refundedPoints} points " +
                "(${event.goldCost} gold spent on the reflection)."

        is Event.MonsterSlain -> {
            val name = content.monsters.getValue(event.monster).name
            "The $name collapses into drifting ash. You claim ${event.gold} gold."
        }

        Event.PlayerDied ->
            "Your wounds take you. The dark below was not empty after all.\n\n— DEATH — " +
                "turn ${state.turn}, ${state.player.gold} gold carried into the ash."

        is Event.FleeFailed -> {
            val name = content.monsters.getValue(event.monster).name
            "You turn to run — the $name cuts you off!"
        }

        is Event.FleeSucceeded ->
            "You tear yourself away and scramble back.\n\n" + describeRoom(state, content)

        is Event.ItemDropped -> {
            val monster = content.monsters.getValue(event.monster).name
            "Among the $monster's ashes: ${content.items.getValue(event.item).name}."
        }

        is Event.ItemFound -> {
            val room = content.rooms.getValue(state.currentRoom)
            room.searchText ?: "Hidden in the rubble: ${content.items.getValue(event.item).name}."
        }

        is Event.ItemTaken -> "Taken: ${content.items.getValue(event.item).name}."

        is Event.Equipped -> {
            val def = content.items.getValue(event.item)
            val bonuses = buildList {
                if (def.attackBonus != 0) add("+${def.attackBonus} attack")
                if (def.defenseBonus != 0) add("+${def.defenseBonus} defense")
            }.joinToString(", ")
            "You ready the ${def.name}${if (bonuses.isEmpty()) "" else " ($bonuses)"}."
        }

        is Event.MetersTicked -> buildList {
            for ((meterId, value) in event.values) {
                val def = content.meters[meterId] ?: continue
                when {
                    value == def.cap / 2 -> add("${def.glyph} Your ${def.name} is fading — ${value}/${def.cap}.")
                    value == 2 -> add("${def.glyph} Your ${def.name} is nearly spent.")
                }
            }
            if (event.chillDamage > 0) {
                add("The chill of the deep places bites — ${event.chillDamage}.")
            }
        }.joinToString("\n")

        is Event.Camped -> {
            val room = content.rooms.getValue(state.currentRoom)
            room.campText ?: "You rest a while in safety; your strength of purpose returns."
        }

        Event.GameWon ->
            "You climb the stair into ember-light and open air.\n\n— THE CELLAR IS CLEARED — " +
                "turn ${state.turn}, ${state.player.gold} gold. The world above awaits; this was only the first chamber of Myriad."
    }

    fun telegraphOf(monsterId: MonsterId, moveId: MoveId, content: ContentPack): String {
        val monster = content.monsters.getValue(monsterId)
        val move = monster.moves.firstOrNull { it.id == moveId } ?: monster.moves.first()
        return move.telegraph
    }
}
