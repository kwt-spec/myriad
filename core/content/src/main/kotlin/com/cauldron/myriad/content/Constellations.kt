package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.AbilityDef
import com.cauldron.myriad.engine.model.AbilityId
import com.cauldron.myriad.engine.model.AbilityKind
import com.cauldron.myriad.engine.model.ConstellationNodeDef
import com.cauldron.myriad.engine.model.NodeEffect
import com.cauldron.myriad.engine.model.NodeId
import com.cauldron.myriad.engine.model.Senses

/**
 * The Body constellation (MASTER_PLAN §16.4): the first of five trees, built
 * deep on the reusable node engine. Exercises every node-effect class —
 * stat-ranks, ability-grants, and a sense-grant — and is the power that makes
 * the deep Ember Depths winnable. Breadth (Mind/Senses/Craft/Voice) scales next.
 */
object Constellations {

    // ── Abilities the tree can grant ────────────────────────────────────────
    val SUNDER = AbilityId("sunder")
    val SECOND_WIND = AbilityId("second_wind")
    val CINDERBRAND = AbilityId("cinderbrand")

    val abilities: Map<AbilityId, AbilityDef> = mapOf(
        SUNDER to AbilityDef(
            id = SUNDER, name = "Sunder",
            description = "A guard-breaking blow that ignores much of the foe's armour.",
            staminaCost = 35, cooldownTurns = 2,
            kind = AbilityKind.Strike(powerNum = 18, powerDen = 10, defenseIgnored = 6, critBonus = 10),
        ),
        SECOND_WIND to AbilityDef(
            id = SECOND_WIND, name = "Second Wind",
            description = "Draw on banked warmth to close your wounds mid-fight.",
            staminaCost = 45, cooldownTurns = 4,
            kind = AbilityKind.Heal(percentNum = 35, percentDen = 100),
        ),
        CINDERBRAND to AbilityDef(
            id = CINDERBRAND, name = "Cinderbrand",
            description = "A furnace-hot overhand that lands like a falling beam.",
            staminaCost = 55, cooldownTurns = 3,
            kind = AbilityKind.Strike(powerNum = 30, powerDen = 10, defenseIgnored = 2, critBonus = 15),
        ),
    )

    // ── Nodes ───────────────────────────────────────────────────────────────
    private const val BODY = "Body"

    val HARDY_1 = NodeId("body_hardy_1")
    val HARDY_2 = NodeId("body_hardy_2")
    val HARDY_3 = NodeId("body_hardy_3")
    val IRON_SKIN_1 = NodeId("body_iron_skin_1")
    val IRON_SKIN_2 = NodeId("body_iron_skin_2")
    val DEEP_LUNGS = NodeId("body_deep_lungs")
    val BRUTE_FORCE_1 = NodeId("body_brute_force_1")
    val BRUTE_FORCE_2 = NodeId("body_brute_force_2")
    val BLOODIED_EDGE = NodeId("body_bloodied_edge")
    val SUNDER_NODE = NodeId("body_sunder")
    val SECOND_WIND_NODE = NodeId("body_second_wind")
    val DEATHSIGHT_NODE = NodeId("body_deathsight")
    val CINDERBRAND_NODE = NodeId("body_cinderbrand")
    val UNBROKEN = NodeId("body_unbroken")

    private fun node(
        id: NodeId, name: String, desc: String, cost: Int,
        prereqs: List<NodeId>, effect: NodeEffect,
    ) = ConstellationNodeDef(id, name, desc, BODY, cost, prereqs, effect)

    val nodes: Map<NodeId, ConstellationNodeDef> = listOf(
        node(HARDY_1, "Hardy I", "Your frame thickens against the deep cold. +8 max HP.", 1,
            emptyList(), NodeEffect.MaxHp(8)),
        node(HARDY_2, "Hardy II", "Harder still. +12 max HP.", 1,
            listOf(HARDY_1), NodeEffect.MaxHp(12)),
        node(HARDY_3, "Hardy III", "You stop fearing the dark's teeth. +18 max HP.", 2,
            listOf(HARDY_2), NodeEffect.MaxHp(18)),
        node(IRON_SKIN_1, "Iron Skin I", "Old burns turn your hide to leather. −1 damage taken.", 1,
            listOf(HARDY_1), NodeEffect.DamageReduction(1)),
        node(IRON_SKIN_2, "Iron Skin II", "Leather becomes iron. −2 more damage taken.", 2,
            listOf(IRON_SKIN_1), NodeEffect.DamageReduction(2)),
        node(DEEP_LUNGS, "Deep Lungs", "You pace your breath for the long fight. +40 max stamina.", 1,
            listOf(HARDY_1), NodeEffect.MaxStamina(40)),
        node(BRUTE_FORCE_1, "Brute Force I", "Every blow carries more of you behind it. +2 attack.", 1,
            emptyList(), NodeEffect.Attack(2)),
        node(BRUTE_FORCE_2, "Brute Force II", "Frightening, now. +3 attack.", 2,
            listOf(BRUTE_FORCE_1), NodeEffect.Attack(3)),
        node(BLOODIED_EDGE, "Bloodied Edge", "You find the seams in things. +12% critical chance.", 2,
            listOf(BRUTE_FORCE_1), NodeEffect.Crit(12)),
        node(SUNDER_NODE, "Sunder", "Learn to break a guard outright.", 2,
            listOf(BRUTE_FORCE_1), NodeEffect.GrantAbility(SUNDER)),
        node(SECOND_WIND_NODE, "Second Wind", "Learn to close your own wounds mid-fight.", 2,
            listOf(HARDY_2), NodeEffect.GrantAbility(SECOND_WIND)),
        node(DEATHSIGHT_NODE, "Deathsight", "You learn to read a foe's exact life and intent.", 2,
            listOf(IRON_SKIN_1), NodeEffect.GrantSense(Senses.DEATHSIGHT)),
        node(CINDERBRAND_NODE, "Cinderbrand", "A furnace-hot finisher for the things below.", 3,
            listOf(SUNDER_NODE, BRUTE_FORCE_2), NodeEffect.GrantAbility(CINDERBRAND)),
        node(UNBROKEN, "Unbroken", "Nothing in the dark will put you down. +30 max HP, −2 damage taken.", 3,
            listOf(HARDY_3, IRON_SKIN_2), NodeEffect.MaxHp(30)),
    ).associateBy { it.id }
}
