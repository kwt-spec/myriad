package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.AbilityDef
import com.cauldron.myriad.engine.model.AbilityId
import com.cauldron.myriad.engine.model.AbilityKind
import com.cauldron.myriad.engine.model.ConstellationNodeDef
import com.cauldron.myriad.engine.model.NodeEffect
import com.cauldron.myriad.engine.model.NodeId

/**
 * The Constellation Forge — generative progression at scale (the "WAY MORE"
 * pass), the same discipline as the item/monster Hundredfold: tiered ability
 * families and per-tree stat-rank chains generated as pure functions of the
 * pools below, distinct in id/name/prose, validated by the pack like all content.
 *
 * Sits ON TOP of the hand-authored [Constellations] core (whose named ids the
 * tests reference). Together they form a deep roster:
 *   abilities ≥ ABILITY_FLOOR, nodes ≥ NODE_FLOOR.
 */
object ConstellationForge {

    const val ABILITY_FLOOR = 60
    const val NODE_FLOOR = 300

    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

    // ── Ability families × tiers ─────────────────────────────────────────────

    private class StrikeFam(val slug: String, val name: String, val pow0: Int, val powStep: Int, val defIgnore: Int, val defStep: Int, val crit: Int, val critStep: Int, val stam: Int, val cd: Int, val flavor: String)
    private class HealFam(val slug: String, val name: String, val pct0: Int, val pctStep: Int, val stam: Int, val cd: Int, val flavor: String)
    private class LifeFam(val slug: String, val name: String, val pow0: Int, val powStep: Int, val heal: Int, val stam: Int, val cd: Int, val flavor: String)
    private class RoutFam(val slug: String, val name: String, val ch0: Int, val chStep: Int, val stam: Int, val cd: Int, val flavor: String)

    private val STRIKES = listOf(
        StrikeFam("slash", "Slash", 11, 2, 2, 0, 20, 2, 20, 1, "a clean drawn cut"),
        StrikeFam("pierce", "Pierce", 13, 2, 8, 2, 10, 1, 30, 2, "a thrust that finds the gap in armour"),
        StrikeFam("crush", "Crush", 20, 3, 0, 0, 5, 1, 42, 3, "a downward blow with all your weight"),
        StrikeFam("sear", "Sear", 16, 2, 4, 1, 25, 2, 35, 2, "a stroke that leaves the air burning"),
        StrikeFam("rend", "Rend", 17, 2, 6, 1, 15, 1, 38, 3, "a tearing cut that does not close"),
    )
    private val HEALS = listOf(
        HealFam("mend", "Mend", 18, 4, 25, 2, "a quick knitting of torn flesh"),
        HealFam("surge", "Surge", 30, 5, 45, 4, "a flood of borrowed warmth"),
        HealFam("knit", "Knit", 22, 3, 30, 3, "a steady closing of the wounds"),
    )
    private val LIVES = listOf(
        LifeFam("leech", "Leech", 14, 2, 50, 38, 3, "a cut that drinks what it spills"),
        LifeFam("drain", "Drain", 18, 2, 60, 45, 4, "a wound that pours into your own"),
    )
    private val ROUTS = listOf(
        RoutFam("daunt", "Daunt", 30, 5, 28, 3, "a presence that makes lesser things doubt"),
        RoutFam("cow", "Cow", 45, 5, 50, 5, "a will that breaks a thing's nerve outright"),
    )

    private const val STRIKE_TIERS = 7
    private const val OTHER_TIERS = 4

