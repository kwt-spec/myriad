package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.AbilityDef
import com.cauldron.myriad.engine.model.AbilityId
import com.cauldron.myriad.engine.model.AbilityKind
import com.cauldron.myriad.engine.model.ConstellationNodeDef
import com.cauldron.myriad.engine.model.NodeEffect
import com.cauldron.myriad.engine.model.NodeId
import com.cauldron.myriad.engine.model.SenseDef
import com.cauldron.myriad.engine.model.SenseHint
import com.cauldron.myriad.engine.model.SenseId

/**
 * The Great Forge — 80 generated constellations on top of the hand-authored core
 * and the [ConstellationForge]. Same Hundredfold discipline: pure functions of the
 * pools below, distinct id/name/prose (test-enforced), real mechanical effects,
 * binding floors. No save-format cost — all derives from unlocked nodes.
 *
 * Yields: 80 constellations · 560 abilities (across all 20 ability kinds) ·
 * 160 senses (across all 20 sense-hints) · ~1,680 nodes.
 */
object GreatForge {

    const val NEW_CONSTELLATIONS = 80
    /** 80 ability families ("kinds"), each having 6 tiers = 480 abilities. */
    const val ABILITY_FAMILIES = 80
    const val ABILITY_TIERS = 6
    const val ABILITY_FLOOR = 480
    const val SENSE_FLOOR = 120
    const val NODE_FLOOR = 1200

    /** 80 unique constellation names — elements, beasts, cosmos, virtues, domains. */
    val THEMES: List<String> = listOf(
        "Flame", "Frost", "Storm", "Stone", "Tide", "Gale", "Cinder", "Glacier", "Magma", "Mist",
        "Thunder", "Quartz", "Brine", "Squall", "Loam", "Sleet",
        "Wolf", "Raven", "Serpent", "Bear", "Spider", "Moth", "Hawk", "Boar", "Lynx", "Viper",
        "Crow", "Stag", "Mantis", "Jackal", "Owl", "Adder",
        "Star", "Void", "Moon", "Comet", "Eclipse", "Dawn", "Dusk", "Nadir", "Zenith", "Aurora", "Nova", "Umbra",
        "Valor", "Mercy", "Wrath", "Patience", "Cunning", "Resolve", "Sorrow", "Hope", "Dread", "Fury",
        "Grace", "Spite", "Vigil", "Ardor",
        "Hunt", "Forge", "Hearth", "Grave", "Throne", "Market", "Road", "Veil", "Loom", "Anvil",
        "Reliquary", "Threshold", "Vault", "Crucible", "Gallows", "Lantern", "Pyre", "Mill", "Bastion",
        "Harbour", "Garrote", "Tomb",
    )

    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")
    private fun slug(theme: String) = theme.lowercase()

    // ── The 20 mechanical kinds (word · base stamina · cooldown · flavour) ────
    private val KIND_WORD = listOf(
        "Strike", "Flurry", "Reckoning", "Frenzy", "Abandon", "Pierce", "Smite", "Cleave", "Riposte", "Leeching",
        "Draining", "Stagger", "Sap", "Terror", "Mending", "Channel", "Bulwark", "Respite", "Quickening", "Banishment",
    )
    private val KIND_STAMINA = listOf(24, 36, 46, 42, 40, 34, 40, 46, 22, 40, 36, 34, 32, 30, 26, 52, 34, 22, 30, 50)
    private val KIND_CD = listOf(1, 2, 4, 3, 3, 2, 3, 3, 1, 3, 3, 2, 2, 3, 2, 4, 3, 2, 2, 5)
    private val KIND_FLAVOR = listOf(
        "a clean committed blow", "a blur of small cuts", "a blow that finds a failing thing", "a wild swing that costs you blood",
        "a strike with nothing held back", "a thrust through any guard", "a blow with weight added", "a heavy two-handed chop",
        "a fast punishing counter", "a cut that drinks what it spills", "a strike that steadies your breath",
        "a blow that breaks a foe's rhythm", "a strike that buys you a moment", "a strike laced with fear",
        "a knitting of torn flesh", "a long, deep mending", "a bracing set of guard and breath", "a stolen breath",
        "a surge that lets you move again", "a word that sends a thing fleeing",
    )

