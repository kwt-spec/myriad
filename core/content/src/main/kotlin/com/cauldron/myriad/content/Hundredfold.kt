package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.LootEntry
import com.cauldron.myriad.engine.model.LootTable
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId

/**
 * The Hundredfold generation layer (MASTER_PLAN §16.5): EVERY weapon family
 * ships in large quantity — distinct named entries assembled from curated bases
 * × shared affix pools, counted honestly (no stat reskins; prose differs per
 * entry). The floor is binding and per-family; the table lives in docs/ITEMS.md
 * and is enforced by HundredfoldTest.
 *
 * Pure function of the constant pools below: no RNG, no clock, stable order.
 * Generation happens at pack construction, so the Forge validators see every
 * entry. Counts: 12 families × (bases × 5 prefixes × 5 suffixes) = 1,550
 * weapons + the First Ember capstone.
 */
object Hundredfold {

    /** Binding minimum distinct entries per weapon family (§1). */
    const val FAMILY_FLOOR = 100

    // ── Affix pools (shared by every family) ────────────────────────────────

    private class Prefix(val slug: String, val adj: String, val atk: Int, val def: Int, val frag: String)
    private class Suffix(val slug: String, val tag: String, val atk: Int, val def: Int, val frag: String)
    private class Base(val slug: String, val noun: String, val atk: Int, val frag: String)
    private class Family(val slug: String, val bases: List<Base>)

    private val PREFIXES = listOf(
        Prefix("ashen", "ashen", 0, 0, "Ash clings to it no matter how often it is wiped clean."),
        Prefix("sootbound", "soot-bound", 1, 0, "Soot has worked itself into every seam and stays there."),
        Prefix("cindered", "cindered", 2, 0, "Faint cinders wake along it when it is swung."),
        Prefix("emberwrought", "ember-wrought", 3, 1, "It was forged in a fire that has not entirely gone out."),
        Prefix("moltenheart", "molten-hearted", 4, 1, "Something at its core still moves like slow fire."),
    )

    private val SUFFIXES = listOf(
        Suffix("drift", "of the Drift", 0, 0, "It was dug out of the grey drifts and remembers them."),
        Suffix("coldhours", "of Cold Hours", 0, 1, "It kept someone alive through the nights without fire."),
        Suffix("rootdark", "of the Root-Choked Dark", 1, 1, "Pale roots once grew through it; their tracery remains."),
        Suffix("bankedfire", "of Banked Fire", 2, 1, "Warmth lingers in the grip long after it is set down."),
        Suffix("firstlight", "of the First Light", 2, 2, "It has glimpsed the ember-light above and wants to return."),
    )

