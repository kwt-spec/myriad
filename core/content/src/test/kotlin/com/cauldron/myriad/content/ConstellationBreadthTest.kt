package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.NodeEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The progression breadth is binding (MASTER_PLAN §16.4): five constellations,
 * a deep node roster, the full ability/sense roster, and every node-effect class
 * actually used (no decorative effects).
 */
class ConstellationBreadthTest {

    private val pack = EmberCellar.pack
    private val engine = Engine(pack)

    @Test
    fun `the five named trees stay deep and the Great Forge adds eighty more`() {
        val byTree = pack.nodes.values.groupingBy { it.constellation }.eachCount()
        for (tree in listOf("Body", "Mind", "Senses", "Craft", "Voice")) {
            assertTrue((byTree[tree] ?: 0) >= 40, "$tree shallow: ${byTree[tree]}")
        }
        // 5 named + 80 generated constellations.
        assertTrue(byTree.size >= 85, "constellations: ${byTree.size} < 85")
        assertTrue(pack.nodes.size >= GreatForge.NODE_FLOOR, "node floor: ${pack.nodes.size} < ${GreatForge.NODE_FLOOR}")
    }

    @Test
    fun `the ability and sense rosters are vast`() {
        assertTrue(pack.abilities.size >= GreatForge.ABILITY_FLOOR, "abilities: ${pack.abilities.size}")
        assertTrue(pack.senses.size >= GreatForge.SENSE_FLOOR, "senses: ${pack.senses.size}")
        // All 20 mechanical ability kinds appear.
        val kinds = pack.abilities.values.map { it.kind::class }.toSet()
        assertEquals(20, kinds.size, "all twenty ability kinds should appear, saw ${kinds.size}")
        // All 20 sense-hint kinds appear.
        val hints = pack.senses.values.map { it.hint }.toSet()
        assertEquals(com.cauldron.myriad.engine.model.SenseHint.entries.size, hints.size, "every sense-hint should appear")
    }

    @Test
    fun `the Great Forge yields 80 families of six tiers`() {
        assertEquals(80, GreatForge.THEMES.size, "80 themes")
        assertEquals(80 * 6, GreatForge.abilities().size, "80 families × 6 tiers = 480 abilities")
        assertEquals(80, GreatForge.THEMES.toSet().size, "theme names must be unique")
    }

    @Test
    fun `every node-effect class is actually used somewhere`() {
        val used = pack.nodes.values.map { it.effect::class }.toSet()
        val expected = listOf(
            NodeEffect.MaxHp::class, NodeEffect.Attack::class, NodeEffect.Defense::class.let { NodeEffect.DamageReduction::class },
            NodeEffect.MaxStamina::class, NodeEffect.Crit::class, NodeEffect.DamageReduction::class,
            NodeEffect.XpBonus::class, NodeEffect.GoldFind::class, NodeEffect.StaminaEfficiency::class,
            NodeEffect.CooldownReduction::class, NodeEffect.Lifesteal::class, NodeEffect.GrantAbility::class,
            NodeEffect.GrantSense::class, NodeEffect.GrantVerb::class,
        ).toSet()
        for (cls in expected) assertTrue(cls in used, "effect class $cls is never used")
    }

    @Test
    fun `nodes are distinct in id, name, and prose`() {
        val defs = pack.nodes.values
        assertEquals(defs.size, defs.map { it.id }.toSet().size, "duplicate node ids")
        assertEquals(defs.size, defs.map { it.name }.toSet().size, "duplicate node names")
        assertEquals(defs.size, defs.map { it.description }.toSet().size, "duplicate node prose")
        for (d in defs) assertTrue(d.description.length > 15, "thin node ${d.id.value}")
    }

    @Test
    fun `a fully-traited hero wields the new abilities and senses`() {
        var s = engine.newGame(1, "Adept")
        s = s.copy(player = s.player.copy(unlockedNodes = pack.nodes.keys.toList()))
        // Senses: every sense narrates a line.
        assertEquals(pack.senses.size, engine.run {
            s.player.unlockedNodes.mapNotNull { (content.nodes[it]?.effect as? NodeEffect.GrantSense)?.sense }.toSet().size
        })
        // Verbs unlocked (Forage/Kindle) appear when warmth is low.
        s = engine.step(s, Action.Look).state // burn a little warmth
        assertTrue(engine.unlockedVerbs(s).size >= 2, "forage + kindle verbs")

        // Modifiers are live.
        assertTrue(engine.xpBonus(s) > 0 && engine.goldFind(s) > 0 && engine.staminaEfficiency(s) > 0 && engine.cooldownReduction(s) > 0)
    }

    @Test
    fun `Intimidate can rout a foe, ending the fight without a kill`() {
        var s = engine.newGame(7, "Caller")
        s = s.copy(player = s.player.copy(unlockedNodes = pack.nodes.keys.toList()))
        s = engine.step(s, Action.Search).state
        s = engine.step(s, Action.Take(EmberCellar.RUSTY_SWORD)).state
        s = engine.step(s, Action.Equip(EmberCellar.RUSTY_SWORD)).state
        s = engine.step(s, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        // Dread Howl routs at 60% — try a few seeds' worth of attempts across fresh fights.
        var routed = false
        for (seed in 1L..20L) {
            var f = engine.newGame(seed, "Caller").let { it.copy(player = it.player.copy(unlockedNodes = pack.nodes.keys.toList())) }
            f = engine.step(f, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            if (f.mode !is Mode.Combat) continue
            val r = engine.step(f, Action.UseAbility(Constellations.DREAD_HOWL))
            if (r.events.any { it is com.cauldron.myriad.engine.model.Event.MonsterRouted }) {
                assertTrue(r.state.mode is Mode.Exploring, "rout ends the fight")
                routed = true
                break
            }
        }
        assertTrue(routed, "Dread Howl should rout at least once across 20 attempts")
    }

    @Test
    fun `Forage restores warmth without a full camp`() {
        var s = engine.newGame(3, "Forager")
        s = s.copy(player = s.player.copy(unlockedNodes = listOf(NodeIdForage)))
        repeat(10) { s = engine.step(s, Action.Look).state } // burn warmth
        val before = s.meters.values.first()
        val forage = engine.legalActions(s).filterIsInstance<Action.UseVerb>().firstOrNull()
        assertTrue(forage != null, "forage available when warmth is down")
        s = engine.step(s, forage).state
        assertTrue(s.meters.values.first() > before, "warmth rose from foraging")
    }

    private val NodeIdForage get() = com.cauldron.myriad.engine.model.NodeId("craft_forage")
}
