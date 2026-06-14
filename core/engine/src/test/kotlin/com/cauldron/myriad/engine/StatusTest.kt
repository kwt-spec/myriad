package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.AbilityDef
import com.cauldron.myriad.engine.model.AbilityEffect
import com.cauldron.myriad.engine.model.AbilityId
import com.cauldron.myriad.engine.model.AbilityKind
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ConstellationNodeDef
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.NodeEffect
import com.cauldron.myriad.engine.model.NodeId
import com.cauldron.myriad.engine.model.StatusKind
import com.cauldron.myriad.engine.persist.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatusTest {

    private val BLEED = AbilityId("ab_bleed")
    private val STUN = AbilityId("ab_stun")
    private val GUARD = AbilityId("ab_guard")

    private fun pack(): ContentPack {
        fun comp(vararg e: AbilityEffect) = AbilityKind.Composite(e.toList())
        val abilities = mapOf(
            BLEED to AbilityDef(BLEED, "Bleed", "x", 5, 0, comp(AbilityEffect.Damage(10, 0, 0, false, 1), AbilityEffect.Inflict(StatusKind.BLEED, 5, 3))),
            STUN to AbilityDef(STUN, "Stun", "x", 5, 0, comp(AbilityEffect.Inflict(StatusKind.STUN, 1, 2))),
            GUARD to AbilityDef(GUARD, "Guard", "x", 5, 0, comp(AbilityEffect.Empower(StatusKind.GUARD, 10, 3))),
        )
        val nodes = listOf(BLEED to "n_bleed", STUN to "n_stun", GUARD to "n_guard").associate { (ab, nid) ->
            NodeId(nid) to ConstellationNodeDef(NodeId(nid), nid, "grants", "Test", 1, emptyList(), NodeEffect.GrantAbility(ab))
        }
        return TestWorlds.cellarLike(ratHp = 1000, ratAttack = 8, ratSpeed = 100)
            .copy(abilities = abilities, nodes = nodes)
    }

    private val engine = Engine(pack())

    /** A combat state with all three abilities unlocked, in the passage fight. */
    private fun inFight(seed: Long = 1): GameState {
        var s = engine.newGame(seed, "Hexer")
        s = s.copy(player = s.player.copy(unlockedNodes = listOf(NodeId("n_bleed"), NodeId("n_stun"), NodeId("n_guard"))))
        s = engine.step(s, Action.Move(TestWorlds.PASSAGE)).state
        return assertIs<Mode.Combat>(s.mode).let { s }
    }

    private fun monsterHp(s: GameState) = s.rooms.getValue(TestWorlds.PASSAGE).monsterHp ?: 0

    @Test
    fun `bleed damages the foe over several turns after the strike`() {
        var s = inFight()
        val before = monsterHp(s)
        s = engine.step(s, Action.UseAbility(BLEED)).state // strike 10 + apply bleed 5/turn × 3
        val afterStrike = monsterHp(s)
        assertTrue(afterStrike < before, "the strike landed")
        val mode = assertIs<Mode.Combat>(s.mode)
        assertTrue(mode.statuses.any { it.kind == StatusKind.BLEED && it.onFoe }, "bleed is active")

        // Bleed (3 turns, ticked to 2 after the cast) bites at each end-of-turn.
        var hp = afterStrike
        repeat(2) {
            s = engine.step(s, Action.Brace).state
            val now = monsterHp(s)
            assertTrue(now <= hp - 5, "bleed should remove >=5 each turn ($hp -> $now)")
            hp = now
        }
    }

    @Test
    fun `stun makes the foe forfeit its next action`() {
        var s = inFight(3)
        s = engine.step(s, Action.UseAbility(STUN)).state // applies stun; effect lands NEXT turn
        val next = engine.step(s, Action.Brace)
        assertTrue(next.events.none { it is Event.MonsterStruckPlayer }, "a stunned foe deals no damage")
    }

    @Test
    fun `guard reduces incoming damage`() {
        // Without guard: measure a braced hit. With guard active: smaller hit.
        val plain = engine.step(inFight(5), Action.Brace)
            .events.filterIsInstance<Event.MonsterStruckPlayer>().firstOrNull()?.damage
        var g = inFight(5)
        g = engine.step(g, Action.UseAbility(GUARD)).state // guard takes effect next turn
        val guarded = engine.step(g, Action.Brace)
            .events.filterIsInstance<Event.MonsterStruckPlayer>().firstOrNull()?.damage
        if (plain != null && guarded != null) {
            assertTrue(guarded < plain, "guard should soften the blow ($plain -> $guarded)")
        }
    }

    @Test
    fun `active statuses survive a save and load`() {
        var s = inFight(7)
        s = engine.step(s, Action.UseAbility(BLEED)).state
        val mode = assertIs<Mode.Combat>(s.mode)
        assertTrue(mode.statuses.isNotEmpty())
        val restored = SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(s))).state
        assertEquals(mode.statuses, assertIs<Mode.Combat>(restored.mode).statuses, "statuses must roundtrip")
        // And the fight continues identically.
        assertEquals(engine.step(s, Action.Brace).events, engine.step(restored, Action.Brace).events)
    }

    @Test
    fun `status combat is deterministic`() {
        for (seed in 1L..10L) {
            val a = engine.step(inFight(seed), Action.UseAbility(BLEED))
            val b = engine.step(inFight(seed), Action.UseAbility(BLEED))
            assertEquals(a.events, b.events, "seed $seed events diverged")
            assertEquals(a.state, b.state, "seed $seed state diverged")
        }
    }
}
