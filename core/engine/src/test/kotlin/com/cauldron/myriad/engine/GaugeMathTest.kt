package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.persist.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GaugeMathTest {

    private fun intoCombat(engine: Engine, seed: Long = 1): com.cauldron.myriad.engine.model.GameState {
        var state = engine.newGame(seed, "Gauge")
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(state.mode)
        return state
    }

    @Test
    fun `quick returns before the rat moves, heavy gives it a turn`() {
        // Durable rat so strikes never end combat mid-measurement.
        val engine = Engine(TestWorlds.cellarLike(ratHp = 1_000))
        val state = intoCombat(engine)

        // Quick: recovery 700 → 7 ticks → rat (speed 80) gains 560 → no strike.
        val quick = engine.step(state, Action.QuickStrike)
        assertEquals(0, quick.events.count { it is Event.MonsterStruckPlayer }, "rat must not act inside a quick")
        val quickTick = quick.events.filterIsInstance<Event.CombatTicked>().single()
        assertEquals(560, quickTick.monsterGauge)
        assertEquals(Mode.Combat.GAUGE_MAX, quickTick.playerGauge)

        // Heavy: recovery 1500 → 15 ticks → rat gains 1200 → exactly one strike, 200 carry.
        val heavy = engine.step(state, Action.HeavyStrike)
        assertEquals(1, heavy.events.count { it is Event.MonsterStruckPlayer }, "rat acts once inside a heavy")
        val heavyTick = heavy.events.filterIsInstance<Event.CombatTicked>().single()
        assertEquals(200, heavyTick.monsterGauge, "gauge overflow must carry, not reset")
    }

    @Test
    fun `a fast foe acts multiple times during one heavy swing`() {
        // Speed 240 × 15 ticks = 3600 → 3 wraps. Gentle stats keep the player alive.
        val engine = Engine(
            TestWorlds.cellarLike(
                ratAttack = 1, ratHp = 1_000, ratDefense = 0, ratSpeed = 240,
                moves = listOf(MoveDef(TestWorlds.GNAW, "tap", "It taps closer.", 1, 10, 1)),
            )
        )
        val result = engine.step(intoCombat(engine), Action.HeavyStrike)
        val strikes = result.events.count { it is Event.MonsterStruckPlayer }
        assertTrue(strikes >= 2, "fast foe should act at least twice inside a heavy, acted $strikes")
    }

    @Test
    fun `brace halves exactly the next hit and is consumed`() {
        // Speed 200 → brace (5 ticks) charges it to exactly 1000: one braced strike.
        val engine = Engine(
            TestWorlds.cellarLike(
                ratAttack = 10, ratHp = 1_000, ratDefense = 1, ratSpeed = 200,
                moves = listOf(MoveDef(TestWorlds.GNAW, "gnaw", "It bares its teeth.", 10, 10, 1)),
            )
        )
        val result = engine.step(intoCombat(engine), Action.Brace)
        val strike = result.events.filterIsInstance<Event.MonsterStruckPlayer>().single()
        assertTrue(strike.braced, "the strike after bracing must be braced")
        // raw = 10 + roll(1..6) − 1 = 10..15 (crit ≤30); braced = ceil(raw/2) ≤ 15.
        assertTrue(strike.damage <= 15, "braced damage ${strike.damage} exceeds halved bound")
        val tick = result.events.filterIsInstance<Event.CombatTicked>().single()
        assertEquals(false, tick.braced, "brace must be consumed by the hit")
    }

    @Test
    fun `unconsumed brace persists into the next state`() {
        // Slow rat (80): brace's 5 ticks only charge 400 → no strike → brace persists.
        val engine = Engine(TestWorlds.cellarLike(ratHp = 1_000))
        val result = engine.step(intoCombat(engine), Action.Brace)
        assertEquals(0, result.events.count { it is Event.MonsterStruckPlayer })
        val mode = assertIs<Mode.Combat>(result.state.mode)
        assertTrue(mode.braced, "unconsumed brace must persist")
    }

    @Test
    fun `stamina gates strikes and brace restores it`() {
        val engine = Engine(TestWorlds.cellarLike(ratAttack = 1, ratHp = 1_000))
        var state = intoCombat(engine)

        state = engine.step(state, Action.HeavyStrike).state // 100 → 60
        state = engine.step(state, Action.HeavyStrike).state // 60 → 20
        var mode = assertIs<Mode.Combat>(state.mode)
        assertEquals(20, mode.playerStamina)
        val gated = engine.legalActions(state)
        assertTrue(Action.HeavyStrike !in gated, "heavy must be stamina-gated at 20")
        assertTrue(Action.QuickStrike in gated)
        assertTrue(Action.Brace in gated && Action.Flee in gated, "brace/flee always legal")

        state = engine.step(state, Action.Brace).state // 20 → 45
        mode = assertIs<Mode.Combat>(state.mode)
        assertEquals(45, mode.playerStamina)
        assertTrue(Action.HeavyStrike in engine.legalActions(state), "45 stamina re-enables heavy")
    }

    @Test
    fun `combat ticked event and resulting mode agree exactly`() {
        val engine = Engine(TestWorlds.cellarLike(ratHp = 1_000))
        var state = intoCombat(engine, seed = 9)
        repeat(6) {
            val legal = engine.legalActions(state)
            val result = engine.step(state, legal.first())
            val tick = result.events.filterIsInstance<Event.CombatTicked>().singleOrNull()
            val mode = result.state.mode
            if (tick != null && mode is Mode.Combat) {
                assertEquals(tick.playerGauge, mode.playerGauge)
                assertEquals(tick.monsterGauge, mode.monsterGauge)
                assertEquals(tick.playerStamina, mode.playerStamina)
                assertEquals(tick.braced, mode.braced)
                assertEquals(tick.monsterIntent, mode.monsterIntent)
            }
            state = result.state
            if (state.mode !is Mode.Combat) return
        }
    }

    @Test
    fun `combat resolution is deterministic`() {
        val engine = Engine(TestWorlds.cellarLike(ratHp = 50))
        for (seed in 1L..20L) {
            val a = engine.step(intoCombat(engine, seed), Action.HeavyStrike)
            val b = engine.step(intoCombat(engine, seed), Action.HeavyStrike)
            assertEquals(a.events, b.events, "seed $seed: combat events diverged")
            assertEquals(a.state, b.state, "seed $seed: combat state diverged")
        }
    }

    @Test
    fun `mid-combat roundtrip preserves the telegraph and future rolls`() {
        val engine = Engine(TestWorlds.cellarLike(ratHp = 100))
        var state = intoCombat(engine, seed = 4)
        state = engine.step(state, Action.QuickStrike).state
        val before = assertIs<Mode.Combat>(state.mode)

        val restored = SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(state))).state
        val after = assertIs<Mode.Combat>(restored.mode)
        assertEquals(before.monsterIntent, after.monsterIntent, "telegraph must survive save/load")

        var a = state
        var b = restored
        repeat(5) {
            if (a.mode !is Mode.Combat) return
            val ra = engine.step(a, Action.Brace)
            val rb = engine.step(b, Action.Brace)
            assertEquals(ra.events, rb.events, "post-restore rolls diverged")
            a = ra.state
            b = rb.state
        }
    }

    @Test
    fun `sentinel intent resolves to the first move instead of crashing`() {
        val engine = Engine(TestWorlds.cellarLike())
        val state = intoCombat(engine)
        val mode = assertIs<Mode.Combat>(state.mode)
        val sentinel = state.copy(mode = mode.copy(monsterIntent = MoveId("renamed-or-missing")))
        // Must resolve via moveFor fallback, not throw.
        engine.step(sentinel, Action.QuickStrike)
    }
}
