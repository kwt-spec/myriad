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
     *
     * v3 → v4 (M1c, progression): `PlayerState` gained xp/level/skillPoints/
     * unlockedNodes/mastery and `Mode.Combat` gained abilityCooldowns; all have
     * defaults (level 1, no xp, no nodes), so the lenient decode fills them and
     * the stamp suffices.
     *
     * v4 → v5 (status effects): `Mode.Combat` gained `statuses`; defaults to empty,
     * so the stamp suffices.
     *
     * v5 → v6 (storylets): `GameState` gained `flags` and a `Mode.Story`; flags
     * default to empty, so the stamp suffices.
     */
    fun migrate(save: SaveFile): SaveFile = when (save.formatVersion) {
        1 -> migrate(save.copy(formatVersion = 2))
        2 -> migrate(save.copy(formatVersion = 3))
        3 -> migrate(save.copy(formatVersion = 4))
        4 -> migrate(save.copy(formatVersion = 5))
        5 -> migrate(save.copy(formatVersion = 6))
        SaveCodec.FORMAT_VERSION -> save
        else -> throw SaveCodec.CorruptSaveException(
            "no migration path from save format ${save.formatVersion}"
        )
    }
}
