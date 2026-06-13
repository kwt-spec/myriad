package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Hundredfold floors are BINDING (MASTER_PLAN §1, §16.5): counted honestly —
 * distinct named entries that read differently, with stat envelopes that rise
 * by tier. These tests are the contract.
 */
class HundredfoldTest {

    private val items = EmberCellar.pack.items.values

    @Test
    fun `every weapon family clears the binding floor`() {
        val counts = items.filter { it.family.isNotEmpty() }.groupingBy { it.family }.eachCount()
        assertTrue(counts.size >= 10, "want a broad weapon roster, got ${counts.size} families")
        for ((family, n) in counts) {
            assertTrue(n >= Hundredfold.FAMILY_FLOOR, "$family: $n < floor ${Hundredfold.FAMILY_FLOOR}")
        }
        // The pack never has fewer than the generator declares (handcrafted
        // entries like the rusty sword and the capstone only ever add).
        for ((family, declared) in Hundredfold.familyCounts()) {
            assertTrue((counts[family] ?: 0) >= declared, "pack has fewer $family than the declared $declared")
        }
    }

    private fun locateDoc(name: String): java.io.File {
        // Tests may run from the module dir or the repo root; walk up to find it.
        var dir: java.io.File? = java.io.File("").absoluteFile
        while (dir != null) {
            val candidate = java.io.File(dir, "docs/$name")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        return java.io.File("docs/$name")
    }

    @Test
    fun `the documented item table matches the generated reality`() {
        val doc = locateDoc("ITEMS.md")
        assertTrue(doc.exists(), "docs/ITEMS.md must exist (searched up from ${java.io.File("").absolutePath})")
        val text = doc.readText()
        assertTrue("${Hundredfold.FAMILY_FLOOR}" in text, "doc must state the binding floor")
        for ((family, count) in Hundredfold.familyCounts()) {
            assertTrue(
                Regex("""\|\s*$family\s*\|.*\|\s*$count\s*\|""").containsMatchIn(text),
                "doc table missing or wrong row for $family ($count)",
            )
        }
        val total = Hundredfold.familyCounts().values.sum()
        assertTrue("$total" in text, "doc must state the total weapon count $total")
    }

    @Test
    fun `the documented world matches the generated reality`() {
        val doc = locateDoc("WORLD.md")
        assertTrue(doc.exists(), "docs/WORLD.md must exist")
        val text = doc.readText()
        val rooms = EmberCellar.pack.rooms.size
        for (number in listOf(Hundredfold.FLOORS, Hundredfold.generatedRoomCount(), rooms, EmberCellar.pack.monsters.size)) {
            assertTrue("$number" in text, "WORLD.md must state $number")
        }
    }

    @Test
    fun `every item is distinct in id, name, and prose`() {
        assertEquals(items.size, items.map { it.id }.toSet().size, "duplicate item ids")
        assertEquals(items.size, items.map { it.name }.toSet().size, "duplicate item names")
        assertEquals(items.size, items.map { it.description }.toSet().size, "duplicate item prose — stat reskins forbidden")
        for (item in items) {
            assertTrue(item.name.isNotBlank() && item.description.length > 20, "thin entry: ${item.id.value}")
        }
    }

    @Test
    fun `stat envelopes rise by tier and stay sane`() {
        val weapons = items.filter { it.family.isNotEmpty() }
        for (weapon in weapons) {
            assertTrue(weapon.tier in 1..5, "${weapon.id.value}: tier ${weapon.tier}")
            assertTrue(weapon.attackBonus in 0..12, "${weapon.id.value}: attack ${weapon.attackBonus}")
            assertTrue(weapon.defenseBonus in 0..4, "${weapon.id.value}: defense ${weapon.defenseBonus}")
        }
        val maxByTier = (1..5).map { tier -> weapons.filter { it.tier == tier }.maxOf { it.attackBonus } }
        for (i in 0 until 4) {
            assertTrue(maxByTier[i] <= maxByTier[i + 1], "tier ceiling must not fall: $maxByTier")
        }
        assertTrue(maxByTier[4] > maxByTier[0], "tier 5 must outclass tier 1: $maxByTier")
    }

    @Test
    fun `the depths exist, scale, and end at the First Ember`() {
        val pack = EmberCellar.pack
        assertEquals(100, Hundredfold.FLOORS, "the descent is a hundred floors deep")
        assertEquals(4 + Hundredfold.generatedRoomCount(), pack.rooms.size, "4 handcrafted + generated depths rooms")
        assertTrue(pack.rooms.size >= 300, "hundredfold rooms: only ${pack.rooms.size}")
        assertEquals(2 + Hundredfold.FLOORS, pack.monsters.size, "2 handcrafted + one den variant per floor")

        // Monster threat rises with depth.
        val hps = (1..Hundredfold.FLOORS).map { pack.monsters.getValue(Hundredfold.monsterIdAt(it)).maxHp }
        assertTrue(hps.first() < hps.last(), "depth must escalate: $hps")

        // Camps every third floor (landings) and every fifth (shrines); capstone at the bottom.
        for (depth in listOf(3, 6, 9, 99)) {
            assertTrue(pack.rooms.getValue(Hundredfold.landingId(depth)).haven, "floor $depth landing is a haven")
        }
        for (depth in listOf(5, 10, 100)) {
            val shrine = pack.rooms.getValue(Hundredfold.shrineId(depth))
            assertTrue(shrine.haven, "floor $depth shrine is a camp")
        }
        assertTrue(Hundredfold.galleryId(2) in pack.rooms, "even floors grow a gallery")
        assertTrue(Hundredfold.galleryId(3) !in pack.rooms, "odd floors have no gallery")
        assertEquals(
            Hundredfold.FIRST_EMBER,
            pack.rooms.getValue(Hundredfold.hoardId(Hundredfold.FLOORS)).hiddenItem,
            "the First Ember waits at the bottom",
        )

        // Every depth variant drops tier-true loot.
        for (depth in 1..Hundredfold.FLOORS) {
            val monster = pack.monsters.getValue(Hundredfold.monsterIdAt(depth))
            val loot = checkNotNull(monster.loot) { "depth $depth variant must carry loot" }
            assertTrue(loot.entries.isNotEmpty() && loot.entries.size <= 30)
            for (entry in loot.entries) {
                assertEquals(Hundredfold.tierForDepth(depth), pack.items.getValue(entry.item).tier, "depth $depth loot off-tier")
            }
        }
    }

    @Test
    fun `the gear loop closes - descend, fight, loot, grow stronger`() {
        val engine = Engine(EmberCellar.pack)
        var closed = false
        for (seed in 1L..12L) {
            var state = engine.newGame(seed, "Delver")
            state = engine.step(state, Action.Search).state
            state = engine.step(state, Action.Take(EmberCellar.RUSTY_SWORD)).state
            state = engine.step(state, Action.Equip(EmberCellar.RUSTY_SWORD)).state
            val baseAttack = engine.playerAttack(state)

            state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            state = fight(engine, state)
            if (state.mode != Mode.Exploring) continue

            // Rest at the hearth, then take the stair down through the vault.
            state = engine.step(state, Action.Move(EmberCellar.ASHEN_CELLAR)).state
            if (Action.Camp in engine.legalActions(state)) state = engine.step(state, Action.Camp).state
            state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            state = engine.step(state, Action.Move(EmberCellar.COLLAPSED_VAULT)).state
            state = fight(engine, state)
            if (state.mode != Mode.Exploring) continue

            state = engine.step(state, Action.Move(Hundredfold.landingId(1))).state
            state = engine.step(state, Action.Move(Hundredfold.denId(1))).state
            state = fight(engine, state)
            if (state.mode != Mode.Exploring) continue

            // Loot the den drop where it fell, then the hoard cache; wield upgrades.
            state = takeEverything(engine, state)
            state = engine.step(state, Action.Move(Hundredfold.hoardId(1))).state
            if (Action.Search in engine.legalActions(state)) state = engine.step(state, Action.Search).state
            state = takeEverything(engine, state)

            var improved = false
            var guard = 0
            while (guard < 12) {
                val upgrade = engine.legalActions(state).filterIsInstance<Action.Equip>().firstOrNull { equip ->
                    engine.playerAttack(engine.step(state, equip).state) > engine.playerAttack(state)
                } ?: break
                state = engine.step(state, upgrade).state
                improved = true
                guard++
            }
            if (improved && engine.playerAttack(state) > baseAttack) {
                closed = true
                break
            }
        }
        assertTrue(closed, "across 12 seeds, at least one delver must descend, loot, and come up stronger")
    }

    private fun takeEverything(engine: Engine, start: GameState): GameState {
        var state = start
        var guard = 0
        while (guard < 8) {
            val take = engine.legalActions(state).filterIsInstance<Action.Take>().firstOrNull() ?: break
            state = engine.step(state, take).state
            guard++
        }
        return state
    }

    private fun fight(engine: Engine, start: GameState): GameState {
        var state = start
        var rounds = 0
        while (state.mode is Mode.Combat && rounds < 80) {
            val mode = state.mode as Mode.Combat
            val monster = engine.content.monsters.getValue(mode.monster)
            val intent = engine.moveFor(monster, mode.monsterIntent)
            val legal = engine.legalActions(state)
            val action = when {
                intent.powerNum * 10 >= intent.powerDen * 13 -> Action.Brace
                Action.HeavyStrike in legal -> Action.HeavyStrike
                Action.QuickStrike in legal -> Action.QuickStrike
                else -> Action.Brace
            }
            state = engine.step(state, action).state
            rounds++
        }
        return state
    }
}