    /** A kind's mechanical params, scaled by 0-based tier. */
    private fun makeKind(k: Int, s: Int): AbilityKind = when (k % 20) {
        0 -> AbilityKind.Strike(14 + s * 2, 10, 2, 12 + s * 3)
        1 -> AbilityKind.MultiStrike(7 + s, 2 + s / 3)
        2 -> AbilityKind.Execute(14 + s * 2, 30, 230 + s * 10)
        3 -> AbilityKind.Berserk(24 + s * 3, 15)
        4 -> AbilityKind.Reckless(16 + s * 2)
        5 -> AbilityKind.Precise(14 + s * 2)
        6 -> AbilityKind.Smite(14 + s * 2, 4 + s)
        7 -> AbilityKind.Hew(22 + s * 3)
        8 -> AbilityKind.Riposte(11 + s * 2, 25)
        9 -> AbilityKind.LifeStrike(14 + s * 2, 10, 50 + s * 2)
        10 -> AbilityKind.Drain(13 + s * 2, 50)
        11 -> AbilityKind.Stagger(12 + s * 2, 250 + s * 30)
        12 -> AbilityKind.Sap(11 + s * 2, 180 + s * 20, 18 + s * 2)
        13 -> AbilityKind.Terror(10 + s * 2, (35 + s * 3).coerceAtMost(70))
        14 -> AbilityKind.Heal(22 + s * 4, 100)
        15 -> AbilityKind.Channel(45 + s * 5)
        16 -> AbilityKind.Bulwark(16 + s * 3, 25 + s * 3)
        17 -> AbilityKind.Recover(35 + s * 5)
        18 -> AbilityKind.Quicken(400 + s * 40)
        else -> AbilityKind.Rout((35 + s * 5).coerceAtMost(85))
    }

    /**
     * Each of the 80 themes IS one of the 80 ability kinds: themes 0–19 use the
     * engine's 20 stateless sealed kinds; themes 20–79 use the 60 composite
     * status archetypes. Every kind anchors a family of 6 tiers.
     */
    fun abilities(): Map<AbilityId, AbilityDef> {
        val out = LinkedHashMap<AbilityId, AbilityDef>()
        THEMES.forEachIndexed { t, theme ->
            for (tier in 0 until ABILITY_TIERS) {
                val id = AbilityId("great_${slug(theme)}_$tier")
                val kind: com.cauldron.myriad.engine.model.AbilityKind
                val word: String; val stam: Int; val cdv: Int; val flavor: String
                if (t < 20) {
                    kind = makeKind(t, tier); word = KIND_WORD[t]
                    stam = KIND_STAMINA[t] + tier * 3; cdv = KIND_CD[t]; flavor = KIND_FLAVOR[t]
                } else {
                    val a = t - 20
                    kind = AbilityArchetypes.composite(a, tier); word = AbilityArchetypes.word(a)
                    stam = AbilityArchetypes.stamina(a, tier); cdv = AbilityArchetypes.cd(a); flavor = AbilityArchetypes.flavor(a)
                }
                out[id] = AbilityDef(
                    id, "$theme $word ${ROMAN[tier]}",
                    "The $theme way of ${word.lowercase()} — $flavor, at its ${ordinal(tier + 1)} form.",
                    stam, cdv, kind,
                )
            }
        }
        return out
    }

    private fun ordinal(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; else -> "sixth" }

    // ── Senses ────────────────────────────────────────────────────────────────
    private const val SENSES_PER_THEME = 2

    fun senses(): Map<SenseId, SenseDef> {
        val out = LinkedHashMap<SenseId, SenseDef>()
        val hints = SenseHint.entries
        THEMES.forEachIndexed { t, theme ->
            for (s in 0 until SENSES_PER_THEME) {
                val id = SenseId("great_sense_${slug(theme)}_$s")
                val hint = hints[(t * SENSES_PER_THEME + s) % hints.size]
                val name = if (s == 0) "${theme}sight" else "$theme Augury"
                out[id] = SenseDef(id, name, "$name — the $theme constellation's reading of a foe (${hint.name.lowercase()}).", hint)
            }
        }
        return out
    }

