package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Attribute
import com.cauldron.myriad.engine.model.ChoiceDef
import com.cauldron.myriad.engine.model.ChoiceId
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.OutcomeEffect
import com.cauldron.myriad.engine.model.Requirement
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import com.cauldron.myriad.engine.model.SkillCheck
import com.cauldron.myriad.engine.model.StoryletDef
import com.cauldron.myriad.engine.model.StoryletId
import com.cauldron.myriad.engine.model.flag
import com.cauldron.myriad.engine.persist.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StoryletTest {

    private val START = RoomId("start")
    private val SCENE = RoomId("scene")
    private val GOAL = RoomId("goal")
    private val PRIZE = ItemId("prize")
    private val RAT = MonsterId("rat")
    private val S1 = StoryletId("s1")
    private val S2 = StoryletId("s2")

    private val pack = ContentPack(
        version = "story-test/1", intro = "x", startRoom = START,
        rooms = mapOf(
            START to RoomDef(START, "Start", "A start.", exits = listOf(ExitDef("in", SCENE))),
            SCENE to RoomDef(SCENE, "Scene", "A scene.", exits = listOf(ExitDef("on", GOAL), ExitDef("back", START)), storylet = S1),
            GOAL to RoomDef(GOAL, "Goal", "A goal.", exits = listOf(ExitDef("back", SCENE)), isGoal = true),
        ),
        items = mapOf(PRIZE to ItemDef(PRIZE, "prize blade", "A test prize.", attackBonus = 5, family = "sword", tier = 3)),
        monsters = mapOf(
            RAT to MonsterDef(RAT, "rat", "A test rat.", maxHp = 8, attack = 3, defense = 1, speed = 80,
                moves = listOf(MoveDef(MoveId("bite"), "bite", "It bites.", 10, 10, 1)), goldDrop = 1..2),
        ),
        storylets = mapOf(
            S1 to StoryletDef(S1, "A door, a key, a choice.", listOf(
                ChoiceDef(ChoiceId("take"), "Take the prize", success = listOf(OutcomeEffect.GiveItem(PRIZE), OutcomeEffect.SetFlag("took", 1))),
                ChoiceDef(ChoiceId("easy"), "An easy check", check = SkillCheck(Attribute.MIGHT, 1),
                    success = listOf(OutcomeEffect.SetFlag("won", 1)), failure = listOf(OutcomeEffect.SetFlag("lost", 1))),
                ChoiceDef(ChoiceId("coin"), "A 50/50 gamble", check = SkillCheck(Attribute.MIGHT, 2),
                    success = listOf(OutcomeEffect.GainGold(10)), failure = listOf(OutcomeEffect.Hurt(3))),
                ChoiceDef(ChoiceId("gated"), "Mighty deed", requirement = Requirement.AttributeAtLeast(Attribute.MIGHT, 99),
                    success = listOf(OutcomeEffect.EndStory)),
                ChoiceDef(ChoiceId("ambush"), "Open the cursed door", success = listOf(OutcomeEffect.StartCombat(RAT))),
                ChoiceDef(ChoiceId("deeper"), "Go deeper", success = listOf(OutcomeEffect.Goto(S2))),
                ChoiceDef(ChoiceId("leave"), "Leave", success = listOf(OutcomeEffect.EndStory)),
            )),
            S2 to StoryletDef(S2, "A second scene.", listOf(
                ChoiceDef(ChoiceId("out"), "Step out", success = listOf(OutcomeEffect.EndStory)),
            )),
        ),
    )
    private val engine = Engine(pack)

    private fun inScene(seed: Long = 1): GameState {
        val s = engine.step(engine.newGame(seed, "Reader"), Action.Move(SCENE)).state
        return assertIs<Mode.Story>(s.mode).let { s }
    }

    @Test
    fun `entering a storylet room opens the scene with its body`() {
        val s = inScene()
        assertEquals(S1, (s.mode as Mode.Story).storylet)
        assertTrue(s.feed.any { it.text.contains("a key, a choice") }, "the body should narrate")
        assertEquals(1, s.flag("seen_s1"), "the seen flag is set so it won't re-fire")
    }

    @Test
    fun `available choices respect requirements`() {
        val legal = engine.legalActions(inScene())
        assertTrue(Action.Choose(ChoiceId("take")) in legal)
        assertTrue(Action.Choose(ChoiceId("gated")) !in legal, "a MIGHT-99 choice must be hidden")
        assertTrue(legal.isNotEmpty(), "story mode must always offer an action (oracle)")
    }

    @Test
    fun `taking the prize gives the item, sets a flag, and ends the scene`() {
        var s = inScene()
        s = engine.step(s, Action.Choose(ChoiceId("take"))).state
        assertIs<Mode.Exploring>(s.mode)
        assertTrue(PRIZE in s.player.inventory, "the weapon is in hand")
        assertEquals(1, s.flag("took"))
        assertEquals(SCENE, s.currentRoom, "you remain where the scene happened")
    }

    @Test
    fun `the seen scene does not re-fire on re-entry`() {
        var s = inScene()
        s = engine.step(s, Action.Choose(ChoiceId("leave"))).state // back to exploring, still in SCENE
        s = engine.step(s, Action.Move(START)).state
        s = engine.step(s, Action.Move(SCENE)).state
        assertIs<Mode.Exploring>(s.mode, "a seen storylet must not re-open")
    }

    @Test
    fun `skill check visible odds match the formula and resolve deterministically`() {
        // MIGHT for a fresh hero = base attack 2; vs difficulty 2 → 50%; ±8% per point.
        assertEquals(50, engine.checkChance(inScene(), SkillCheck(Attribute.MIGHT, 2)))
        assertEquals(58, engine.checkChance(inScene(), SkillCheck(Attribute.MIGHT, 1)))
        assertEquals(5, engine.checkChance(inScene(), SkillCheck(Attribute.MIGHT, 99)), "clamped to a 5% floor")
        for (seed in 1L..10L) {
            val a = engine.step(inScene(seed), Action.Choose(ChoiceId("coin")))
            val b = engine.step(inScene(seed), Action.Choose(ChoiceId("coin")))
            assertEquals(a.events, b.events, "seed $seed: check diverged")
        }
    }

    @Test
    fun `a high-odds check usually succeeds and picks the success branch`() {
        var anySuccess = false
        for (seed in 1L..20L) {
            // Pump MIGHT (base attack 20) so the difficulty-1 check sits at the 95% ceiling.
            var s = inScene(seed).let { it.copy(player = it.player.copy(baseAttack = 20)) }
            s = engine.step(s, Action.Choose(ChoiceId("easy"))).state
            if (s.flag("won") == 1) anySuccess = true
            assertTrue(s.flag("won") == 1 || s.flag("lost") == 1, "exactly one branch ran")
        }
        assertTrue(anySuccess, "a 95% check should succeed on most seeds")
    }

    @Test
    fun `goto chains into the next storylet`() {
        var s = inScene()
        s = engine.step(s, Action.Choose(ChoiceId("deeper"))).state
        assertEquals(S2, assertIs<Mode.Story>(s.mode).storylet)
        s = engine.step(s, Action.Choose(ChoiceId("out"))).state
        assertIs<Mode.Exploring>(s.mode)
    }

    @Test
    fun `a storylet can start a real fight with a seeded foe`() {
        var s = inScene()
        s = engine.step(s, Action.Choose(ChoiceId("ambush"))).state
        assertIs<Mode.Combat>(s.mode)
        assertEquals(8, s.rooms.getValue(SCENE).monsterHp, "the ambush foe is seeded to full HP")
    }

    @Test
    fun `flags and story mode survive a save and load`() {
        val s = inScene()
        val restored = SaveCodec.decode(SaveCodec.encode(SaveCodec.fresh(s))).state
        assertEquals(s.flags, restored.flags)
        assertEquals(s.mode, restored.mode)
        assertEquals(engine.legalActions(s), engine.legalActions(restored))
    }
}
