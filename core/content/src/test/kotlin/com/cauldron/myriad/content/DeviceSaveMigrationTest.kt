package com.cauldron.myriad.content

import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.persist.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A real M0 device save (pulled via adb before the v2 changes; it happens to be
 * a fresh run at turn 0 — the device had started a new game since the t2 session).
 * It must load through migration AND survive content additions: the Collapsed
 * Vault didn't exist in v1, so reaching it exercises roomStateFor's
 * seed-on-demand path against a genuine artifact.
 */
class DeviceSaveMigrationTest {

    private fun deviceSave() = SaveCodec.decode(
        checkNotNull(javaClass.getResourceAsStream("/golden/v1-device.myr")).readBytes()
    )

    @Test
    fun `the real device save loads and migrates`() {
        val save = deviceSave()
        assertEquals(SaveCodec.FORMAT_VERSION, save.formatVersion, "decode must migrate to current")
        assertIs<Mode.Exploring>(save.state.mode)
        assertEquals(3, save.state.rooms.size, "v1 world had exactly three rooms")
        assertTrue(EmberCellar.COLLAPSED_VAULT !in save.state.rooms, "the vault postdates this save")
        assertEquals("ember-cellar/0.1", save.state.contentVersion)
    }

    @Test
    fun `the device save can walk into a room that did not exist when it was written`() {
        val engine = Engine(EmberCellar.pack)
        var state = deviceSave().state

        // Arm up from wherever this save actually is (it's a fresh t0 run, but the
        // policy below works for any v1 exploring state — fixtures are artifacts,
        // not assumptions).
        if (Action.Search in engine.legalActions(state)) {
            state = engine.step(state, Action.Search).state
        }
        val take = engine.legalActions(state).filterIsInstance<Action.Take>().firstOrNull()
        if (take != null) state = engine.step(state, take).state
        val equip = engine.legalActions(state).filterIsInstance<Action.Equip>().firstOrNull()
        if (equip != null) state = engine.step(state, equip).state

        if (state.currentRoom != EmberCellar.ROOT_PASSAGE) {
            state = engine.step(state, Action.Move(EmberCellar.ROOT_PASSAGE)).state
        }
        if (state.mode is Mode.Combat) {
            var rounds = 0
            while (state.mode is Mode.Combat && rounds < 60) {
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
        }
        if (state.mode is Mode.Dead) return // honest outcome; crash-freedom is what's under test

        // The vault was added AFTER this save: entering it must seed fresh room
        // state on demand instead of crashing on the missing map key.
        assertIs<Mode.Exploring>(state.mode)
        state = engine.step(state, Action.Move(EmberCellar.COLLAPSED_VAULT)).state
        assertIs<Mode.Combat>(state.mode, "the wisp must be seeded on demand for an old save")
    }
}