    // ── Stat lines ──────────────────────────────────────────────────────────
    private class Stat(val slug: String, val word: String, val phrase: String, val unit: String, val values: List<Int>, val effect: (Int) -> NodeEffect)

    private val STATS = listOf(
        Stat("vigor", "Vigor", "your life deepens", " max HP", listOf(8, 11, 14, 18)) { NodeEffect.MaxHp(it) },
        Stat("force", "Force", "your blows land heavier", " attack", listOf(1, 2, 2, 3)) { NodeEffect.Attack(it) },
        Stat("ward", "Ward", "the blow turns aside", " damage taken", listOf(1, 1, 2, 2)) { NodeEffect.DamageReduction(it) },
        Stat("edge", "Edge", "you find the seam", "% critical", listOf(5, 7, 9, 11)) { NodeEffect.Crit(it) },
        Stat("breath", "Breath", "your wind holds", " max stamina", listOf(10, 14, 18, 22)) { NodeEffect.MaxStamina(it) },
        Stat("lore", "Lore", "you learn from the dark", "% experience", listOf(6, 9, 12, 16)) { NodeEffect.XpBonus(it) },
        Stat("fortune", "Fortune", "the caches sing to you", "% gold", listOf(12, 18, 24, 30)) { NodeEffect.GoldFind(it) },
        Stat("hunger", "Hunger", "the fight feeds you", "% lifesteal", listOf(3, 4, 5, 6)) { NodeEffect.Lifesteal(it) },
        Stat("grace", "Grace", "you waste no motion", "% stamina cost", listOf(3, 4, 5, 6)) { NodeEffect.StaminaEfficiency(it) },
        Stat("tempo", "Tempo", "your tricks come round sooner", " cooldown", listOf(1, 1, 1, 1)) { NodeEffect.CooldownReduction(it) },
    )

    private const val STATS_PER_THEME = 3

    fun nodes(senses: Map<SenseId, SenseDef>, abilities: Map<AbilityId, AbilityDef>): Map<NodeId, ConstellationNodeDef> {
        val out = LinkedHashMap<NodeId, ConstellationNodeDef>()
        THEMES.forEachIndexed { t, theme ->
            var root: NodeId? = null
            // 3 stat-rank chains, varied per theme by rotation.
            for (k in 0 until STATS_PER_THEME) {
                val stat = STATS[(t * STATS_PER_THEME + k) % STATS.size]
                var prev: NodeId? = null
                stat.values.forEachIndexed { i, v ->
                    val id = NodeId("great_${slug(theme)}_${stat.slug}_${i + 1}")
                    out[id] = ConstellationNodeDef(
                        id, "$theme ${stat.word} ${ROMAN[i]}",
                        "$theme: ${stat.phrase}. +$v${stat.unit} (rank ${i + 1}).",
                        theme, 1 + i / 2, listOfNotNull(prev), stat.effect(v),
                    )
                    if (root == null) root = id
                    prev = id
                }
            }
            val anchor = listOfNotNull(root)
            // Ability grant-nodes — one per tier of this theme's family (6).
            for (tier in 0 until ABILITY_TIERS) {
                val ability = abilities.getValue(AbilityId("great_${slug(theme)}_$tier"))
                val id = NodeId("great_grant_${slug(theme)}_$tier")
                out[id] = ConstellationNodeDef(
                    id, ability.name, "Learn ${ability.name}: ${ability.description}",
                    theme, 1 + tier / 2, anchor, NodeEffect.GrantAbility(ability.id),
                )
            }
            // Sense grant-nodes.
            for (s in 0 until SENSES_PER_THEME) {
                val sense = senses.getValue(SenseId("great_sense_${slug(theme)}_$s"))
                val id = NodeId("great_sensenode_${slug(theme)}_$s")
                out[id] = ConstellationNodeDef(
                    id, sense.name, "Open the $theme constellation's eye: ${sense.description}",
                    theme, 2, anchor, NodeEffect.GrantSense(sense.id),
                )
            }
        }
        return out
    }
}
