package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId

/**
 * M0 micro-dungeon: three rooms, one hidden weapon, one fight, one way out.
 * Exists to exercise every engine system end-to-end, not to be big.
 */
object EmberCellar {
    val ASHEN_CELLAR = RoomId("ashen_cellar")
    val ROOT_PASSAGE = RoomId("root_passage")
    val CELLAR_STAIR = RoomId("cellar_stair")

    val RUSTY_SWORD = ItemId("rusty_sword")
    val CINDER_RAT = MonsterId("cinder_rat")

    val pack = ContentPack(
        version = "ember-cellar/0.1",
        intro = "Cold ash sifts down from the beams overhead. You wake on cracked flagstones " +
            "with no memory of the stairs that brought you down — only a seam of dim ember-light " +
            "somewhere above, and the certainty that the dark below is not empty.",
        startRoom = ASHEN_CELLAR,
        rooms = mapOf(
            ASHEN_CELLAR to RoomDef(
                id = ASHEN_CELLAR,
                name = "Ashen Cellar",
                description = "A vaulted cellar half-buried in grey drifts. Broken casks line the " +
                    "walls, and something glints beneath the ash.",
                exits = listOf(ExitDef("North — a root-choked archway", ROOT_PASSAGE)),
                hiddenItem = RUSTY_SWORD,
                searchText = "You drag your fingers through the drifts and strike metal: " +
                    "a rusty sword, pitted but still keen enough to bite.",
            ),
            ROOT_PASSAGE to RoomDef(
                id = ROOT_PASSAGE,
                name = "Root-Choked Passage",
                description = "Pale roots hang from the ceiling like a curtain of fingers. " +
                    "The air is warmer here, and it smells of singed fur.",
                exits = listOf(
                    ExitDef("South — back to the cellar", ASHEN_CELLAR),
                    ExitDef("North — a stair toward ember-light", CELLAR_STAIR),
                ),
                monster = CINDER_RAT,
            ),
            CELLAR_STAIR to RoomDef(
                id = CELLAR_STAIR,
                name = "Cellar Stair",
                description = "A narrow stone stair climbs toward a seam of warm orange light.",
                exits = listOf(ExitDef("South — down to the passage", ROOT_PASSAGE)),
                isGoal = true,
            ),
        ),
        items = mapOf(
            RUSTY_SWORD to ItemDef(
                id = RUSTY_SWORD,
                name = "rusty sword",
                description = "Pitted iron, older than the cellar. Still bites.",
                attackBonus = 2,
            ),
        ),
        monsters = mapOf(
            CINDER_RAT to MonsterDef(
                id = CINDER_RAT,
                name = "cinder rat",
                description = "A rat the size of a hound noses out of the roots, " +
                    "coals glowing somewhere under its skin.",
                maxHp = 8,
                attack = 3,
                defense = 1,
                goldDrop = 2..6,
            ),
        ),
    )
}
