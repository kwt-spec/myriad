package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.persist.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaveRoundtripTest {

    private val engine = Engine(TestWorlds.cellarLike())

    @Test
    fun `random walks roundtrip byte-exact and continue identically`() {
        for (seed in 1L..200L) {
            val steps = (seed % 60 + 5).toInt()
            val walk = randomWalk(engine, seed, maxSteps = steps)
            val save = SaveCodec.fresh(walk.state)

            val decoded = SaveCodec.decode(SaveCodec.encode(save))
            assertEquals(save, decoded, "seed $seed: decoded save differs")

            // The restored game must be indistinguishable going forward, not just equal at rest.
            val legal = engine.legalActions(walk.state)
            if (legal.isNotEmpty()) {
                val action = legal.first()
                val original = engine.step(walk.state, action)
                val restored = engine.step(decoded.state, action)
                assertEquals(original.state, restored.state, "seed $seed: post-restore step diverged")
                assertEquals(original.events, restored.events, "seed $seed: post-restore events diverged")
            }
        }
    }

    @Test
    fun `every mode variant roundtrips`() {
        // Exploring
        val exploring = engine.newGame(1, "Hero")
        assertIs<Mode.Exploring>(exploring.mode)
        assertEquals(exploring, SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(exploring))).state)

        // Combat
        var combat = engine.newGame(1, "Hero")
        combat = engine.step(combat, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(combat.mode)
        assertEquals(combat, SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(combat))).state)

        // Dead — brutal monster guarantees death on the first exchange.
        val brutalEngine = Engine(TestWorlds.brutal())
        var dead = brutalEngine.newGame(1, "Doomed")
        dead = brutalEngine.step(dead, Action.Move(TestWorlds.PASSAGE)).state
        while (dead.mode is Mode.Combat) {
            dead = brutalEngine.step(dead, Action.Attack).state
        }
        assertIs<Mode.Dead>(dead.mode)
        assertEquals(dead, SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(dead))).state)

        // Victory
        var victory = engine.newGame(1, "Hero")
        victory = engine.step(victory, Action.Search).state
        victory = engine.step(victory, Action.Take(TestWorlds.SWORD)).state
        victory = engine.step(victory, Action.Equip(TestWorlds.SWORD)).state
        victory = engine.step(victory, Action.Move(TestWorlds.PASSAGE)).state
        while (victory.mode is Mode.Combat) victory = engine.step(victory, Action.Attack).state
        victory = engine.step(victory, Action.Move(TestWorlds.STAIR)).state
        assertIs<Mode.Victory>(victory.mode)
        assertEquals(victory, SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(victory))).state)
    }

    @Test
    fun `save preserves rng state so future rolls match`() {
        var state = engine.newGame(42, "Hero")
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(state.mode)

        val restored = SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(state))).state
        // Ten combat rounds of identical rolls prove the RNG streams survived intact.
        var a = state
        var b = restored
        var rounds = 0
        while (a.mode is Mode.Combat && rounds < 10) {
            val ra = engine.step(a, Action.Defend)
            val rb = engine.step(b, Action.Defend)
            assertEquals(ra.events, rb.events, "round $rounds rolls diverged")
            a = ra.state
            b = rb.state
            rounds++
            if (a.mode is Mode.Dead) break
        }
        assertTrue(rounds > 0)
    }
}
