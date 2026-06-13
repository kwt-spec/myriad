package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.LootEntry
import com.cauldron.myriad.engine.model.LootTable
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId

/**
 * The Bestiary (MASTER_PLAN §1: 1,000+ monsters): every creature in the Ember
 * Age is generated as archetype × condition × tier — distinct name, distinct
 * prose, archetype-flavoured moveset, tier-scaled stats. Counted honestly (unique
 * id/name/prose, test-enforced); the floor is binding and documented in
 * docs/BESTIARY.md.
 *
 * Pure function of the constant pools below — no RNG, no clock, stable order.
 * 14 archetypes × 6 conditions × 10 tiers = 840 species. The Ember Depths draw
 * their den-dwellers from this roster (variety per floor); the remainder is the
 * catalogued bestiary future floors and Ages pull from.
 */
object Bestiary {

    /** Binding minimum distinct species in the bestiary (§1 lifetime target track). */
    const val SPECIES_FLOOR = 700

    private class Archetype(
        val slug: String,
        val noun: String,
        val hp: Int,
        val atk: Int,
        val def: Int,
        val speed: Int,
        val family: String,
        val frag: String,
        val moves: List<MoveDef>,
    )

    private class Condition(val slug: String, val adj: String, val hpPct: Int, val atkAdd: Int, val frag: String)
    private class Tier(val index: Int, val word: String, val hpPct: Int, val frag: String)

    private fun mv(id: String, name: String, telegraph: String, num: Int, den: Int, weight: Int) =
        MoveDef(MoveId(id), name, telegraph, num, den, weight)

    private val ARCHETYPES = listOf(
        Archetype("rat", "cinder rat", 8, 3, 1, 90, "dagger",
            "A rat the size of a hound, coals glowing under its skin.",
            listOf(mv("lunge", "lunge", "It coils low, tail lashing the ash.", 8, 10, 3),
                   mv("gnaw", "gnaw", "It bares teeth the colour of clinker.", 11, 10, 3))),
        Archetype("wisp", "wisp", 5, 2, 0, 230, "staff",
            "A mote of living fire, frantic and hungry.",
            listOf(mv("flicker", "flicker", "It gutters and streaks sideways, trailing sparks.", 7, 10, 3),
                   mv("scorch", "scorch", "It flares painfully bright.", 13, 10, 1))),
        Archetype("hound", "ash hound", 14, 4, 2, 120, "sword",
            "A lean hound of grey muscle and smoke, ribs glowing as it breathes.",
            listOf(mv("snap", "snap", "It lunges, jaws wide and smoking.", 10, 10, 3),
                   mv("maul", "maul", "It drops its shoulder to bowl you down.", 15, 10, 2))),
        Archetype("crawler", "ember crawler", 18, 3, 3, 80, "spear",
            "A many-legged thing armoured in cracked, heat-blackened plates.",
            listOf(mv("skitter", "skitter-bite", "Its legs blur as it scuttles in.", 9, 10, 3),
                   mv("spew", "ember spew", "Its abdomen swells, glowing from within.", 16, 10, 1))),
        Archetype("wraith", "ash wraith", 10, 5, 0, 140, "staff",
            "A figure of drifting ash that never quite holds a shape.",
            listOf(mv("rake", "spectral rake", "It reaches through the air toward you.", 11, 10, 3),
                   mv("wail", "ember wail", "Its formless face splits in a soundless cry.", 17, 10, 1))),
        Archetype("golem", "slag golem", 30, 4, 5, 60, "mace",
            "A hulk of fused slag and cooling iron, moving with grinding patience.",
            listOf(mv("slam", "slag slam", "It raises a fist of welded iron.", 12, 10, 3),
                   mv("quake", "ground quake", "It rears back to bring both fists down.", 18, 10, 1))),
        Archetype("serpent", "cinder serpent", 16, 4, 2, 150, "whip",
            "A long serpent of overlapping ember-scales, fast as a struck match.",
            listOf(mv("strike", "fanged strike", "It draws its head back into an S.", 11, 10, 3),
                   mv("coil", "crushing coil", "It begins to wind around your legs.", 15, 10, 2))),
        Archetype("swarm", "ember swarm", 12, 2, 0, 200, "flail",
            "A roiling cloud of burning insects with a single furious will.",
            listOf(mv("engulf", "engulf", "The cloud tightens and roars inward.", 9, 10, 4),
                   mv("sear", "searing veil", "The swarm glows white at its heart.", 14, 10, 1))),
        Archetype("brute", "soot brute", 26, 6, 3, 90, "warhammer",
            "A slab-shouldered ogre-kin streaked with soot and old burns.",
            listOf(mv("clobber", "clobber", "It winds up a haymaker.", 13, 10, 3),
                   mv("stomp", "stomp", "It lifts a tree-trunk leg.", 17, 10, 2))),
        Archetype("stalker", "dark stalker", 13, 5, 1, 170, "dagger",
            "A lean shape that keeps to the edge of the firelight, watching.",
            listOf(mv("ambush", "ambush", "It tenses, weight shifting onto its toes.", 12, 10, 3),
                   mv("eviscerate", "eviscerate", "It flips its blades into a reverse grip.", 18, 10, 1))),
        Archetype("knight", "cinder knight", 28, 5, 6, 100, "sword",
            "A suit of ember-lit plate that still keeps a dead soldier's discipline.",
            listOf(mv("cleave", "cleave", "It raises a blackened longsword overhead.", 13, 10, 3),
                   mv("bash", "shield bash", "It sets its shield and drives forward.", 12, 10, 2))),
        Archetype("maw", "gaping maw", 22, 6, 2, 110, "axe",
            "Mostly mouth — a floor-bound devourer ringed with inward teeth.",
            listOf(mv("bite", "engulfing bite", "The maw yawns impossibly wide.", 14, 10, 3),
                   mv("swallow", "swallow whole", "It lunges to drag you in.", 19, 10, 1))),
        Archetype("shade", "pyre shade", 11, 6, 0, 160, "scythe",
            "A tall, thin darkness with two coals for eyes and a reaper's patience.",
            listOf(mv("reap", "reaping sweep", "It draws a long arm back like a scythe.", 13, 10, 3),
                   mv("chill", "grave chill", "The air around it dims and cools.", 16, 10, 1))),
        Archetype("drake", "ember drake", 34, 7, 4, 130, "cleaver",
            "A low, wingless drake whose every breath glows in its throat.",
            listOf(mv("rend", "rending claw", "It plants its feet and rakes the air.", 14, 10, 3),
                   mv("breath", "ember breath", "Its throat kindles to a furnace roar.", 20, 10, 2))),
    )

