package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.AbilityId
import com.cauldron.myriad.engine.model.ConstellationNodeDef
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ContentValidationException
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.NodeEffect
import com.cauldron.myriad.engine.model.NodeId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NodeValidationTest {

    private val start = RoomId("a")

    private fun packWith(nodes: Map<NodeId, ConstellationNodeDef>): ContentPack =
        ContentPack(
            version = "node-test/1", intro = "x", startRoom = start,
            rooms = mapOf(start to RoomDef(start, "A", "x", isGoal = true)),
            items = emptyMap(), monsters = emptyMap(),
            nodes = nodes,
        )

    private fun node(id: String, prereqs: List<NodeId> = emptyList(), cost: Int = 1, effect: NodeEffect = NodeEffect.MaxHp(1)) =
        ConstellationNodeDef(NodeId(id), id, "desc long enough", "Body", cost, prereqs, effect)

    @Test
    fun `missing prereq fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(mapOf(NodeId("x") to node("x", prereqs = listOf(NodeId("ghost")))))
        }
        assertTrue("missing prereq" in e.message!!, e.message)
    }

    @Test
    fun `prerequisite cycle fails construction`() {
        val a = NodeId("a"); val b = NodeId("b")
        val e = assertFailsWith<ContentValidationException> {
            packWith(mapOf(
                a to node("a", prereqs = listOf(b)),
                b to node("b", prereqs = listOf(a)),
            ))
        }
        assertTrue("cycle" in e.message!!, e.message)
    }

    @Test
    fun `non-positive cost fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(mapOf(NodeId("x") to node("x", cost = 0)))
        }
        assertTrue("cost" in e.message!!, e.message)
    }

    @Test
    fun `granting a missing ability fails construction`() {
        val e = assertFailsWith<ContentValidationException> {
            packWith(mapOf(NodeId("x") to node("x", effect = NodeEffect.GrantAbility(AbilityId("phantom")))))
        }
        assertTrue("missing ability" in e.message!!, e.message)
    }

    @Test
    fun `the real Body constellation validates inside the full pack`() {
        // EmberCellar.pack construction runs every node and ability linter.
        assertTrue(EmberCellar.pack.nodes.size >= 12, "Body tree should be deep")
        assertTrue(EmberCellar.pack.abilities.size >= 3, "abilities present")
        // A couple of structural sanity checks on the tree itself.
        assertTrue(Constellations.UNBROKEN in EmberCellar.pack.nodes)
        val capstonePrereqs = EmberCellar.pack.nodes.getValue(Constellations.UNBROKEN).prereqs
        assertTrue(capstonePrereqs.isNotEmpty(), "the capstone is gated")
    }
}