    private val FAMILIES = listOf(
        Family("dagger", listOf(
            Base("shiv", "shiv", 1, "A short, mean sliver of iron."),
            Base("dirk", "dirk", 2, "A long-bladed dirk, plain and certain."),
            Base("stiletto", "stiletto", 2, "A needle of a blade, made for the gaps in armour."),
            Base("kris", "kris", 3, "A wave-bladed kris that drags strangely at the air."),
            Base("fang", "fang-knife", 3, "A blade ground from something's tooth; better not to ask whose."),
        )),
        Family("sword", listOf(
            Base("arming", "arming sword", 2, "A soldier's plain cross-hilt sword."),
            Base("falchion", "falchion", 3, "Heavy-bladed and forward-weighted, a chopper's tool."),
            Base("estoc", "estoc", 3, "Edgeless and stiff, a sword that is mostly point."),
            Base("backsword", "backsword", 2, "Single-edged and practical, with a worn knuckle-bow."),
            Base("warblade", "war blade", 4, "Too much sword for a cellar; just enough for what is below."),
            Base("relicblade", "relic blade", 4, "An age-old blade re-hilted by later, rougher hands."),
        )),
        Family("staff", listOf(
            Base("ashwood", "ashwood staff", 1, "A staff of pale ashwood, smooth with handling."),
            Base("rootstave", "root-stave", 2, "A stave of dried root, knotted like old knuckles."),
            Base("emberrod", "ember rod", 2, "A rod with a banked coal seated in its iron crown."),
            Base("ferrule", "ferruled staff", 2, "Iron-shod top and bottom; it rings on the flagstones."),
            Base("pyrewood", "pyrewood staff", 3, "Cut from a pyre that refused to finish burning."),
            Base("hushbranch", "hush-branch", 3, "A branch from no named tree; it is very quiet."),
        )),
        Family("axe", listOf(
            Base("handaxe", "hand axe", 2, "A stubby axe equally at home in wood or worse."),
            Base("hatchet", "hatchet", 1, "Light enough to throw, mean enough to keep."),
            Base("bearded", "bearded axe", 3, "Its hooked beard catches shields and limbs alike."),
            Base("broadaxe", "broad axe", 4, "A wide crescent of grey steel on a long haft."),
            Base("splitter", "splitting axe", 3, "Wedge-headed, made for things that resist being opened."),
        )),
        Family("mace", listOf(
            Base("cudgel", "iron cudgel", 1, "A blunt bar of pitted iron with a wrapped grip."),
            Base("ridged", "ridged mace", 2, "Cast with raised ridges that bite where they land."),
            Base("flanged", "flanged mace", 3, "Six steel flanges fan from its head like a black flower."),
            Base("morningstar", "morningstar", 3, "A spiked ball on a haft; subtlety was never the point."),
            Base("warmace", "war mace", 4, "A two-handed maul of a mace that ends arguments."),
        )),
        Family("spear", listOf(
            Base("shortspear", "short spear", 2, "A plain leaf-bladed spear, reliable as a fence post."),
            Base("harpoon", "harpoon", 2, "Barbed and corded, made to go in and not come out."),
            Base("pike", "pike", 3, "Absurdly long; it keeps the warm dark at arm's reach."),
            Base("trident", "trident", 3, "Three rusted tines that catch more than they pierce."),
            Base("lance", "lance", 4, "A heavy couched lance, awkward indoors and lethal anyway."),
        )),
        Family("bow", listOf(
            Base("shortbow", "short bow", 1, "A simple self-bow of springy ashwood."),
            Base("recurve", "recurve bow", 2, "Its back-curved limbs store a vicious amount of spite."),
            Base("longbow", "longbow", 3, "Tall as you are, and twice as patient."),
            Base("hornbow", "horn bow", 3, "Laminated horn and sinew; it cracks like a whip on release."),
            Base("siegebow", "siege bow", 4, "An oversized bow that needs a stirrup and a grudge."),
        )),
        Family("warhammer", listOf(
            Base("tackhammer", "tack hammer", 1, "A small hard hammer that hits above its weight."),
            Base("clawhammer", "claw hammer", 2, "Hammer one side, hooked claw the other."),
            Base("sledge", "sledge", 3, "A long-hafted sledge for walls and the things behind them."),
            Base("polehammer", "pole hammer", 3, "Hammer, spike, and hook on a soldier's-height shaft."),
            Base("maul", "great maul", 4, "A monstrous head of cast iron; the floor flinches."),
        )),
        Family("flail", listOf(
            Base("nunchaku", "linked rods", 2, "Two iron rods on a short chain; it argues with the wielder too."),
            Base("thresher", "threshing flail", 2, "A peasant's flail that learned a crueler trade."),
            Base("chainflail", "chain flail", 3, "A spiked head whirling on a hand's-length chain."),
            Base("scourge", "spiked scourge", 3, "Three weighted chains that wrap before they break."),
            Base("ballandchain", "ball and chain", 4, "A heavy spiked ball you commit to the moment you swing."),
        )),
        Family("scythe", listOf(
            Base("sickle", "sickle", 1, "A hand-sickle, curved for grain and other harvests."),
            Base("handscythe", "hand scythe", 2, "A short scythe rebalanced for a fighter's reach."),
            Base("warscythe", "war scythe", 3, "A scythe blade turned upright to face the wielder's foes."),
            Base("glaive", "glaive", 3, "A single broad blade on a long pole, all sweep and menace."),
            Base("reaper", "reaper's scythe", 4, "Tall, hooked, and theatrically grim; it earns the name."),
        )),
        Family("cleaver", listOf(
            Base("froe", "froe", 2, "An L-shaped splitting blade, ugly and effective."),
            Base("hatchetcleaver", "cleaver", 2, "A heavy rectangle of steel that does not bother with a point."),
            Base("billhook", "billhook", 3, "A hooked hedging blade that catches and pulls."),
            Base("butcher", "butcher's cleaver", 3, "Honed for joints; it does not ask which kind."),
            Base("greatcleaver", "great cleaver", 4, "A slab of a blade that needs both hands and no apology."),
        )),
        Family("whip", listOf(
            Base("lash", "leather lash", 1, "A plain braided lash that cracks the cold air."),
            Base("scourgewhip", "knotted scourge", 2, "Knots worked along its length for the bite to catch on."),
            Base("barbedwhip", "barbed whip", 3, "Small barbs set into the tip; it does not let go cleanly."),
            Base("chainwhip", "chain whip", 3, "Links instead of leather; it rings as it flays."),
            Base("urumi", "coiled urumi", 4, "A whip of flexible steel that is as dangerous to hold as to face."),
        )),
    )

