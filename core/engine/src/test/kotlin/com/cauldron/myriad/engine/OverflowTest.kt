package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OverflowTest {

    @Test
    fun `damage math never overflows at hostile extremes`() {
        assertEquals(Engine.DAMAGE_CAP, scaledDamage(Int.MAX_VALUE, 16, 10, 6, 0, crit = true, halved = false))
        assertEquals(Engine.DAMAGE_CAP, scaledDamage(Int.MAX_VALUE, 10, 10, 6, Int.MIN_VALUE + 10, crit = true, halved = false))
        assertEquals(1, scaledDamage(1, 7, 10, 1, Int.MAX_VALUE, crit = false, halved = false))
        assertEquals(1, scaledDamage(Int.MIN_VALUE + 10, 10, 10, 1, Int.MAX_VALUE, crit = true, halved = true))
    }

    @Test
    fun `power fractions, crit, and brace interact correctly at normal scale`() {
        // attack 10, quick (×0.7 → 7): 7 + 6 − 1 = 12; crit 24; braced crit 12.
        assertEquals(12, scaledDamage(10, 7, 10, 6, 1, crit = false, halved = false))
        assertEquals(24, scaledDamage(10, 7, 10, 6, 1, crit = true, halved = false))
        assertEquals(12, scaledDamage(10, 7, 10, 6, 1, crit = true, halved = true))
        // attack 3, heavy (×1.6 → 4): 4 + 6 − 1 = 9; floor of 1 vs huge defense.
        assertEquals(9, scaledDamage(3, 16, 10, 6, 1, crit = false, halved = false))
        assertEquals(1, scaledDamage(1, 10, 10, 1, 50, crit = false, halved = false))
    }

    @Test
    fun `gold addition clamps instead of wrapping`() {
        assertEquals(Int.MAX_VALUE, addClamped(Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, addClamped(Int.MAX_VALUE - 1, 5))
        assertEquals(7, addClamped(3, 4))
        assertEquals(0, addClamped(0, 0))
    }

    @Test
    fun `a stat-maxed monster cannot crash the engine, only kill you`() {
        val engine = Engine(TestWorlds.brutal())
        var state = engine.newGame(1, "Doomed")
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        assertIs<Mode.Combat>(state.mode)

        var struckForCap = false
        var guard = 0
        while (state.mode is Mode.Combat && guard < 10) {
            val result = engine.step(state, Action.QuickStrike)
            for (event in result.events) {
                when (event) {
                    is Event.PlayerStruckMonster ->
                        assertEquals(1, event.damage, "vs MAX defense, damage clamps to the floor of 1")
                    is Event.MonsterStruckPlayer -> {
                        assertEquals(Engine.DAMAGE_CAP, event.damage, "MAX attack clamps to the cap, not overflow")
                        struckForCap = true
                    }
                    else -> {}
                }
            }
            state = result.state
            guard++
        }
        assertIs<Mode.Dead>(state.mode)
        assertTrue(struckForCap, "the horror should have landed at least one capped hit")
        assertEquals(0, state.player.hp, "hp floors at zero, never negative")
    }
}