    private val CONDITIONS = listOf(
        Condition("ashen", "ashen", 90, 0, "Grey ash sheets its hide and sifts away as it moves."),
        Condition("sooty", "sooty", 100, 1, "Soot blackens it; it leaves smudged prints on the stone."),
        Condition("cindered", "cindered", 110, 2, "Live cinders crawl across it like restless freckles."),
        Condition("molten", "molten", 125, 3, "Seams of molten orange split its surface where it flexes."),
        Condition("voidsinged", "void-singed", 115, 4, "Parts of it are simply gone, burned out of the world."),
        Condition("hushtouched", "hush-touched", 130, 5, "It makes no sound at all, and the silence is worse."),
    )

    private val TIERS = listOf(
        Tier(1, "lesser", 100, "A small, half-grown specimen."),
        Tier(2, "common", 145, "A common adult of its kind."),
        Tier(3, "hale", 200, "Hale and well-fed on the deep heat."),
        Tier(4, "grown", 270, "Fully grown and unafraid."),
        Tier(5, "elder", 360, "An elder, scarred and cunning."),
        Tier(6, "dire", 470, "A dire specimen, oversized and wrong."),
        Tier(7, "great", 600, "A great one, the terror of its floor."),
        Tier(8, "dread", 760, "A dread-thing other monsters avoid."),
        Tier(9, "ancient", 950, "Ancient beyond the cooling of the world."),
        Tier(10, "apex", 1200, "An apex horror; the dark made flesh."),
    )

    fun speciesCount(): Int = ARCHETYPES.size * CONDITIONS.size * TIERS.size

    private fun idOf(arch: Archetype, cond: Condition, tier: Tier) =
        MonsterId("best_${arch.slug}_${cond.slug}_t${tier.index}")

    /** Item tier a species' loot draws from (item tiers run 1..5; species tiers 1..10). */
    private fun itemTierFor(speciesTier: Int): Int = ((speciesTier + 1) / 2).coerceIn(1, 5)

    fun all(allItems: Map<ItemId, ItemDef>): Map<MonsterId, MonsterDef> {
        val out = LinkedHashMap<MonsterId, MonsterDef>()
        for (arch in ARCHETYPES) for (cond in CONDITIONS) for (tier in TIERS) {
            val hp = (arch.hp.toLong() * tier.hpPct / 100 * cond.hpPct / 100).toInt().coerceAtLeast(1)
            val atk = arch.atk + tier.index * 2 + cond.atkAdd
            val def = arch.def + tier.index / 2
            out[idOf(arch, cond, tier)] = MonsterDef(
                id = idOf(arch, cond, tier),
                name = "${cond.adj} ${tier.word} ${arch.noun}",
                description = "${arch.frag} ${cond.frag} ${tier.frag}",
                maxHp = hp,
                attack = atk,
                defense = def,
                speed = arch.speed,
                moves = arch.moves,
                goldDrop = tier.index..(tier.index * 5),
                loot = lootFor(arch.family, itemTierFor(tier.index), allItems),
            )
        }
        return out
    }

    private fun lootFor(family: String, itemTier: Int, allItems: Map<ItemId, ItemDef>): LootTable {
        val exact = allItems.values.filter { it.family == family && it.tier == itemTier }
        val pool = exact.ifEmpty { allItems.values.filter { it.family == family } }
            .ifEmpty { allItems.values.toList() }
        return LootTable(chancePercent = 30, entries = pool.take(24).map { LootEntry(it.id, 1) })
    }

    // ── Depths placement: each floor draws a depth-appropriate species ──────────

    /** Species tier for a depth: floors 1–10 → t1 … 91–100 → t10. */
    private fun tierForDepth(depth: Int): Int = (((depth - 1) / 10) + 1).coerceIn(1, TIERS.size)

    /** The den-dweller of a given floor — rotated for variety, scaled by depth. */
    fun denSpeciesId(depth: Int): MonsterId {
        val arch = ARCHETYPES[(depth - 1) % ARCHETYPES.size]
        val cond = CONDITIONS[(depth - 1) % CONDITIONS.size]
        val tier = TIERS[tierForDepth(depth) - 1]
        return idOf(arch, cond, tier)
    }
}
