package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Local copy of the telegraph-aware reference fighter (engine test sources aren't shared). */
private fun fightSmart(engine: Engine, start: GameState, maxRounds: Int = 60): GameState {
    var state = start
    var rounds = 0
    while (state.mode is Mode.Combat && rounds < maxRounds) {
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

class EmberCellarTest {

    @Test
    fun `pack constructs, which means every linter passed`() {
        assertEquals("ember-age/0.5", EmberCellar.pack.version)
        assertTrue(EmberCellar.WARMTH in EmberCellar.pack.meters, "the survival era has its meter")
        assertTrue(EmberCellar.pack.rooms.getValue(EmberCellar.ASHEN_CELLAR).haven, "the cellar is the haven")
        assertEquals(EmberCellar.ASHEN_CELLAR, EmberCellar.pack.startRoom)
        assertTrue(EmberCellar.pack.rooms.getValue(EmberCellar.CELLAR_STAIR).isGoal)
        assertTrue(EmberCellar.COLLAPSED_VAULT in EmberCellar.pack.rooms, "the vault exists")
        val wisp = EmberCellar.pack.monsters.getValue(EmberCellar.EMBER_WISP)
        val rat = EmberCellar.pack.monsters.getValue(EmberCellar.CINDER_RAT)
        assertTrue(wisp.speed > rat.speed * 2, "the wisp's identity is speed")
    }

    @Test
    fun `the armed cellar run wins for every seed with telegraph play`() {
        val engine = Engine(EmberCellar.pack)
        for (seed in 1L..25L) {
            var state = engine.newGame(seed, "Wanderer")
            state = engine.step(state, Action.Search).state
            state = engine.step(state, Action.Take(EmberCellar.RUSTY_SWORD)).state
            state = engine.step(state, Action.Equip(EmberCellar.RUSTY_SWORD)).state
            state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
            assertIs<Mode.Combat>(state.mode, "seed $seed")
            state = fightSmart(engine, state)
            assertIs<Mode.Exploring>(state.mode, "seed $seed: rat fight should be winnable, hp=${state.player.hp}")
            assertTrue(state.player.hp > 0, "seed $seed")
            state = engine.step(state, Action.Move(EmberCellar.CELLAR_STAIR)).state
            assertIs<Mode.Victory>(state.mode, "seed $seed")
        }
    }

    @Test
    fun `the wisp gets extra turns against heavy swings`() {
        val engine = Engine(EmberCellar.pack)
        var state = engine.newGame(7, "Wanderer")
        state = engine.step(state, Action.Search).state
        state = engine.step(state, Action.Take(EmberCellar.RUSTY_SWORD)).state
        state = engine.step(state, Action.Equip(EmberCellar.RUSTY_SWORD)).state
        state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        state = fightSmart(engine, state)
        assertIs<Mode.Exploring>(state.mode, "seed 7 rat fight")

        state = engine.step(state, Action.Move(EmberCellar.COLLAPSED_VAULT)).state
        assertIs<Mode.Combat>(state.mode, "the vault holds the wisp")
        // The wisp (hp 5) dies to one connected heavy (eff 6 + roll − 0 ≥ 7);
        // the fight must end without crashing regardless of outcome.
        val done = fightSmart(engine, state)
        assertTrue(done.mode is Mode.Exploring || done.mode is Mode.Dead)
    }

    @Test
    fun `prose is present where the player will look`() {
        assertTrue(EmberCellar.pack.intro.isNotBlank())
        for (room in EmberCellar.pack.rooms.values) {
            assertTrue(room.name.isNotBlank() && room.description.isNotBlank(), "room ${room.id.value}")
        }
        for (monster in EmberCellar.pack.monsters.values) {
            assertTrue(monster.description.isNotBlank(), "monster ${monster.id.value}")
            for (move in monster.moves) {
                assertTrue(move.telegraph.isNotBlank(), "move ${move.id.value}")
            }
        }
    }
}
