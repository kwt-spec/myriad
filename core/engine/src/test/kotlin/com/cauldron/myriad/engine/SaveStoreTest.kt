package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.persist.SaveCodec
import com.cauldron.myriad.engine.persist.SaveStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveStoreTest {

    private val engine = Engine(TestWorlds.cellarLike())

    private fun withTempDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("myriad-savestore-test")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun statesByTurn(count: Int): List<com.cauldron.myriad.engine.model.GameState> {
        var state = engine.newGame(1, "Hero")
        val states = mutableListOf(state)
        repeat(count - 1) {
            state = engine.step(state, Action.Look).state
            states += state
        }
        return states
    }

    @Test
    fun `writes rotate a backup chain, newest first`() {
        withTempDir { dir ->
            val store = SaveStore(dir)
            val states = statesByTurn(5)
            for (s in states) store.write(SaveCodec.fresh(s))

            val loaded = assertNotNull(store.load())
            assertEquals(0, loaded.usedBackupIndex)
            assertEquals(4L, loaded.save.state.turn, "primary must be the newest save")

            assertTrue(Files.exists(dir.resolve("save.myr")))
            assertTrue(Files.exists(dir.resolve("save.bak1.myr")))
            assertTrue(Files.exists(dir.resolve("save.bak2.myr")))
            assertTrue(Files.exists(dir.resolve("save.bak3.myr")))
            assertTrue(!Files.exists(dir.resolve("save.tmp")), "tmp must not linger")
        }
    }

    @Test
    fun `corrupt primary falls back to the freshest intact backup`() {
        withTempDir { dir ->
            val store = SaveStore(dir)
            for (s in statesByTurn(3)) store.write(SaveCodec.fresh(s))

            dir.resolve("save.myr").writeBytes(ByteArray(64) { 0x00 })

            val loaded = assertNotNull(store.load())
            assertEquals(1, loaded.usedBackupIndex, "should report recovery from bak1")
            assertEquals(1L, loaded.save.state.turn, "bak1 holds the second-newest save")
        }
    }

    @Test
    fun `all files corrupt loads nothing rather than garbage`() {
        withTempDir { dir ->
            val store = SaveStore(dir)
            for (s in statesByTurn(5)) store.write(SaveCodec.fresh(s))
            for (name in listOf("save.myr", "save.bak1.myr", "save.bak2.myr", "save.bak3.myr")) {
                dir.resolve(name).writeBytes(ByteArray(16) { 0x42 })
            }
            assertNull(store.load())
            assertTrue(!store.hasLoadableSave())
        }
    }

    @Test
    fun `writing after corruption recovers cleanly`() {
        withTempDir { dir ->
            val store = SaveStore(dir)
            store.write(SaveCodec.fresh(engine.newGame(1, "Hero")))
            dir.resolve("save.myr").writeBytes(ByteArray(8) { 0x00 })

            val fresh = engine.newGame(2, "Hero")
            store.write(SaveCodec.fresh(fresh))
            val loaded = assertNotNull(store.load())
            assertEquals(0, loaded.usedBackupIndex)
            assertEquals(fresh, loaded.save.state)
        }
    }

    @Test
    fun `empty directory has no save`() {
        withTempDir { dir ->
            assertNull(SaveStore(dir.resolve("does-not-exist-yet")).load())
        }
    }
}