    val FAMILY_SLUGS: List<String> = FAMILIES.map { it.slug }

    // ── Item generation ─────────────────────────────────────────────────────

    fun weapons(): Map<ItemId, ItemDef> {
        val out = LinkedHashMap<ItemId, ItemDef>()
        for (family in FAMILIES) {
            for (base in family.bases) for (prefix in PREFIXES) for (suffix in SUFFIXES) {
                val id = ItemId("${family.slug}_${base.slug}_${prefix.slug}_${suffix.slug}")
                val attack = base.atk + prefix.atk + suffix.atk
                out[id] = ItemDef(
                    id = id,
                    name = "${prefix.adj} ${base.noun} ${suffix.tag}",
                    description = "${base.frag} ${prefix.frag} ${suffix.frag}",
                    attackBonus = attack,
                    defenseBonus = prefix.def + suffix.def,
                    family = family.slug,
                    // Tier derives from power, so envelopes are monotone by construction.
                    tier = ((attack + 1) / 2).coerceIn(1, 5),
                )
            }
        }
        return out
    }

    /** Per-family counts — the source of truth the doc table must match. */
    fun familyCounts(): Map<String, Int> =
        FAMILIES.associate { it.slug to it.bases.size * PREFIXES.size * SUFFIXES.size }

    val FIRST_EMBER = ItemId("staff_first_ember_unique")

    fun capstone(): ItemDef = ItemDef(
        id = FIRST_EMBER,
        name = "The First Ember",
        description = "A staff crowned with a coal that predates the cooling of the world. " +
            "It does not burn the hand that holds it; it remembers being held.",
        attackBonus = 7,
        defenseBonus = 2,
        family = "staff",
        tier = 5,
    )

    // ── Monster variants ─────────────────────────────────────────────────────

    private val DEPTH_ADJ = listOf(
        "ashen", "sooty", "cindered", "charred", "smoldering",
        "ember-eyed", "molten", "pyre-born", "void-singed", "hush-touched",
    )

    /** The Ember Depths run a hundred floors deep (MASTER_PLAN §16 — hundredfold). */
    const val FLOORS = 100

    fun monsterIdAt(depth: Int): MonsterId =
        if (depth % 2 == 1) MonsterId("rat_d$depth") else MonsterId("wisp_d$depth")

    private fun adjFor(depth: Int): String = DEPTH_ADJ[(depth - 1) % DEPTH_ADJ.size]

