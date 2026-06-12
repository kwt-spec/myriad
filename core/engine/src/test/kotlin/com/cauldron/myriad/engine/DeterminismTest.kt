package com.cauldron.myriad.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class DeterminismTest {

    @Test
    fun `replaying the recorded actions reproduces the exact final state`() {
        val engine = Engine(TestWorlds.cellarLike())
        for (seed in 1L..20L) {
            val walk = randomWalk(engine, seed, maxSteps = 120)

            var replayed = engine.newGame(seed, "Bot")
            for (action in walk.actions) {
                replayed = engine.step(replayed, action).state
            }

            // Full structural equality: player, rooms, mode, RNG state, and feed prose.
            assertEquals(walk.state, replayed, "seed $seed: replay diverged")
        }
    }

    @Test
    fun `two engines with the same content are interchangeable`() {
        val a = Engine(TestWorlds.cellarLike())
        val b = Engine(TestWorlds.cellarLike())
        val walkA = randomWalk(a, seed = 77, maxSteps = 100)
        var stateB = b.newGame(77, "Bot")
        for (action in walkA.actions) stateB = b.step(stateB, action).state
        assertEquals(walkA.state, stateB)
    }
}
