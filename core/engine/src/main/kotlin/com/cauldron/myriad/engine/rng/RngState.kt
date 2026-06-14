package com.cauldron.myriad.engine.rng

import kotlinx.serialization.Serializable

/**
 * Named streams: each system rolls from its own generator, so adding rolls to one
 * system never shifts another system's sequence (MASTER_PLAN §9). New systems get
 * NEW streams appended here — never reorder or remove entries; saves reference them.
 */
@Serializable
enum class RngStream { COMBAT, LOOT, WORLDGEN, AMBIENT, STORY }

@Serializable
data class RngState(val streams: Map<RngStream, Pcg32State>) {
    companion object {
        fun seeded(seed: Long): RngState = RngState(
            RngStream.entries.associateWith { Pcg32.seeded(seed, it.ordinal.toLong()).snapshot() }
        )
    }
}

/** Mutable working set for one resolution; snapshot back into GameState afterwards. */
class Dice(start: RngState) {
    private val rngs: Map<RngStream, Pcg32> =
        start.streams.mapValues { (_, s) -> Pcg32.fromState(s) }

    fun roll(stream: RngStream, range: IntRange): Int = rngs.getValue(stream).nextIn(range)

    fun chance(stream: RngStream, percent: Int): Boolean = roll(stream, 1..100) <= percent

    fun snapshot(): RngState = RngState(rngs.mapValues { (_, rng) -> rng.snapshot() })
}
