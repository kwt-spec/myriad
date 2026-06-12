package com.cauldron.myriad.engine.persist

/**
 * Save-format migrations: pure functions, no RNG, no content access
 * (MASTER_PLAN §8). Every released format version must keep a loading path
 * forever, proven by the golden-save corpus in test resources.
 */
object Migrations {

    /**
     * Chain: each released version steps to the next, recursively, so a v1 save
     * walks 1→2→3→…
     *
     * v1 → v2 (M1a, tick-ATB): `Mode.Combat` gained gauge/stamina/intent/braced;
     * lenient CBOR decode fills ready-to-act defaults, the engine resolves the
     * sentinel intent to the monster's first move.
     *
     * v2 → v3 (M1b, meters): `GameState.meters` added; defaults to empty and
     * `meterFor` seeds reads at each meter's start value, so the stamp suffices.
     */
    fun migrate(save: SaveFile): SaveFile = when (save.formatVersion) {
        1 -> migrate(save.copy(formatVersion = 2))
        2 -> migrate(save.copy(formatVersion = 3))
        SaveCodec.FORMAT_VERSION -> save
        else -> throw SaveCodec.CorruptSaveException(
            "no migration path from save format ${save.formatVersion}"
        )
    }
}
