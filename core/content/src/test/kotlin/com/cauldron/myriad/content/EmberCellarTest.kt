package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmberCellarTest {

    @Test
    fun `pack constructs, which means every linter passed`() {
        assertEquals("ember-cellar/0.1", EmberCellar.pack.version)
        assertEquals(EmberCellar.ASHEN_CELLAR, EmberCellar.pack.startRoom)
        assertTrue(EmberCellar.pack.rooms.getValue(EmberCellar.CELLAR_STAIR).isGoal)
    }

    @Test
    fun `the real cellar is beatable with the sword for every seed`() {
        val engine = Engine(EmberCellar.pack)
        for (seed in 1L..25L) {
            var state = engine.newGame(seed, "Wanderer")
            state = engine.step(state, Action.Search).state
            state = engine.step(state, Action.Take(EmberCellar.RUSTY_SWORD)).state
            state = engine.step(state, Action.Equip(EmberCellar.RUSTY_SWORD)).state
            state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            assertIs<Mode.Combat>(state.mode, "seed $seed")
            var rounds = 0
            while (state.mode is Mode.Combat) {
                state = engine.step(state, Action.Attack).state
                rounds++
                assertTrue(rounds <= 3, "seed $seed: cinder rat survived $rounds rounds")
            }
            assertTrue(state.player.hp > 0, "seed $seed")
            state = engine.step(state, Action.Move(EmberCellar.CELLAR_STAIR)).state
            assertIs<Mode.Victory>(state.mode, "seed $seed")
        }
    }

    @Test
    fun `prose is present where the player will look`() {
        assertTrue(EmberCellar.pack.intro.isNotBlank())
        for (room in EmberCellar.pack.rooms.values) {
            assertTrue(room.name.isNotBlank() && room.description.isNotBlank(), "room ${room.id.value}")
        }
        for (item in EmberCellar.pack.items.values) {
            assertTrue(item.name.isNotBlank(), "item ${item.id.value}")
        }
        for (monster in EmberCellar.pack.monsters.values) {
            assertTrue(monster.description.isNotBlank(), "monster ${monster.id.value}")
        }
    }
}
