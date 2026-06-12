package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState

/**
 * Tier-0 narration (MASTER_PLAN §5): deterministic templates, always available,
 * zero RNG. Prose variety (Tracery grammars) and AI tiers layer on top later —
 * they replace these strings, never the events behind them.
 */
object Narrator {

    fun describeRoom(state: GameState, content: ContentPack): String {
        val def = content.rooms.getValue(state.currentRoom)
        val roomState = state.rooms.getValue(state.currentRoom)
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
        Event.PlayerDefended, is Event.FleeFailed, is Event.MonsterSlain -> FeedKind.COMBAT
        is Event.ItemFound, is Event.ItemTaken, is Event.Equipped -> FeedKind.LOOT
        Event.PlayerDied, Event.GameWon -> FeedKind.SYSTEM
    }

    /** Renders one event against the post-reduce state. */
    fun narrate(event: Event, state: GameState, content: ContentPack): String = when (event) {
        is Event.LookedAround -> describeRoom(state, content)

        is Event.MovedTo -> describeRoom(state, content)

        is Event.CombatStarted -> {
            val def = content.monsters.getValue(event.monster)
            "${def.description}\n\nThe ${def.name} attacks!"
        }

        is Event.PlayerStruckMonster -> {
            val name = content.monsters.getValue(event.monster).name
            if (event.crit) "A perfect strike — you hit the $name for ${event.damage}!"
            else "You strike the $name for ${event.damage}."
        }

        is Event.MonsterStruckPlayer -> {
            val name = content.monsters.getValue(event.monster).name
            when {
                event.defended -> "You brace behind your guard; the $name lands only ${event.damage}."
                event.crit -> "The $name savages you for ${event.damage}!"
                else -> "The $name bites back for ${event.damage}."
            }
        }

        Event.PlayerDefended -> "You raise your guard."

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

        Event.GameWon ->
            "You climb the stair into ember-light and open air.\n\n— THE CELLAR IS CLEARED — " +
                "turn ${state.turn}, ${state.player.gold} gold. The world above awaits; this was only the first chamber of Myriad."
    }
}