    fun abilities(): Map<AbilityId, AbilityDef> {
        val out = LinkedHashMap<AbilityId, AbilityDef>()
        for (f in STRIKES) for (t in 1..STRIKE_TIERS) {
            val id = AbilityId("forge_${f.slug}_$t")
            out[id] = AbilityDef(id, "${f.name} ${ROMAN[t - 1]}", "${cap(f.flavor)}, honed to its ${ordinal(t)} form.",
                f.stam + (t - 1) * 5, f.cd,
                AbilityKind.Strike(f.pow0 + (t - 1) * f.powStep, 10, f.defIgnore + (t - 1) * f.defStep, f.crit + (t - 1) * f.critStep))
        }
        for (f in HEALS) for (t in 1..OTHER_TIERS) {
            val id = AbilityId("forge_${f.slug}_$t")
            out[id] = AbilityDef(id, "${f.name} ${ROMAN[t - 1]}", "${cap(f.flavor)}, deepened.",
                f.stam + (t - 1) * 4, f.cd, AbilityKind.Heal(f.pct0 + (t - 1) * f.pctStep, 100))
        }
        for (f in LIVES) for (t in 1..OTHER_TIERS) {
            val id = AbilityId("forge_${f.slug}_$t")
            out[id] = AbilityDef(id, "${f.name} ${ROMAN[t - 1]}", "${cap(f.flavor)}, grown crueler.",
                f.stam + (t - 1) * 4, f.cd, AbilityKind.LifeStrike(f.pow0 + (t - 1) * f.powStep, 10, f.heal))
        }
        for (f in ROUTS) for (t in 1..OTHER_TIERS) {
            val id = AbilityId("forge_${f.slug}_$t")
            out[id] = AbilityDef(id, "${f.name} ${ROMAN[t - 1]}", "${cap(f.flavor)}, made certain.",
                f.stam + (t - 1) * 4, f.cd, AbilityKind.Rout((f.ch0 + (t - 1) * f.chStep).coerceAtMost(90)))
        }
        return out
    }

    // ── Per-tree stat-rank chains ────────────────────────────────────────────

    private class StatLine(val slug: String, val phrase: String, val unit: String, val values: List<Int>, val effect: (Int) -> NodeEffect)

    private fun lines(tree: String): List<StatLine> = when (tree) {
        "Body" -> listOf(
            StatLine("vitality", "your flesh thickens against the deep", " max HP", listOf(6, 8, 10, 12, 14, 16)) { NodeEffect.MaxHp(it) },
            StatLine("hide", "your hide turns the blow", " damage taken", listOf(1, 1, 1, 2, 2, 2)) { NodeEffect.DamageReduction(it) },
            StatLine("might", "your blows land heavier", " attack", listOf(1, 1, 2, 2, 3, 3)) { NodeEffect.Attack(it) },
            StatLine("wind", "your lungs hold the long fight", " max stamina", listOf(10, 12, 15, 18, 20, 25)) { NodeEffect.MaxStamina(it) },
            StatLine("thirst", "the fight feeds you", "% lifesteal", listOf(2, 3, 4, 5, 6, 7)) { NodeEffect.Lifesteal(it) },
        )
        "Mind" -> listOf(
            StatLine("study", "you learn faster from the dark", "% experience", listOf(6, 8, 10, 12, 14, 16)) { NodeEffect.XpBonus(it) },
            StatLine("economy", "you waste no motion", "% stamina cost", listOf(3, 4, 5, 6, 7, 8)) { NodeEffect.StaminaEfficiency(it) },
            StatLine("tempo", "your tricks come round sooner", " cooldown", listOf(1, 1, 1, 1, 1, 1)) { NodeEffect.CooldownReduction(it) },
            StatLine("clarity", "the killing angle is always there", "% critical", listOf(4, 5, 6, 7, 8, 9)) { NodeEffect.Crit(it) },
            StatLine("reserve", "a trained mind tires last", " max stamina", listOf(8, 10, 12, 15, 18, 20)) { NodeEffect.MaxStamina(it) },
        )
        "Senses" -> listOf(
            StatLine("eye", "nothing the firelight touches escapes you", "% critical", listOf(5, 6, 7, 8, 9, 10)) { NodeEffect.Crit(it) },
            StatLine("evade", "what you see, you slip", " damage taken", listOf(1, 1, 1, 2, 2, 2)) { NodeEffect.DamageReduction(it) },
            StatLine("insight", "every detail teaches", "% experience", listOf(5, 6, 7, 8, 9, 10)) { NodeEffect.XpBonus(it) },
            StatLine("precision", "you strike exactly where it tells", " attack", listOf(1, 1, 2, 2, 3, 3)) { NodeEffect.Attack(it) },
            StatLine("vigil", "you are never quite surprised", " max stamina", listOf(8, 10, 12, 14, 16, 18)) { NodeEffect.MaxStamina(it) },
        )
        "Craft" -> listOf(
            StatLine("scavenge", "you leave nothing of worth behind", "% gold", listOf(12, 16, 20, 24, 28, 32)) { NodeEffect.GoldFind(it) },
            StatLine("render", "you make the kill into strength", "% lifesteal", listOf(2, 3, 4, 5, 6, 7)) { NodeEffect.Lifesteal(it) },
            StatLine("grit", "a maker's stubborn hardiness", " max HP", listOf(6, 8, 10, 12, 14, 16)) { NodeEffect.MaxHp(it) },
            StatLine("whetting", "you keep a keener edge than most", " attack", listOf(1, 1, 2, 2, 3, 3)) { NodeEffect.Attack(it) },
            StatLine("thrift", "you spend yourself like a miser", "% stamina cost", listOf(3, 4, 5, 6, 7, 8)) { NodeEffect.StaminaEfficiency(it) },
        )
        else -> listOf( // Voice
            StatLine("stature", "you fill more of the dark than your body", " attack", listOf(1, 1, 2, 2, 3, 3)) { NodeEffect.Attack(it) },
            StatLine("renown", "your legend grows in the telling", " max HP", listOf(6, 8, 10, 12, 14, 16)) { NodeEffect.MaxHp(it) },
            StatLine("saga", "you learn from your own legend", "% experience", listOf(5, 6, 7, 8, 9, 10)) { NodeEffect.XpBonus(it) },
            StatLine("will", "you decide what the dark does to you", " damage taken", listOf(1, 1, 1, 2, 2, 2)) { NodeEffect.DamageReduction(it) },
            StatLine("authority", "none mistake your meaning", "% critical", listOf(4, 5, 6, 7, 8, 9)) { NodeEffect.Crit(it) },
        )
    }

