package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.persist.SaveCodec
import com.cauldron.myriad.engine.persist.SaveFile
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SaveCodecCorruptionTest {

    private val engine = Engine(TestWorlds.cellarLike())
    private val bytes: ByteArray = SaveCodec.encode(SaveCodec.fresh(engine.newGame(13, "Hero")))

    @Test
    fun `single bit flips anywhere are detected`() {
        // Magic, checksum, and payload regions each get hit.
        val offsets = listOf(0, 3, 4, 20, 35, 36, 50, bytes.size / 2, bytes.size - 1)
        for (offset in offsets) {
            val corrupted = bytes.copyOf()
            corrupted[offset] = (corrupted[offset].toInt() xor 0x01).toByte()
            assertFailsWith<SaveCodec.CorruptSaveException>("flip at $offset went undetected") {
                SaveCodec.decode(corrupted)
            }
        }
    }

    @Test
    fun `truncation is detected`() {
        assertFailsWith<SaveCodec.CorruptSaveException> { SaveCodec.decode(bytes.copyOfRange(0, 10)) }
        assertFailsWith<SaveCodec.CorruptSaveException> { SaveCodec.decode(bytes.copyOfRange(0, bytes.size - 5)) }
        assertFailsWith<SaveCodec.CorruptSaveException> { SaveCodec.decode(ByteArray(0)) }
    }

    @Test
    fun `garbage and wrong magic are rejected`() {
        assertFailsWith<SaveCodec.CorruptSaveException> { SaveCodec.decode(ByteArray(200) { 0x5A }) }
    }

    @Test
    fun `saves from a future format version are refused, not misread`() {
        val future = SaveFile(
            formatVersion = SaveCodec.FORMAT_VERSION + 1,
            engineVersion = "9.9.9",
            state = engine.newGame(1, "Traveler"),
        )
        val encoded = SaveCodec.encode(future)
        assertFailsWith<SaveCodec.CorruptSaveException> { SaveCodec.decode(encoded) }
    }
}
