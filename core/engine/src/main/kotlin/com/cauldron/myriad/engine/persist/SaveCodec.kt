package com.cauldron.myriad.engine.persist

import com.cauldron.myriad.engine.model.GameState
import java.security.MessageDigest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor

@Serializable
data class SaveFile(
    val formatVersion: Int,
    val engineVersion: String,
    val state: GameState,
)

/**
 * Save bytes = MAGIC ("MYR1") + SHA-256(payload) + CBOR payload.
 * Decode verifies magic, checksum, and format version before touching the
 * payload, so a torn or tampered file fails loudly and the SaveStore can
 * walk the backup chain (MASTER_PLAN §8).
 */
object SaveCodec {
    const val FORMAT_VERSION = 6
    const val ENGINE_VERSION = "0.6.0"

    private val MAGIC = byteArrayOf(0x4D, 0x59, 0x52, 0x31) // "MYR1"
    private const val SHA_LEN = 32

    class CorruptSaveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun fresh(state: GameState): SaveFile = SaveFile(FORMAT_VERSION, ENGINE_VERSION, state)

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(save: SaveFile): ByteArray {
        val payload = Cbor.encodeToByteArray(SaveFile.serializer(), save)
        return MAGIC + sha256(payload) + payload
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(bytes: ByteArray): SaveFile {
        if (bytes.size < MAGIC.size + SHA_LEN + 1) {
            throw CorruptSaveException("save too short: ${bytes.size} bytes")
        }
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptSaveException("bad magic header")
        }
        val expected = bytes.copyOfRange(MAGIC.size, MAGIC.size + SHA_LEN)
        val payload = bytes.copyOfRange(MAGIC.size + SHA_LEN, bytes.size)
        if (!sha256(payload).contentEquals(expected)) {
            throw CorruptSaveException("checksum mismatch")
        }
        val save = try {
            Cbor.decodeFromByteArray(SaveFile.serializer(), payload)
        } catch (e: Exception) {
            throw CorruptSaveException("payload undecodable: ${e.message}", e)
        }
        if (save.formatVersion > FORMAT_VERSION) {
            throw CorruptSaveException(
                "save format ${save.formatVersion} is newer than supported $FORMAT_VERSION"
            )
        }
        return Migrations.migrate(save)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
