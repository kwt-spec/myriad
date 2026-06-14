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
    data class PlayerStruckMonster(
        val monster: MonsterId,
        val damage: Int,
        val crit: Boolean,
        val heavy: Boolean,
    ) : Event

    @Serializable
    @SerialName("monster_struck")
    data class MonsterStruckPlayer(
        val monster: MonsterId,
        val move: MoveId,
        val damage: Int,
        val crit: Boolean,
        val braced: Boolean,
    ) : Event

    @Serializable
    @SerialName("intent_drawn")
    data class MonsterIntentDrawn(val monster: MonsterId, val move: MoveId) : Event

    @Serializable
    @SerialName("braced")
    data object PlayerBraced : Event

    /**
     * Syncs the ATB bookkeeping (gauges/stamina/brace/intent) into Mode.Combat
     * at the end of a combat resolution, so reduce alone reconstructs the exact
     * state. Carries no narration.
     */
    @Serializable
    @SerialName("combat_ticked")
    data class CombatTicked(
        val playerGauge: Int,
        val monsterGauge: Int,
        val playerStamina: Int,
        val braced: Boolean,
        val monsterIntent: MoveId,
        val abilityCooldowns: Map<AbilityId, Int> = emptyMap(),
        val statuses: List<ActiveStatus> = emptyList(),
    ) : Event

    @Serializable
    @SerialName("ability_used")
    data class AbilityUsed(val ability: AbilityId) : Event

    @Serializable
    @SerialName("player_healed")
    data class PlayerHealed(val amount: Int) : Event

    @Serializable
    @SerialName("monster_bled")
    data class MonsterBled(val monster: MonsterId, val damage: Int) : Event

    @Serializable
    @SerialName("player_self_harm")
    data class PlayerSelfHarm(val amount: Int) : Event

    @Serializable
    @SerialName("monster_routed")
    data class MonsterRouted(val monster: MonsterId) : Event

    @Serializable
    @SerialName("foraged")
    data class Foraged(val verb: VerbId, val values: Map<MeterId, Int>) : Event

    @Serializable
    @SerialName("monster_slain")
    data class MonsterSlain(val monster: MonsterId, val gold: Int) : Event

    @Serializable
    @SerialName("xp_gained")
    data class XpGained(val amount: Long) : Event

    @Serializable
    @SerialName("leveled_up")
    data class LeveledUp(
        val level: Int,
        val hpGain: Int,
        val attackGain: Int,
        val defenseGain: Int,
        val skillPoints: Int,
    ) : Event

    @Serializable
    @SerialName("node_unlocked")
    data class NodeUnlocked(val node: NodeId) : Event

    @Serializable
    @SerialName("respecced")
    data class Respecced(val refundedPoints: Int, val goldCost: Int) : Event

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
    @SerialName("item_dropped")
    data class ItemDropped(val monster: MonsterId, val item: ItemId) : Event

    @Serializable
    @SerialName("item_found")
    data class ItemFound(val item: ItemId) : Event

    @Serializable
    @SerialName("item_taken")
    data class ItemTaken(val item: ItemId) : Event

    @Serializable
    @SerialName("equipped")
    data class Equipped(val item: ItemId) : Event

    /**
     * Survival clock advanced (Ember-age game-time: per action). Carries the
     * post-burn meter values and any attrition damage so reduce stays pure.
     */
    @Serializable
    @SerialName("meters_ticked")
    data class MetersTicked(val values: Map<MeterId, Int>, val chillDamage: Int) : Event

    @Serializable
    @SerialName("camped")
    data class Camped(val restored: Map<MeterId, Int>) : Event

    // ── Storylets (M2) ──────────────────────────────────────────────────────
    @Serializable
    @SerialName("entered_story")
    data class EnteredStorylet(val storylet: StoryletId) : Event

    @Serializable
    @SerialName("left_story")
    data object LeftStorylet : Event

    @Serializable
    @SerialName("flag_set")
    data class FlagSet(val flag: String, val value: Int) : Event

    @Serializable
    @SerialName("story_narration")
    data class StoryNarration(val text: String) : Event

    /** A weapon received straight to hand from a story (a fortunate find). */
    @Serializable
    @SerialName("item_received")
    data class ItemReceived(val item: ItemId) : Event

    @Serializable
    @SerialName("item_consumed")
    data class ItemConsumed(val item: ItemId) : Event

    @Serializable
    @SerialName("gold_gained")
    data class GoldGained(val amount: Int) : Event

    @Serializable
    @SerialName("check_resolved")
    data class CheckResolved(val attribute: com.cauldron.myriad.engine.model.Attribute, val chancePercent: Int, val success: Boolean) : Event

    @Serializable
    @SerialName("game_won")
    data object GameWon : Event
}
