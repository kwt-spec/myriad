package com.cauldron.myriad.engine.model

import com.cauldron.myriad.engine.rng.RngState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val name: String,
    val hp: Int,
    /** Stored stat: base + level gains. Constellation bonuses are DERIVED on top, never stored. */
    val maxHp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val gold: Int,
    val inventory: List<ItemId>,
    val equipped: ItemId?,
    // Progression (v4; defaults keep pre-v4 saves decoding):
    val xp: Long = 0,
    val level: Int = 1,
    val skillPoints: Int = 0,
    val unlockedNodes: List<NodeId> = emptyList(),
    /** Use-texture: weapon-family slug → swings landed. Derives a small capped bonus. */
    val mastery: Map<String, Int> = emptyMap(),
)

@Serializable
data class RoomState(
    val searched: Boolean = false,
    /** Current monster HP; null = dead or never present. */
    val monsterHp: Int? = null,
    val itemsOnFloor: List<ItemId> = emptyList(),
)

/**
 * Lingering combat effects (the status-effect system behind the 80 ability kinds).
 * Foe debuffs: BLEED (damage over time), STUN (skip a turn), WEAKEN (−attack%),
 * SUNDER (−defense), MARK (+player crit). Self buffs: GUARD (−damage taken), RAGE
 * (+attack), REGEN (heal over time), FOCUS (+crit), HASTE (faster recovery).
 */
@Serializable
enum class StatusKind { BLEED, STUN, WEAKEN, SUNDER, MARK, GUARD, RAGE, REGEN, FOCUS, HASTE }

@Serializable
data class ActiveStatus(
    val kind: StatusKind,
    val magnitude: Int,
    val turnsLeft: Int,
    val onFoe: Boolean,
)

@Serializable
sealed interface Mode {
    @Serializable
    @SerialName("exploring")
    data object Exploring : Mode

    /**
     * Tick-ATB combat (MASTER_PLAN M1a). Gauges fill by speed per tick; the
     * player acts at GAUGE_MAX, the monster executes its telegraphed intent
     * when its gauge wraps. Defaults are the v1→v2 migration values: a v1 save
     * decodes straight into "player ready to act, monster winding up" —
     * `monsterIntent` defaults to a sentinel the engine resolves to the
     * monster's first move (also makes renamed move ids tombstone-safe).
     */
    @Serializable
    @SerialName("combat")
    data class Combat(
        val monster: MonsterId,
        val playerGauge: Int = GAUGE_MAX,
        val monsterGauge: Int = 0,
        val playerStamina: Int = STAMINA_MAX,
        val monsterIntent: MoveId = MoveId(""),
        val braced: Boolean = false,
        /** Ability id → player-turns remaining before reuse. Resets each fight. */
        val abilityCooldowns: Map<AbilityId, Int> = emptyMap(),
        /** Active status effects (v5). Resets each fight; default keeps pre-v5 saves decoding. */
        val statuses: List<ActiveStatus> = emptyList(),
    ) : Mode {
        companion object {
            const val GAUGE_MAX = 1000
            const val STAMINA_MAX = 100
        }
    }

    /** A tap-a-choice narrative scene (MASTER_PLAN §16.9). */
    @Serializable
    @SerialName("story")
    data class Story(val storylet: StoryletId) : Mode

    @Serializable
    @SerialName("dead")
    data object Dead : Mode

    @Serializable
    @SerialName("victory")
    data object Victory : Mode
}

@Serializable
enum class FeedKind { NARRATION, COMBAT, LOOT, SYSTEM }

@Serializable
data class FeedEntry(
    /** Monotonic per-game id — stable key for UI lists. */
    val id: Long,
    val turn: Long,
    val kind: FeedKind,
    val text: String,
)

@Serializable
data class GameState(
    val seed: Long,
    val turn: Long,
    val contentVersion: String,
    val rng: RngState,
    val player: PlayerState,
    val currentRoom: RoomId,
    val lastRoom: RoomId? = null,
    val rooms: Map<RoomId, RoomState>,
    val mode: Mode,
    val feed: List<FeedEntry> = emptyList(),
    val nextFeedId: Long = 0,
    /** Survival meter values; default keeps pre-v3 saves decoding (meterFor seeds reads). */
    val meters: Map<MeterId, Int> = emptyMap(),
    /** Story qualities/flags (Fallen-London style); default keeps pre-v6 saves decoding. */
    val flags: Map<String, Int> = emptyMap(),
) {
    companion object {
        const val FEED_LIMIT = 500
    }
}

/**
 * Tolerant room-state lookup: rooms added by newer content packs don't exist in
 * older saves' state maps — seed them fresh from the pack instead of crashing.
 * (Found by the v1-device golden save: it predates the Collapsed Vault.)
 */
fun GameState.roomStateFor(id: RoomId, content: ContentPack): RoomState =
    rooms[id] ?: RoomState(
        monsterHp = content.rooms.getValue(id).monster?.let { content.monsters.getValue(it).maxHp }
    )

/** Same tolerance for meters: saves predating a meter read it at its start value. */
fun GameState.meterFor(id: MeterId, content: ContentPack): Int =
    meters[id] ?: content.meters.getValue(id).start

fun GameState.flag(name: String): Int = flags[name] ?: 0

/** Append narration, assigning monotonic ids and trimming to FEED_LIMIT. */
fun GameState.appendFeed(entries: List<Pair<FeedKind, String>>): GameState {
    if (entries.isEmpty()) return this
    var id = nextFeedId
    val appended = entries.map { (kind, text) -> FeedEntry(id++, turn, kind, text) }
    return copy(
        feed = (feed + appended).takeLast(GameState.FEED_LIMIT),
        nextFeedId = id,
    )
}