    fun depthMonsters(allItems: Map<ItemId, ItemDef>): Map<MonsterId, MonsterDef> {
        val out = LinkedHashMap<MonsterId, MonsterDef>()
        for (depth in 1..FLOORS) {
            val adj = adjFor(depth)
            val id = monsterIdAt(depth)
            out[id] = if (depth % 2 == 1) {
                MonsterDef(
                    id = id,
                    name = "$adj cinder rat",
                    description = "A $adj kin of the cellar rats, fattened on the heat of floor $depth. " +
                        "Its coals burn brighter down here.",
                    maxHp = 8 + 4 * depth,
                    attack = 3 + depth,
                    defense = 1 + depth / 3,
                    speed = 80 + 4 * depth,
                    moves = listOf(
                        MoveDef(MoveId("lunge"), "lunge", "It coils low, tail lashing the hot ash.", 7, 10, 3),
                        MoveDef(MoveId("gnaw"), "gnaw", "It bares teeth the color of clinker.", 10, 10, 4),
                        MoveDef(MoveId("burst"), "ember burst", "Heat rolls off it in slow, hungry waves.", 16, 10, 2),
                    ),
                    goldDrop = depth..(2 + 4 * depth),
                    loot = lootFor(depth, allItems),
                )
            } else {
                MonsterDef(
                    id = id,
                    name = "$adj wisp",
                    description = "A $adj mote of living fire, quicker and angrier than the ones above. " +
                        "Floor $depth feeds it well.",
                    maxHp = 5 + 2 * depth,
                    attack = 2 + (2 * depth) / 3,
                    defense = depth / 4,
                    speed = 240,
                    moves = listOf(
                        MoveDef(MoveId("flicker"), "flicker", "It gutters and streaks sideways, trailing white sparks.", 6, 10, 3),
                        MoveDef(MoveId("scorch"), "scorch", "It flares painfully bright.", 12, 10, 1),
                    ),
                    goldDrop = depth..(1 + 3 * depth),
                    loot = lootFor(depth, allItems),
                )
            }
        }
        return out
    }

    /** Each floor themes to a different weapon family, for variety on the way down. */
    fun familyForDepth(depth: Int): String = FAMILY_SLUGS[(depth - 1) % FAMILY_SLUGS.size]

    /** Tiers stretch across the whole descent: floors 1–20 → t1 … 81–100 → t5. */
    fun tierForDepth(depth: Int): Int = ((depth - 1) * 5 / FLOORS + 1).coerceIn(1, 5)

    private fun bucket(allItems: Map<ItemId, ItemDef>, family: String, tier: Int): List<ItemDef> {
        val exact = allItems.values.filter { it.family == family && it.tier == tier }
        return exact.ifEmpty { allItems.values.filter { it.family == family } }
    }

    private fun lootFor(depth: Int, allItems: Map<ItemId, ItemDef>): LootTable {
        val entries = bucket(allItems, familyForDepth(depth), tierForDepth(depth))
            .take(30)
            .map { LootEntry(it.id, 1) }
        return LootTable(chancePercent = 35, entries = entries)
    }

    // ── The Ember Depths ──────────────────────────────────────────────────────

    fun landingId(depth: Int) = RoomId("depths_landing_$depth")
    fun denId(depth: Int) = RoomId("depths_den_$depth")
    fun hoardId(depth: Int) = RoomId("depths_hoard_$depth")
    fun galleryId(depth: Int) = RoomId("depths_gallery_$depth")
    fun shrineId(depth: Int) = RoomId("depths_shrine_$depth")

    /** Even floors grow a gallery (a cache side-room). */
    fun hasGallery(depth: Int): Boolean = depth % 2 == 0

    /** Every fifth floor holds a shrine — an extra ember-camp, off the den. */
    fun hasShrine(depth: Int): Boolean = depth % 5 == 0

    /** Landing is a haven every third floor (in addition to shrine-camps). */
    fun landingIsHaven(depth: Int): Boolean = depth % 3 == 0

    fun roomsOnFloor(depth: Int): Int =
        3 + (if (hasGallery(depth)) 1 else 0) + (if (hasShrine(depth)) 1 else 0)

    fun generatedRoomCount(): Int = (1..FLOORS).sumOf { roomsOnFloor(it) }

    private val LANDING_FLAVOR = listOf(
        "The stair ends in a chamber of warm brick.",
        "Heat breathes up the shaft like something sleeping.",
        "Old tool-marks score the walls; someone mined toward the warmth.",
        "The ash here is darker, almost black, and faintly soft underfoot.",
        "A dry wind moves through, smelling of clinker and time.",
        "The brickwork has begun to vitrify, glassy where the heat leans on it.",
        "Mortar weeps slow amber beads down the walls.",
        "The floor is warm enough to feel through boot leather.",
        "Light without source: a deep red seam glows along the ceiling.",
        "The walls hum, very low, like a banked furnace dreaming.",
    )

