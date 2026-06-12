package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertTrue

class SoftlockPropertyTest {

    @Test
    fun `every reachable non-terminal state offers at least one action`() {
        val engine = Engine(TestWorlds.cellarLike())
        for (seed in 1L..200L) {
            var state = engine.newGame(seed, "Bot")
            val picker = com.cauldron.myriad.engine.rng.Pcg32.seeded(seed, 555)
            var steps = 0
            while (steps < 200) {
                val legal = engine.legalActions(state)
                val terminal = state.mode is Mode.Dead || state.mode is Mode.Victory
                if (terminal) {
                    assertTrue(legal.isEmpty(), "seed $seed: terminal state offered actions $legal")
                    break
                }
                assertTrue(
                    legal.isNotEmpty(),
                    "SOFTLOCK seed=$seed turn=${state.turn} room=${state.currentRoom.value} mode=${state.mode}"
                )
                state = engine.step(state, legal[picker.nextBelow(legal.size)]).state
                steps++
            }
        }
    }
}
