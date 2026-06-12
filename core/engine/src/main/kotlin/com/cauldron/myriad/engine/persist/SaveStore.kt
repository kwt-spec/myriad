package com.cauldron.myriad.engine.persist

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Atomic save persistence with a rotating backup chain (MASTER_PLAN §8):
 * write temp → fsync → atomic rename, rotating primary → bak1 → bak2 → bak3
 * first. A kill at any instant leaves at least one intact, checksummed file.
 */
class SaveStore(private val dir: Path, private val backups: Int = 3) {

    data class Loaded(val save: SaveFile, val usedBackupIndex: Int)

    private fun fileAt(index: Int): Path =
        dir.resolve(if (index == 0) "save.myr" else "save.bak$index.myr")

    private val tmp: Path get() = dir.resolve("save.tmp")

    @Synchronized
    fun write(save: SaveFile) {
        Files.createDirectories(dir)
        val bytes = SaveCodec.encode(save)
        FileChannel.open(
            tmp,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        ).use { channel ->
            val buf = ByteBuffer.wrap(bytes)
            while (buf.hasRemaining()) channel.write(buf)
            channel.force(true)
        }
        // Rotate the chain from the back so nothing is overwritten before it's copied forward.
        for (index in backups - 1 downTo 0) {
            val from = fileAt(index)
            if (Files.exists(from)) {
                moveReplacing(from, fileAt(index + 1))
            }
        }
        moveReplacing(tmp, fileAt(0))
        fsyncDirBestEffort()
    }

    /** Walks primary then backups, skipping anything corrupt or unreadable. */
    @Synchronized
    fun load(): Loaded? {
        for (index in 0..backups) {
            val path = fileAt(index)
            if (!Files.exists(path)) continue
            try {
                return Loaded(SaveCodec.decode(Files.readAllBytes(path)), index)
            } catch (_: Exception) {
                // Corrupt or unreadable — keep walking the chain.
            }
        }
        return null
    }

    fun hasLoadableSave(): Boolean = load() != null

    @Synchronized
    fun wipe() {
        for (index in 0..backups) Files.deleteIfExists(fileAt(index))
        Files.deleteIfExists(tmp)
    }

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun fsyncDirBestEffort() {
        try {
            FileChannel.open(dir, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // Directory fsync is unsupported on some filesystems; the data file itself is synced.
        }
    }
}
