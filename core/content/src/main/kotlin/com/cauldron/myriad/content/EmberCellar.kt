package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.LootEntry
import com.cauldron.myriad.engine.model.LootTable
import com.cauldron.myriad.engine.model.MeterDef
import com.cauldron.myriad.engine.model.MeterId
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId

/**
 * M1a micro-dungeon: four rooms, one hidden weapon, two fights, one way out.
 * The cinder rat teaches telegraph-reading; the ember wisp teaches speed
 * (it acts 2.4× per player tick — heavy swings give it extra turns).
 */
object EmberCellar {
    val ASHEN_CELLAR = RoomId("ashen_cellar")
    val ROOT_PASSAGE = RoomId("root_passage")
    val COLLAPSED_VAULT = RoomId("collapsed_vault")
    val CELLAR_STAIR = RoomId("cellar_stair")

    val RUSTY_SWORD = ItemId("rusty_sword")

    val CINDER_RAT = MonsterId("cinder_rat")
    val EMBER_WISP = MonsterId("ember_wisp")

    val RAT_LUNGE = MoveId("rat_lunge")
    val RAT_GNAW = MoveId("rat_gnaw")
    val RAT_EMBER_BURST = MoveId("rat_ember_burst")
    val WISP_FLICKER = MoveId("wisp_flicker")
    val WISP_SCORCH = MoveId("wisp_scorch")

    val WARMTH = MeterId("warmth")

    /** The Hundredfold expansion: generated weapon families + the capstone. */
    private val hundredfoldItems: Map<ItemId, ItemDef> =
        Hundredfold.weapons() + (Hundredfold.FIRST_EMBER to Hundredfold.capstone())

    /** The Constellation Forge + Great Forge: generated progression (declared before pack). */
    private val forgedAbilities = ConstellationForge.abilities()
    private val greatAbilities = GreatForge.abilities()
    private val greatSenses = GreatForge.senses()

    val pack = ContentPack(
        version = "ember-age/0.4",
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
                haven = true,
                campText = "You bank the loose embers among the broken casks and huddle close. " +
                    "Warmth seeps back into your hands, and the dark feels survivable again.",
            ),
            ROOT_PASSAGE to RoomDef(
                id = ROOT_PASSAGE,
                name = "Root-Choked Passage",
                description = "Pale roots hang from the ceiling like a curtain of fingers. " +
                    "The air is warmer here, and it smells of singed fur.",
                exits = listOf(
                    ExitDef("South — back to the cellar", ASHEN_CELLAR),
                    ExitDef("East — a collapsed vault, breathing faint light", COLLAPSED_VAULT),
                    ExitDef("North — a stair toward ember-light", CELLAR_STAIR),
                ),
                monster = CINDER_RAT,
            ),
            COLLAPSED_VAULT to RoomDef(
                id = COLLAPSED_VAULT,
                name = "Collapsed Vault",
                description = "Half the ceiling has come down in a frozen wave of brick. " +
                    "Between the fallen stones, a point of light weaves and bobs — too quick, " +
                    "too deliberate to be a stray spark. Behind the rubble, a cracked stair " +
                    "descends into a heat you can feel on your face.",
                exits = listOf(
                    ExitDef("West — back to the passage", ROOT_PASSAGE),
                    ExitDef("Down — the cracked stair into the Ember Depths", Hundredfold.landingId(1)),
                ),
                monster = EMBER_WISP,
            ),
            CELLAR_STAIR to RoomDef(
                id = CELLAR_STAIR,
                name = "Cellar Stair",
                description = "A narrow stone stair climbs toward a seam of warm orange light.",
                exits = listOf(ExitDef("South — down to the passage", ROOT_PASSAGE)),
                isGoal = true,
            ),
        ) + Hundredfold.depthsRooms(COLLAPSED_VAULT, hundredfoldItems),
        items = mapOf(
            RUSTY_SWORD to ItemDef(
                id = RUSTY_SWORD,
                name = "rusty sword",
                description = "Pitted iron, older than the cellar. Still bites.",
                attackBonus = 2,
                family = "sword",
                tier = 1,
            ),
        ) + hundredfoldItems,
        monsters = mapOf(
            CINDER_RAT to MonsterDef(
                id = CINDER_RAT,
                name = "cinder rat",
                description = "A rat the size of a hound noses out of the roots, " +
                    "coals glowing somewhere under its skin.",
                maxHp = 8,
                attack = 3,
                defense = 1,
                speed = 80,
                moves = listOf(
                    MoveDef(
                        id = RAT_LUNGE, name = "lunge",
                        telegraph = "The rat coils low, tail lashing the ash.",
                        powerNum = 7, powerDen = 10, weight = 3,
                    ),
                    MoveDef(
                        id = RAT_GNAW, name = "gnaw",
                        telegraph = "It bares cracked yellow teeth, edging closer.",
                        powerNum = 10, powerDen = 10, weight = 4,
                    ),
                    MoveDef(
                        id = RAT_EMBER_BURST, name = "ember burst",
                        telegraph = "Embers swell beneath its skin — heat rolls off it in waves.",
                        powerNum = 16, powerDen = 10, weight = 2,
                    ),
                ),
                goldDrop = 2..6,
            ),
            EMBER_WISP to MonsterDef(
                id = EMBER_WISP,
                name = "ember wisp",
                description = "A mote of living fire, frantic and hungry. It moves like a " +
                    "thought you almost had.",
                maxHp = 5,
                attack = 2,
                defense = 0,
                speed = 240,
                moves = listOf(
                    MoveDef(
                        id = WISP_FLICKER, name = "flicker",
                        telegraph = "The wisp gutters and streaks sideways, trailing sparks.",
                        powerNum = 6, powerDen = 10, weight = 3,
                    ),
                    MoveDef(
                        id = WISP_SCORCH, name = "scorch",
                        telegraph = "The wisp flares white-hot.",
                        powerNum = 12, powerDen = 10, weight = 1,
                    ),
                ),
                goldDrop = 1..3,
                loot = LootTable(
                    chancePercent = 50,
                    entries = hundredfoldItems.values
                        .filter { it.family == "dagger" && it.tier == 1 }
                        .take(12)
                        .map { LootEntry(it.id, 1) },
                ),
            ),
        ) + Bestiary.all(hundredfoldItems),
        meters = mapOf(
            WARMTH to MeterDef(
                id = WARMTH,
                name = "warmth",
                glyph = "🔥",
                // Headroom for a deep descent: ~60 actions between camps (landings
                // every 3rd floor, shrines every 5th) keeps survival a real but
                // fair pressure rather than a per-room tax.
                cap = 60,
                start = 60,
                burnPerAction = 1,
                emptyDamagePerAction = 2,
            ),
        ),
        abilities = Constellations.abilities + forgedAbilities + greatAbilities,
        nodes = Constellations.nodes + ConstellationForge.nodes(forgedAbilities) +
            GreatForge.nodes(greatSenses, greatAbilities),
        senses = Constellations.senses + greatSenses,
    )
}
