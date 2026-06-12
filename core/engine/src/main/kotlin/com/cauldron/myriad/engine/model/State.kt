package com.cauldron.myriad.engine.model

import com.cauldron.myriad.engine.rng.RngState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val gold: Int,
    val inventory: List<ItemId>,
    val equipped: ItemId?,
)

@Serializable
data class RoomState(
    val searched: Boolean = false,
    /** Current monster HP; null = dead or never present. */
    val monsterHp: Int? = null,
    val itemsOnFloor: List<ItemId> = emptyList(),
)

@Serializable
sealed interface Mode {
    @Serializable
    @SerialName("exploring")
    data object Exploring : Mode

    @Serializable
    @SerialName("combat")
    data class Combat(val monster: MonsterId) : Mode

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
) {
    companion object {
        const val FEED_LIMIT = 500
    }
}

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
