package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.meterFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeterTest {

    @Test
    fun `every action burns the meter by its rate`() {
        val engine = Engine(TestWorlds.frostbitten(cap = 6))
        var state = engine.newGame(1, "Cold")
        assertEquals(6, state.meterFor(TestWorlds.WARMTH, engine.content))

        state = engine.step(state, Action.Look).state
        assertEquals(5, state.meterFor(TestWorlds.WARMTH, engine.content))
        state = engine.step(state, Action.Search).state
        assertEquals(4, state.meterFor(TestWorlds.WARMTH, engine.content))
    }

    @Test
    fun `empty meter draws blood every action`() {
        val engine = Engine(TestWorlds.frostbitten(cap = 2, emptyDamage = 3))
        var state = engine.newGame(1, "Cold")
        state = engine.step(state, Action.Look).state // warmth 1
        state = engine.step(state, Action.Look).state // warmth 0 → chill starts
        val hpAtZero = state.player.hp
        val result = engine.step(state, Action.Look)
        val tick = result.events.filterIsInstance<Event.MetersTicked>().single()
        assertEquals(3, tick.chillDamage)
        assertEquals(hpAtZero - 3, result.state.player.hp)
    }

    @Test
    fun `camping restores meters to cap and burns nothing`() {
        val engine = Engine(TestWorlds.frostbitten(cap = 6))
        var state = engine.newGame(1, "Cold")
        repeat(4) { state = engine.step(state, Action.Look).state }
        assertEquals(2, state.meterFor(TestWorlds.WARMTH, engine.content))

        assertTrue(Action.Camp in engine.legalActions(state), "start room is a haven")
        val result = engine.step(state, Action.Camp)
        assertTrue(result.events.any { it is Event.Camped })
        assertTrue(result.events.none { it is Event.MetersTicked }, "camp must not burn")
        assertEquals(6, result.state.meterFor(TestWorlds.WARMTH, engine.content))
        assertTrue(result.state.feed.last().text.contains("test embers"), "campText narrates")
    }

    @Test
    fun `camp is only legal in havens and only when meters exist`() {
        val frostEngine = Engine(TestWorlds.frostbitten())
        var state = frostEngine.newGame(1, "Cold")
        state = frostEngine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        // Combat first; even after it, the passage is no haven.
        if (state.mode is Mode.Combat) state = fightSmart(frostEngine, state)
        if (state.mode is Mode.Exploring && state.currentRoom == TestWorlds.PASSAGE) {
            assertTrue(Action.Camp !in frostEngine.legalActions(state), "no camping outside havens")
        }

        val meterless = Engine(TestWorlds.cellarLike())
        assertTrue(
            Action.Camp !in meterless.legalActions(meterless.newGame(1, "Warm")),
            "no meters, no camp verb — the unfolding-verb principle"
        )
    }

    @Test
    fun `the cold can kill, terminally and honestly`() {
        val engine = Engine(TestWorlds.frostbitten(cap = 2, emptyDamage = 5))
        var state = engine.newGame(1, "Frozen")
        var guard = 0
        // Refuse to camp; pace the cellar until the cold wins (hp 20 / 5 per turn).
        while (state.mode is Mode.Exploring && guard < 30) {
            state = engine.step(state, Action.Look).state
            guard++
        }
        assertIs<Mode.Dead>(state.mode, "the cold must eventually kill")
        assertEquals(0, state.player.hp)
        assertTrue(engine.legalActions(state).isEmpty(), "cold death is terminal")
        assertTrue(state.feed.any { "DEATH" in it.text }, "the epitaph fires for cold deaths too")
    }

    @Test
    fun `survival ticks are deterministic and replayable`() {
        val engine = Engine(TestWorlds.frostbitten())
        for (seed in 1L..10L) {
            val walk = randomWalk(engine, seed, maxSteps = 80)
            var replayed = engine.newGame(seed, "Bot")
            for (action in walk.actions) replayed = engine.step(replayed, action).state
            assertEquals(walk.state, replayed, "seed $seed: survival replay diverged")
        }
    }

    @Test
    fun `no double death when combat and cold land together`() {
        // Brutal monster kills outright; the tick must not append a second PlayerDied.
        val engine = Engine(
            com.cauldron.myriad.engine.model.ContentPack(
                version = "test-coldbrutal/1",
                intro = "x",
                startRoom = TestWorlds.CELLAR,
                rooms = TestWorlds.brutal().rooms.mapValues { (id, def) ->
                    if (id == TestWorlds.CELLAR) def.copy(haven = true, campText = "x") else def
                },
                items = emptyMap(),
                monsters = TestWorlds.brutal().monsters,
                meters = TestWorlds.warmthMeter(cap = 2),
            )
        )
        var state = engine.newGame(1, "Doomed")
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        var guard = 0
        while (state.mode is Mode.Combat && guard < 10) {
            val result = engine.step(state, Action.QuickStrike)
            assertTrue(
                result.events.count { it is Event.PlayerDied } <= 1,
                "exactly one death event allowed: ${result.events}"
            )
            state = result.state
            guard++
        }
        assertIs<Mode.Dead>(state.mode)
    }
}
