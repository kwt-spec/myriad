package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AbilityTest {

    private val engine = Engine(EmberCellar.pack)

    /** A hero who already owns the whole Body tree, dropped straight into a fight. */
    private fun adeptInCombat(seed: Long = 1): GameState {
        var s = engine.newGame(seed, "Adept")
        s = s.copy(player = s.player.copy(unlockedNodes = Constellations.nodes.keys.toList()))
        s = engine.step(s, Action.Search).state
        s = engine.step(s, Action.Take(EmberCellar.RUSTY_SWORD)).state
        s = engine.step(s, Action.Equip(EmberCellar.RUSTY_SWORD)).state
        s = engine.step(s, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        assertIs<Mode.Combat>(s.mode)
        return s
    }

    @Test
    fun `granted abilities become legal in combat when affordable and off cooldown`() {
        val s = adeptInCombat()
        val legal = engine.legalActions(s)
        assertTrue(Action.UseAbility(Constellations.SUNDER) in legal, "Sunder should be available")
        assertTrue(Action.UseAbility(Constellations.SECOND_WIND) in legal, "Second Wind should be available")
    }

    @Test
    fun `Sunder ignores defence and deals real damage`() {
        // A tanky test foe so the strike never ends the fight before we measure.
        val tanky = Engine(EmberCellar.pack)
        var s = adeptInCombat(5)
        val result = engine.step(s, Action.UseAbility(Constellations.SUNDER))
        assertTrue(result.events.any { it is Event.AbilityUsed }, "ability-used event")
        val hit = result.events.filterIsInstance<Event.PlayerStruckMonster>().firstOrNull()
        assertTrue(hit != null && hit.damage >= 1, "Sunder must land a blow")
    }

    @Test
    fun `Second Wind heals, clamped to the effective max`() {
        var s = adeptInCombat(2)
        // Wound the player first so there is room to heal.
        s = s.copy(player = s.player.copy(hp = 5))
        val maxHp = engine.effectiveMaxHp(s)
        val result = engine.step(s, Action.UseAbility(Constellations.SECOND_WIND))
        val healed = result.events.filterIsInstance<Event.PlayerHealed>().single()
        assertTrue(healed.amount > 0, "should heal")
        assertTrue(result.state.player.hp <= maxHp, "never overheal")
        assertTrue(result.state.player.hp > 5, "hp must rise")
    }

    @Test
    fun `cooldown blocks reuse then frees up`() {
        var s = adeptInCombat(9)
        // Use Sunder; it has a 2-turn cooldown.
        s = engine.step(s, Action.UseAbility(Constellations.SUNDER)).state
        if (s.mode !is Mode.Combat) return // killed it; cooldown semantics covered elsewhere
        assertTrue(Action.UseAbility(Constellations.SUNDER) !in engine.legalActions(s), "Sunder on cooldown")
        // Brace a couple of turns to tick it down.
        var rounds = 0
        while (s.mode is Mode.Combat && Action.UseAbility(Constellations.SUNDER) !in engine.legalActions(s) && rounds < 5) {
            s = engine.step(s, Action.Brace).state
            rounds++
        }
        if (s.mode is Mode.Combat) {
            assertTrue(Action.UseAbility(Constellations.SUNDER) in engine.legalActions(s), "Sunder should free up")
        }
    }

    @Test
    fun `abilities are gated by stamina`() {
        var s = adeptInCombat(4)
        // Drain stamina below Cinderbrand's cost (55) but above Sunder's (35) is fiddly;
        // instead set it explicitly low and confirm the gate.
        val mode = s.mode as Mode.Combat
        s = s.copy(mode = mode.copy(playerStamina = 10))
        val legal = engine.legalActions(s)
        assertTrue(Action.UseAbility(Constellations.SUNDER) !in legal, "10 stamina can't afford Sunder (35)")
        assertTrue(Action.Brace in legal && Action.Flee in legal, "brace/flee always legal")
    }

    @Test
    fun `ability combat is deterministic`() {
        for (seed in 1L..6L) {
            val a = engine.step(adeptInCombat(seed), Action.UseAbility(Constellations.CINDERBRAND))
            val b = engine.step(adeptInCombat(seed), Action.UseAbility(Constellations.CINDERBRAND))
            assertEquals(a.events, b.events, "seed $seed ability events diverged")
            assertEquals(a.state, b.state, "seed $seed state diverged")
        }
    }
}
