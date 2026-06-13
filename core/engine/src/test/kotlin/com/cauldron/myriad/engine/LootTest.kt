package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.LootEntry
import com.cauldron.myriad.engine.model.LootTable
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LootTest {

    private val PRIZE = ItemId("prize_blade")

    private fun lootEngine(chance: Int = 100) = Engine(
        TestWorlds.cellarLike(
            ratLoot = LootTable(chance, listOf(LootEntry(PRIZE, 1))),
            extraItems = mapOf(
                PRIZE to ItemDef(PRIZE, "prize blade", "A test prize.", attackBonus = 5, family = "sword", tier = 3)
            ),
        )
    )

    private fun killRatArmed(engine: Engine, seed: Long): StepResult {
        var state = engine.newGame(seed, "Looter")
        state = engine.step(state, Action.Search).state
        state = engine.step(state, Action.Take(TestWorlds.SWORD)).state
        state = engine.step(state, Action.Equip(TestWorlds.SWORD)).state
        state = engine.step(state, Action.Move(TestWorlds.PASSAGE)).state
        var last: StepResult? = null
        var guard = 0
        while (state.mode is Mode.Combat && guard < 30) {
            last = engine.step(state, engine.legalActions(state).first())
            state = last.state
            guard++
        }
        return checkNotNull(last)
    }

    @Test
    fun `guaranteed loot drops to the floor and can be taken and worn`() {
        val engine = lootEngine(chance = 100)
        val kill = killRatArmed(engine, seed = 5)
        assertTrue(kill.events.any { it is Event.MonsterSlain })
        val drop = kill.events.filterIsInstance<Event.ItemDropped>().single()
        assertEquals(PRIZE, drop.item)

        var state = kill.state
        assertIs<Mode.Exploring>(state.mode)
        assertTrue(Action.Take(PRIZE) in engine.legalActions(state), "drop must land on the floor")
        state = engine.step(state, Action.Take(PRIZE)).state
        state = engine.step(state, Action.Equip(PRIZE)).state
        assertEquals(7, engine.playerAttack(state), "prize blade equips: base 2 + 5")
    }

    @Test
    fun `loot rolls are deterministic per seed`() {
        val a = killRatArmed(lootEngine(chance = 40), seed = 11)
        val b = killRatArmed(lootEngine(chance = 40), seed = 11)
        assertEquals(a.events, b.events, "same seed, same drop outcome")
        assertEquals(a.state, b.state)
    }

    @Test
    fun `lootless monsters drop nothing, as ever`() {
        val engine = Engine(TestWorlds.cellarLike())
        val kill = killRatArmed(engine, seed = 5)
        assertTrue(kill.events.none { it is Event.ItemDropped })
    }
}
