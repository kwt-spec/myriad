package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ContentValidationException
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentValidationTest {

    private val a = RoomId("a")
    private val b = RoomId("b")
    private val rat = MonsterId("rat")

    private fun move(weight: Int = 1, telegraph: String = "It moves.", num: Int = 10, den: Int = 10) =
        MoveDef(MoveId("m$weight$num$den${telegraph.length}"), "move", telegraph, num, den, weight)

    private fun monster(
        speed: Int = 100,
        moves: List<MoveDef> = listOf(move()),
        goldDrop: IntRange = 1..3,
    ) = MonsterDef(rat, "rat", "x", maxHp = 5, attack = 1, defense = 0, speed = speed, moves = moves, goldDrop = goldDrop)

    private fun packWith(monsterDef: MonsterDef? = null, rooms: Map<RoomId, RoomDef>? = null, start: RoomId = a) =
        ContentPack(
            version = "test-validate/1",
            intro = "x",
            startRoom = start,
            rooms = rooms ?: mapOf(a to RoomDef(a, "A", "x", monster = monsterDef?.id, isGoal = true)),
            items = emptyMap(),
            monsters = monsterDef?.let { mapOf(it.id to it) } ?: emptyMap(),
        )

    @Test
    fun `dangling exit fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(rooms = mapOf(a to RoomDef(a, "A", "x", exits = listOf(ExitDef("void", RoomId("nowhere"))), isGoal = true)))
        }
        assertTrue("missing room" in e.message!!, e.message)
    }

    @Test
    fun `unreachable room fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(rooms = mapOf(a to RoomDef(a, "A", "x", isGoal = true), b to RoomDef(b, "B", "x")))
        }
        assertTrue("unreachable" in e.message!!, e.message)
    }

    @Test
    fun `missing goal fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(rooms = mapOf(a to RoomDef(a, "A", "x")))
        }
        assertTrue("goal" in e.message!!, e.message)
    }

    @Test
    fun `missing monster reference fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(rooms = mapOf(a to RoomDef(a, "A", "x", monster = MonsterId("ghost"), isGoal = true)))
        }
        assertTrue("missing monster" in e.message!!, e.message)
    }

    @Test
    fun `gold drop beyond the cap fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(goldDrop = 0..2_000_000))
        }
        assertTrue("cap" in e.message!!, e.message)
    }

    @Test
    fun `monster without moves fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(moves = emptyList()))
        }
        assertTrue("at least one move" in e.message!!, e.message)
    }

    @Test
    fun `zero or negative speed fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(speed = 0))
        }
        assertTrue("speed" in e.message!!, e.message)
    }

    @Test
    fun `bad move definitions fail construction`() {
        val weightless = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(moves = listOf(move(weight = 0))))
        }
        assertTrue("weight" in weightless.message!!, weightless.message)

        val blankTelegraph = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(moves = listOf(move(telegraph = "  "))))
        }
        assertTrue("telegraph" in blankTelegraph.message!!, blankTelegraph.message)

        val badPower = assertFailsWith<ContentValidationException> {
            packWith(monsterDef = monster(moves = listOf(move(num = 0))))
        }
        assertTrue("power" in badPower.message!!, badPower.message)
    }

    @Test
    fun `all problems are reported at once, not one per attempt`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/many", intro = "x", startRoom = RoomId("missing-start"),
                rooms = mapOf(a to RoomDef(a, "A", "x", exits = listOf(ExitDef("void", RoomId("nowhere"))))),
                items = emptyMap(), monsters = emptyMap(),
            )
        }
        val message = e.message!!
        assertTrue("startRoom" in message, message)
        assertTrue("missing room" in message, message)
        assertTrue("goal" in message, message)
    }

    @Test
    fun `the test worlds themselves validate`() {
        TestWorlds.cellarLike()
        TestWorlds.fastFoe()
        TestWorlds.brutal()
        TestWorlds.monsterOnGoal()
    }
}
