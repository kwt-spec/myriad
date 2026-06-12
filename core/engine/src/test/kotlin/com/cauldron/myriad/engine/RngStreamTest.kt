package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.rng.Dice
import com.cauldron.myriad.engine.rng.RngState
import com.cauldron.myriad.engine.rng.RngStream
import kotlin.test.Test
import kotlin.test.assertEquals

class RngStreamTest {

    @Test
    fun `draining one stream never shifts another`() {
        val drained = Dice(RngState.seeded(7))
        repeat(100) { drained.roll(RngStream.COMBAT, 1..1_000_000) }

        val fresh = Dice(RngState.seeded(7))

        // LOOT/WORLDGEN/AMBIENT must be untouched by 100 COMBAT rolls.
        assertEquals(fresh.roll(RngStream.LOOT, 1..1_000_000), drained.roll(RngStream.LOOT, 1..1_000_000))
        assertEquals(fresh.roll(RngStream.WORLDGEN, 1..1_000_000), drained.roll(RngStream.WORLDGEN, 1..1_000_000))
        assertEquals(fresh.roll(RngStream.AMBIENT, 1..1_000_000), drained.roll(RngStream.AMBIENT, 1..1_000_000))
    }

    @Test
    fun `snapshot only advances streams that rolled`() {
        val seeded = RngState.seeded(42)
        val dice = Dice(seeded)
        dice.roll(RngStream.COMBAT, 1..6)
        val after = dice.snapshot()

        assertEquals(seeded.streams.getValue(RngStream.LOOT), after.streams.getValue(RngStream.LOOT))
        assertEquals(seeded.streams.getValue(RngStream.WORLDGEN), after.streams.getValue(RngStream.WORLDGEN))
        assertEquals(seeded.streams.getValue(RngStream.AMBIENT), after.streams.getValue(RngStream.AMBIENT))
        assertEquals(false, seeded.streams.getValue(RngStream.COMBAT) == after.streams.getValue(RngStream.COMBAT))
    }

    @Test
    fun `streams from the same seed are distinct`() {
        val dice = Dice(RngState.seeded(123))
        val values = RngStream.entries.map { dice.roll(it, 1..1_000_000_0) }
        assertEquals(values.size, values.toSet().size, "streams produced identical first values: $values")
    }
}
