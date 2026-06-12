package com.cauldron.myriad.engine.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Facts the engine computed. Events carry every rolled outcome, so applying
 * them (reduce) is pure and deterministic — no RNG at reduce time.
 */
@Serializable
sealed interface Event {
    @Serializable
    @SerialName("looked")
    data class LookedAround(val room: RoomId) : Event

    @Serializable
    @SerialName("moved")
    data class MovedTo(val room: RoomId) : Event

    @Serializable
    @SerialName("combat_started")
    data class CombatStarted(val monster: MonsterId) : Event

    @Serializable
    @SerialName("player_struck")
    data class PlayerStruckMonster(val monster: MonsterId, val damage: Int, val crit: Boolean) : Event

    @Serializable
    @SerialName("monster_struck")
    data class MonsterStruckPlayer(
        val monster: MonsterId,
        val damage: Int,
        val crit: Boolean,
        val defended: Boolean,
    ) : Event

    @Serializable
    @SerialName("defended")
    data object PlayerDefended : Event

    @Serializable
    @SerialName("monster_slain")
    data class MonsterSlain(val monster: MonsterId, val gold: Int) : Event

    @Serializable
    @SerialName("player_died")
    data object PlayerDied : Event

    @Serializable
    @SerialName("flee_failed")
    data class FleeFailed(val monster: MonsterId) : Event

    @Serializable
    @SerialName("flee_succeeded")
    data class FleeSucceeded(val to: RoomId) : Event

    @Serializable
    @SerialName("item_found")
    data class ItemFound(val item: ItemId) : Event

    @Serializable
    @SerialName("item_taken")
    data class ItemTaken(val item: ItemId) : Event

    @Serializable
    @SerialName("equipped")
    data class Equipped(val item: ItemId) : Event

    @Serializable
    @SerialName("game_won")
    data object GameWon : Event
}
