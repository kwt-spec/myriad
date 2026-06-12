package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineFlowTest {

    private val engine = Engine(TestWorlds.cellarLike())

    @Test
    fun `telegraph-aware fighter survives the armed rat for every seed`() {
        var wins = 0
        for (seed in 1L..50L) {
            var state = engine.newGame(seed, "Hero")
            state = engine.step(state, Action.Search).state
            state = engine.step(state, Action.Take(TestWorlds.SWORD)).state
            state = engine.step(state, Action.Equip(TestWorlds.SWORD)).state
            assertEquals(4, engine.playerAttack(state), "seed $seed: sword not applied")

            state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
            assertIs<Mode.Combat>(state.mode, "seed $seed: combat should start")

            state = fightSmart(engine, state)
            assertIs<Mode.Exploring>(state.mode, "seed $seed: smart fighter should win, hp=${state.player.hp}")
            assertTrue(state.player.hp > 0, "seed $seed")
            assertTrue(state.player.gold in 2..6, "seed $seed: gold ${state.player.gold} outside drop range")

            state = engine.step(state, Action.Move(TestWorlds.STAIR)).state
            assertIs<Mode.Victory>(state.mode, "seed $seed")
            assertTrue(engine.legalActions(state).isEmpty(), "seed $seed: victory must be terminal")
            wins++
        }
        assertEquals(50, wins)
    }

    @Test
    fun `combat opens with a readable telegraph`() {
        var state = engine.newGame(2, "Reader")
        val result = engine.step(state, Action.Move(TestWorlds.PASSAGE))
        assertTrue(result.events.any { it is Event.CombatStarted })
        assertTrue(result.events.any { it is Event.MonsterIntentDrawn }, "an intent must be telegraphed at combat start")
        state = result.state
        val mode = assertIs<Mode.Combat>(state.mode)
        assertTrue(mode.monsterIntent.value.isNotEmpty(), "intent must be drawn, not sentinel")
        assertTrue(state.feed.any { it.text.startsWith("⚠") }, "telegraph must reach the feed")
    }

    @Test
    fun `search reveals item exactly once`() {
        var state = engine.newGame(1, "Hero")
        assertTrue(Action.Search in engine.legalActions(state))
        state = engine.step(state, Action.Search).state
        assertTrue(Action.Search !in engine.legalActions(state), "search must not repeat")
        assertTrue(Action.Take(TestWorlds.SWORD) in engine.legalActions(state))
    }

    @Test
    fun `fleeing returns to the previous room or kills you trying`() {
        var state = engine.newGame(3, "Coward")
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(state.mode)

        var attempts = 0
        while (state.mode is Mode.Combat && attempts < 30) {
            state = engine.step(state, Action.Flee).state
            attempts++
        }
        when (val mode = state.mode) {
            is Mode.Exploring -> assertEquals(TestWorlds.CELLAR, state.currentRoom, "flee must retreat to the room you came from")
            is Mode.Dead -> assertTrue(engine.legalActions(state).isEmpty())
            else -> throw AssertionError("unexpected mode after fleeing: $mode")
        }
    }

    @Test
    fun `slaying a monster on the goal room wins immediately`() {
        val goalEngine = Engine(TestWorlds.monsterOnGoal())
        var state = goalEngine.newGame(5, "Hero")
        state = goalEngine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(state.mode)
        val result = goalEngine.step(state, Action.QuickStrike)
        assertTrue(result.events.any { it is Event.MonsterSlain }, "1-hp wisp must die to any hit")
        assertTrue(result.events.any { it is Event.GameWon }, "goal room must trigger victory after the kill")
        assertIs<Mode.Victory>(result.state.mode)
    }

    @Test
    fun `illegal actions are rejected loudly`() {
        val state = engine.newGame(1, "Hero")
        assertFailsWith<IllegalArgumentException> { engine.step(state, Action.QuickStrike) }
        assertFailsWith<IllegalArgumentException> { engine.step(state, Action.Take(TestWorlds.SWORD)) }
    }

    @Test
    fun `feed never exceeds its cap`() {
        var state = engine.newGame(9, "Looper")
        repeat(600) { state = engine.step(state, Action.Look).state }
        assertTrue(state.feed.size <= 500, "feed grew to ${state.feed.size}")
        assertEquals(state.feed.map { it.id }, state.feed.map { it.id }.sorted(), "feed ids must stay monotonic")
    }
}