    private val TREES = listOf("Body", "Mind", "Senses", "Craft", "Voice")

    /** Which tree teaches which generated ability (for its grant-node). */
    private fun treeForStrike(slug: String) = when (slug) {
        "slash", "crush" -> "Body"
        else -> "Senses"
    }

    fun nodes(forged: Map<AbilityId, AbilityDef>): Map<NodeId, ConstellationNodeDef> {
        val out = LinkedHashMap<NodeId, ConstellationNodeDef>()
        val treeRoot = HashMap<String, NodeId>()

        for (tree in TREES) {
            for (line in lines(tree)) {
                var prev: NodeId? = null
                line.values.forEachIndexed { i, v ->
                    val id = NodeId("forge_${tree.lowercase()}_${line.slug}_${i + 1}")
                    val cost = 1 + i / 2
                    out[id] = ConstellationNodeDef(
                        id, "${cap(line.slug)} ${ROMAN[i]}",
                        "${cap(line.phrase)}. +$v${line.unit} (rank ${i + 1}).",
                        tree, cost, listOfNotNull(prev), line.effect(v),
                    )
                    if (prev == null) treeRoot.putIfAbsent(tree, id)
                    prev = id
                }
            }
        }

        // Grant-nodes for every forged ability, gated behind its tree's root.
        fun grant(tree: String, ability: AbilityDef, tier: Int) {
            val id = NodeId("forge_grant_${ability.id.value}")
            out[id] = ConstellationNodeDef(
                id, ability.name, "Learn to wield ${ability.name}: ${ability.description}",
                tree, 1 + tier / 3, listOfNotNull(treeRoot[tree]),
                NodeEffect.GrantAbility(ability.id),
            )
        }
        for (f in STRIKES) for (t in 1..STRIKE_TIERS) grant(treeForStrike(f.slug), forged.getValue(AbilityId("forge_${f.slug}_$t")), t)
        for (f in HEALS) for (t in 1..OTHER_TIERS) grant(if (f.slug == "surge") "Voice" else "Mind", forged.getValue(AbilityId("forge_${f.slug}_$t")), t)
        for (f in LIVES) for (t in 1..OTHER_TIERS) grant("Craft", forged.getValue(AbilityId("forge_${f.slug}_$t")), t)
        for (f in ROUTS) for (t in 1..OTHER_TIERS) grant("Voice", forged.getValue(AbilityId("forge_${f.slug}_$t")), t)

        return out
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun ordinal(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; else -> "${n}th" }
}
