package com.cauldron.myriad.engine.persist

/**
 * Save-format migrations: pure functions, no RNG, no content access
 * (MASTER_PLAN §8). Every released format version must keep a loading path
 * forever, proven by the golden-save corpus in test resources.
 */
object Migrations {

    /**
     * v1 → v2 (M1a, tick-ATB combat): `Mode.Combat` gained gauge/stamina/
     * intent/braced fields. The lenient CBOR decode already fills them with
     * ready-to-act defaults (player gauge full, monster gauge empty, full
     * stamina, unbraced) and a sentinel intent the engine resolves to the
     * monster's first move. Nothing else changed shape, so the migration is
     * just the version stamp.
     */
    fun migrate(save: SaveFile): SaveFile = when (save.formatVersion) {
        1 -> save.copy(formatVersion = 2)
        SaveCodec.FORMAT_VERSION -> save
        else -> throw SaveCodec.CorruptSaveException(
            "no migration path from save format ${save.formatVersion}"
        )
    }
}
