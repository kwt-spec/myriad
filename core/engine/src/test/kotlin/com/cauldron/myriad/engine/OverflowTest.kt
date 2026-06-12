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
        assertEquals(Engine.DAMAGE_CAP, damage(Int.MAX_VALUE, 6, 0, crit = true, halved = false))
        assertEquals(Engine.DAMAGE_CAP, damage(Int.MAX_VALUE, 6, Int.MIN_VALUE + 10, crit = true, halved = false))
        assertEquals(1, damage(1, 1, Int.MAX_VALUE, crit = false, halved = false))
        assertEquals(1, damage(Int.MIN_VALUE + 10, 1, Int.MAX_VALUE, crit = true, halved = true))
    }

    @Test
    fun `damage floor and crit interact correctly at normal scale`() {
        assertEquals(8, damage(3, 6, 1, crit = false, halved = false))
        assertEquals(16, damage(3, 6, 1, crit = true, halved = false))
        assertEquals(8, damage(3, 6, 1, crit = true, halved = true))
        assertEquals(1, damage(1, 1, 50, crit = false, halved = false))
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

        val result = engine.step(state, Action.Attack)
        val ourHit = result.events.filterIsInstance<Event.PlayerStruckMonster>().single()
        assertEquals(1, ourHit.damage, "vs MAX defense, damage clamps to the floor of 1")

        val theirHit = result.events.filterIsInstance<Event.MonsterStruckPlayer>().single()
        assertEquals(Engine.DAMAGE_CAP, theirHit.damage, "MAX attack clamps to the cap, not overflow")
        assertTrue(result.events.any { it is Event.PlayerDied })
        assertIs<Mode.Dead>(result.state.mode)
        assertEquals(0, result.state.player.hp, "hp floors at zero, never negative")
    }
}
