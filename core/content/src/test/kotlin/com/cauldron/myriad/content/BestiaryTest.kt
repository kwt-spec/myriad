package com.cauldron.myriad.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bestiary is BINDING (MASTER_PLAN §1: 1,000+ monsters on the lifetime track).
 * Counted honestly — distinct id, name, and prose per species, with stat envelopes
 * that rise by tier. These tests are the contract.
 */
class BestiaryTest {

    private val species = Bestiary.all(EmberCellar.pack.items)

    @Test
    fun `the bestiary clears its binding floor`() {
        assertTrue(
            species.size >= Bestiary.SPECIES_FLOOR,
            "bestiary: ${species.size} < floor ${Bestiary.SPECIES_FLOOR}",
        )
        assertEquals(species.size, Bestiary.speciesCount(), "declared count disagrees with generated")
    }

    @Test
    fun `every species is distinct in id, name, and prose`() {
        val defs = species.values
        assertEquals(defs.size, defs.map { it.id }.toSet().size, "duplicate monster ids")
        assertEquals(defs.size, defs.map { it.name }.toSet().size, "duplicate monster names")
        assertEquals(defs.size, defs.map { it.description }.toSet().size, "duplicate prose — reskins forbidden")
        for (def in defs) {
            assertTrue(def.name.isNotBlank() && def.description.length > 20, "thin entry: ${def.id.value}")
        }
    }

    @Test
    fun `every species is mechanically valid and tier-scaled`() {
        for (def in species.values) {
            assertTrue(def.maxHp >= 1, "${def.id.value}: hp ${def.maxHp}")
            assertTrue(def.attack >= 1, "${def.id.value}: attack ${def.attack}")
            assertTrue(def.speed in 1..1000, "${def.id.value}: speed ${def.speed}")
            assertTrue(def.moves.isNotEmpty(), "${def.id.value}: no moves")
            for (move in def.moves) assertTrue(move.telegraph.isNotBlank(), "${def.id.value}: blank telegraph")
            val loot = checkNotNull(def.loot) { "${def.id.value}: no loot" }
            assertTrue(loot.entries.isNotEmpty(), "${def.id.value}: empty loot")
        }
    }

    @Test
    fun `the same archetype grows monstrously from tier 1 to tier 10`() {
        // A lesser rat versus an apex rat of the same condition.
        val lesser = species.getValue(com.cauldron.myriad.engine.model.MonsterId("best_rat_ashen_t1"))
        val apex = species.getValue(com.cauldron.myriad.engine.model.MonsterId("best_rat_ashen_t10"))
        assertTrue(apex.maxHp > lesser.maxHp * 8, "apex hp ${apex.maxHp} should dwarf lesser ${lesser.maxHp}")
        assertTrue(apex.attack > lesser.attack, "apex must hit harder")
    }

    @Test
    fun `the whole pack validates with the bestiary placed in the depths`() {
        // Pack construction runs every Forge linter; reaching here means 800+
        // monsters, their moves, their loot refs, and reachability all passed.
        assertTrue(EmberCellar.pack.monsters.size >= 800, "pack monsters: ${EmberCellar.pack.monsters.size}")
    }

    private fun locateDoc(name: String): java.io.File {
        var dir: java.io.File? = java.io.File("").absoluteFile
        while (dir != null) {
            val candidate = java.io.File(dir, "docs/$name")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return java.io.File("docs/$name")
    }

    @Test
    fun `the documented bestiary matches the generated reality`() {
        val doc = locateDoc("BESTIARY.md")
        assertTrue(doc.exists(), "docs/BESTIARY.md must exist")
        val text = doc.readText()
        for (number in listOf(Bestiary.SPECIES_FLOOR, Bestiary.speciesCount())) {
            assertTrue("$number" in text, "BESTIARY.md must state $number")
        }
    }
}
