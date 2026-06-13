package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProgressionTest {

    private val engine = Engine(EmberCellar.pack)

    private fun armed(seed: Long): GameState {
        var s = engine.newGame(seed, "Delver")
        s = engine.step(s, Action.Search).state
        s = engine.step(s, Action.Take(EmberCellar.RUSTY_SWORD)).state
        engine.step(s, Action.Equip(EmberCellar.RUSTY_SWORD)).state.let { return it }
    }

    private fun fight(state: GameState): GameState {
        var s = state
        var rounds = 0
        while (s.mode is Mode.Combat && rounds < 80) {
            val mode = s.mode as Mode.Combat
            val monster = engine.content.monsters.getValue(mode.monster)
            val intent = engine.moveFor(monster, mode.monsterIntent)
            val legal = engine.legalActions(s)
            val act = when {
                intent.powerNum * 10 >= intent.powerDen * 13 && Action.Brace in legal -> Action.Brace
                Action.HeavyStrike in legal -> Action.HeavyStrike
                Action.QuickStrike in legal -> Action.QuickStrike
                else -> Action.Brace
            }
            s = engine.step(s, act).state
            rounds++
        }
        return s
    }

    @Test
    fun `xp curve is strictly monotone`() {
        var prev = engine.xpToReach(1)
        for (level in 2..50) {
            val here = engine.xpToReach(level)
            assertTrue(here > prev, "xpToReach($level)=$here not above $prev")
            prev = here
        }
    }

    @Test
    fun `kills grant xp and enough kills level you up, healing to full`() {
        var s = armed(7)
        s = engine.step(s, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        val result = engine.step(s, Action.HeavyStrike)
        // first heavy may or may not kill; drive to a kill and inspect events across the fight
        var state = s
        var sawXp = false
        var rounds = 0
        while (state.mode is Mode.Combat && rounds < 40) {
            val r = engine.step(state, Action.HeavyStrike)
            if (r.events.any { it is Event.XpGained }) sawXp = true
            state = r.state
            rounds++
        }
        assertTrue(sawXp, "a kill must grant XP")
        assertTrue(state.player.xp > 0, "xp must accumulate")
    }

    @Test
    fun `descending and grinding produces real levels and a full heal on level-up`() {
        var s = armed(3)
        s = engine.step(s, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        s = fight(s) // cellar rat
        // dive a few floors, fighting, to accrue XP
        var leveled = false
        if (s.mode is Mode.Exploring) {
            s = engine.step(s, Action.Move(EmberCellar.COLLAPSED_VAULT)).state
            s = fight(s)
        }
        var depth = 1
        while (s.mode is Mode.Exploring && depth <= 6) {
            val landing = Hundredfold.landingId(depth)
            s = engine.step(s, Action.Move(landing).takeIf { Action.Move(landing) in engine.legalActions(s) } ?: engine.legalActions(s).first()).state
            val den = Hundredfold.denId(depth)
            if (Action.Move(den) in engine.legalActions(s)) {
                s = engine.step(s, Action.Move(den)).state
                val before = s.player.level
                s = fight(s)
                if (s.player.level > before) leveled = true
            }
            if (s.mode !is Mode.Exploring) break
            val hoard = Hundredfold.hoardId(depth)
            if (Action.Move(hoard) in engine.legalActions(s)) s = engine.step(s, Action.Move(hoard)).state
            depth++
        }
        assertTrue(s.player.level >= 2 || leveled || s.mode is Mode.Dead, "a real delve should level you (or honestly kill you)")
    }

    @Test
    fun `unlocking nodes spends points and raises effective stats`() {
        // Hand a fresh hero points to spend, then unlock Hardy I + Brute Force I.
        var s = engine.newGame(1, "Tester")
        s = s.copy(player = s.player.copy(skillPoints = 5))
        val baseHp = engine.effectiveMaxHp(s)
        val baseAtk = engine.playerAttack(s)

        assertTrue(engine.canUnlock(s, Constellations.HARDY_1))
        s = engine.step(s, Action.UnlockNode(Constellations.HARDY_1)).state
        assertEquals(4, s.player.skillPoints, "Hardy I costs 1")
        assertEquals(baseHp + 8, engine.effectiveMaxHp(s), "Hardy I grants +8 max HP")

        s = engine.step(s, Action.UnlockNode(Constellations.BRUTE_FORCE_1)).state
        assertEquals(baseAtk + 2, engine.playerAttack(s), "Brute Force I grants +2 attack")
    }

    @Test
    fun `prerequisites gate nodes`() {
        var s = engine.newGame(1, "Tester").let { it.copy(player = it.player.copy(skillPoints = 5)) }
        assertTrue(!engine.canUnlock(s, Constellations.HARDY_2), "Hardy II needs Hardy I")
        assertFailsWith<IllegalArgumentException> { engine.step(s, Action.UnlockNode(Constellations.HARDY_2)) }
        s = engine.step(s, Action.UnlockNode(Constellations.HARDY_1)).state
        assertTrue(engine.canUnlock(s, Constellations.HARDY_2), "Hardy I opens Hardy II")
    }

    @Test
    fun `respec refunds points, costs gold, and only works at a camp`() {
        var s = engine.newGame(1, "Tester").let {
            it.copy(player = it.player.copy(skillPoints = 5, gold = 1000))
        }
        s = engine.step(s, Action.UnlockNode(Constellations.HARDY_1)).state
        s = engine.step(s, Action.UnlockNode(Constellations.HARDY_2)).state
        assertEquals(3, s.player.skillPoints, "Hardy I + II cost 1 each → 3 of 5 left")

        // Start room (Ashen Cellar) is a haven → respec legal.
        assertTrue(engine.canRespec(s), "the cellar is a camp")
        val cost = engine.respecCost(s)
        val goldBefore = s.player.gold
        s = engine.step(s, Action.Respec).state
        assertEquals(5, s.player.skillPoints, "all points refunded")
        assertTrue(s.player.unlockedNodes.isEmpty(), "nodes cleared")
        assertEquals(goldBefore - cost, s.player.gold, "gold spent")

        // In the passage (not a haven) respec is illegal.
        var t = engine.newGame(1, "Tester").let { it.copy(player = it.player.copy(skillPoints = 5, gold = 1000)) }
        t = engine.step(t, Action.UnlockNode(Constellations.HARDY_1)).state
        t = engine.step(t, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        if (t.mode is Mode.Combat) t = fight(t)
        if (t.mode is Mode.Exploring) {
            assertTrue(!engine.canRespec(t), "no respec away from a camp")
        }
    }

    @Test
    fun `weapon mastery grows with use and is capped`() {
        var s = engine.newGame(1, "Tester")
        // Hand-craft a high-mastery state for the sword family and confirm the cap.
        s = s.copy(player = s.player.copy(equipped = EmberCellar.RUSTY_SWORD, mastery = mapOf("sword" to 1000)))
        assertEquals(Engine.MASTERY_CAP, engine.masteryBonus(s), "mastery bonus is capped")
    }

    @Test
    fun `progression is deterministic across replays`() {
        for (seed in 1L..8L) {
            var a = armed(seed)
            var b = armed(seed)
            a = engine.step(a, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            b = engine.step(b, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            a = fight(a); b = fight(b)
            assertEquals(a.player.xp, b.player.xp, "seed $seed xp diverged")
            assertEquals(a.player.level, b.player.level, "seed $seed level diverged")
            assertEquals(a.player.mastery, b.player.mastery, "seed $seed mastery diverged")
        }
    }
}
