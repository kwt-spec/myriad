package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ContentValidationException
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentValidationTest {

    private val a = RoomId("a")
    private val b = RoomId("b")

    @Test
    fun `dangling exit fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/1", intro = "x", startRoom = a,
                rooms = mapOf(
                    a to RoomDef(a, "A", "x", exits = listOf(ExitDef("void", RoomId("nowhere"))), isGoal = true),
                ),
                items = emptyMap(), monsters = emptyMap(),
            )
        }
        assertTrue("missing room" in e.message!!, e.message)
    }

    @Test
    fun `unreachable room fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/2", intro = "x", startRoom = a,
                rooms = mapOf(
                    a to RoomDef(a, "A", "x", isGoal = true),
                    b to RoomDef(b, "B", "x"),
                ),
                items = emptyMap(), monsters = emptyMap(),
            )
        }
        assertTrue("unreachable" in e.message!!, e.message)
    }

    @Test
    fun `missing goal fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/3", intro = "x", startRoom = a,
                rooms = mapOf(a to RoomDef(a, "A", "x")),
                items = emptyMap(), monsters = emptyMap(),
            )
        }
        assertTrue("goal" in e.message!!, e.message)
    }

    @Test
    fun `missing monster reference fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/4", intro = "x", startRoom = a,
                rooms = mapOf(a to RoomDef(a, "A", "x", monster = MonsterId("ghost"), isGoal = true)),
                items = emptyMap(), monsters = emptyMap(),
            )
        }
        assertTrue("missing monster" in e.message!!, e.message)
    }

    @Test
    fun `gold drop beyond the cap fails construction`() {
        val rat = MonsterId("rat")
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/5", intro = "x", startRoom = a,
                rooms = mapOf(a to RoomDef(a, "A", "x", monster = rat, isGoal = true)),
                items = emptyMap(),
                monsters = mapOf(rat to MonsterDef(rat, "rat", "x", maxHp = 1, attack = 1, defense = 0, goldDrop = 0..2_000_000)),
            )
        }
        assertTrue("cap" in e.message!!, e.message)
    }

    @Test
    fun `all problems are reported at once, not one per attempt`() {
        val e = assertFailsWith<ContentValidationException> {
            ContentPack(
                version = "bad/6", intro = "x", startRoom = RoomId("missing-start"),
                rooms = mapOf(
                    a to RoomDef(a, "A", "x", exits = listOf(ExitDef("void", RoomId("nowhere")))),
                ),
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
        TestWorlds.brutal()
        TestWorlds.monsterOnGoal()
    }
}