    fun depthsRooms(entranceRoom: RoomId, allItems: Map<ItemId, ItemDef>): Map<RoomId, RoomDef> {
        val out = LinkedHashMap<RoomId, RoomDef>()
        for (depth in 1..FLOORS) {
            val upExit = if (depth == 1) {
                ExitDef("Up — the cracked stair to the vault", entranceRoom)
            } else {
                ExitDef("Up — back toward floor ${depth - 1}", hoardId(depth - 1))
            }
            val haven = landingIsHaven(depth)

            out[landingId(depth)] = RoomDef(
                id = landingId(depth),
                name = "Ember Depths — Floor $depth",
                description = LANDING_FLAVOR[(depth - 1) % LANDING_FLAVOR.size] +
                    if (haven) " A niche of banked embers glows here, kept by no one." else "",
                exits = buildList {
                    add(upExit)
                    add(ExitDef("Onward — a den that smells of singed fur", denId(depth)))
                    if (hasGallery(depth)) add(ExitDef("Aside — a low gallery of cached ash", galleryId(depth)))
                },
                haven = haven,
                campText = if (haven) {
                    "You settle into the ember niche of floor $depth and let the banked heat undo " +
                        "the cold's work. The deep places wait."
                } else null,
            )

            if (hasGallery(depth)) {
                // Cache side-room: a dead end with a tier-true find, no monster.
                val galleryTier = tierForDepth(depth)
                val galleryCandidates = bucket(allItems, familyForDepth(depth), galleryTier)
                out[galleryId(depth)] = RoomDef(
                    id = galleryId(depth),
                    name = "Gallery — Floor $depth",
                    description = "A long, low gallery where the diggers of floor $depth stacked what " +
                        "they meant to come back for. Most is ash now. Most.",
                    exits = listOf(ExitDef("Back — to the landing", landingId(depth))),
                    hiddenItem = galleryCandidates[(depth * 7) % galleryCandidates.size].id,
                    searchText = "You rake through the cached ash and lift something still whole.",
                )
            }

            out[denId(depth)] = RoomDef(
                id = denId(depth),
                name = "Den — Floor $depth",
                description = "Bones and slag are heaped in corners. Something lives here, and the " +
                    "warmth of floor $depth has made it strong.",
                exits = buildList {
                    add(ExitDef("Back — to the landing", landingId(depth)))
                    add(ExitDef("Through — toward a glint of metal", hoardId(depth)))
                    if (hasShrine(depth)) add(ExitDef("Aside — a still alcove, oddly warm", shrineId(depth)))
                },
                monster = monsterIdAt(depth),
            )

            if (hasShrine(depth)) {
                // Shrine: an extra ember-camp, keeping deep delving survivable.
                out[shrineId(depth)] = RoomDef(
                    id = shrineId(depth),
                    name = "Shrine — Floor $depth",
                    description = "A still alcove where someone long ago banked a fire and never let it " +
                        "die. The coals of floor $depth still breathe, faintly, in the dark.",
                    exits = listOf(ExitDef("Back — to the den", denId(depth))),
                    haven = true,
                    campText = "You feed the shrine-coals of floor $depth and warm yourself at a fire " +
                        "older than your descent. For a moment the deep is almost kind.",
                )
            }

            // Hoard caches run one tier above the floor: what the dead delvers
            // could not carry up is worth carrying up.
            val hoardTier = (tierForDepth(depth) + 1).coerceAtMost(5)
            val hoardCandidates = bucket(allItems, familyForDepth(depth), hoardTier)
            val hoardItem = hoardCandidates[depth % hoardCandidates.size].id

            out[hoardId(depth)] = RoomDef(
                id = hoardId(depth),
                name = "Hoard — Floor $depth",
                description = "A dead end where someone cached what they could not carry up. " +
                    "Ash has buried most of it; something may remain.",
                exits = buildList {
                    add(ExitDef("Back — through the den", denId(depth)))
                    if (depth < FLOORS) add(ExitDef("Down — a stair into deeper heat", landingId(depth + 1)))
                },
                hiddenItem = if (depth == FLOORS) FIRST_EMBER else hoardItem,
                searchText = if (depth == FLOORS) {
                    "Beneath a fall of fused ash you find it: a staff crowned with a coal older than " +
                        "the cold. The First Ember. The dark around it feels respectful."
                } else {
                    "You sift the cached ash of floor $depth and your fingers close on a weapon, " +
                        "wrapped and waiting."
                },
            )
        }
        return out
    }
}
