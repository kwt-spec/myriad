package com.cauldron.myriad.engine.model

/**
 * Progression content (MASTER_PLAN §16.4): abilities, constellation nodes,
 * senses, and function-verbs. All pure data — the engine reads these to derive
 * stats, grant verbs, resolve combat, and colour narration. Validated at pack
 * construction by [ContentPack]. None of this touches the save format: every
 * effect derives from PlayerState.unlockedNodes.
 */

/** Senses the engine/Narrator react to. New senses extend this set + SenseDef. */
object Senses {
    val DEATHSIGHT = SenseId("deathsight")
    val FORESIGHT = SenseId("foresight")
    val AURASIGHT = SenseId("aurasight")
    val GREEDSENSE = SenseId("greedsense")
    val TREMORSENSE = SenseId("tremorsense")
    val SOULSIGHT = SenseId("soulsight")
}

/** What a sense reveals in combat narration — rendered generically by the Narrator. */
enum class SenseHint { EXACT_HP, DAMAGE_FORECAST, WEAKNESS, LOOT_SCENT, SPEED_READ, SOUL_COUNT }

data class SenseDef(
    val id: SenseId,
    val name: String,
    val description: String,
    val hint: SenseHint,
)

/** Function-verbs unlocked by nodes (the §16.4 "functions" node class). */
object Verbs {
    val FORAGE = VerbId("forage")
    val KINDLE = VerbId("kindle")
}

sealed interface AbilityKind {
    /** Weapon strike: attack × num/den, ignoring [defenseIgnored] of defence, with a flat crit-chance rider. */
    data class Strike(val powerNum: Int, val powerDen: Int, val defenseIgnored: Int, val critBonus: Int) : AbilityKind

    /** Restore [percentNum]/[percentDen] of effective max HP. */
    data class Heal(val percentNum: Int, val percentDen: Int) : AbilityKind

    /** A strike that heals you for [healPercent]% of the damage it deals. */
    data class LifeStrike(val powerNum: Int, val powerDen: Int, val healPercent: Int) : AbilityKind

    /** [chancePercent] to rout the foe outright — it flees, ending the fight (no loot, no XP). */
    data class Rout(val chancePercent: Int) : AbilityKind
}

data class AbilityDef(
    val id: AbilityId,
    val name: String,
    val description: String,
    val staminaCost: Int,
    /** Player-turns before it can be used again within the same fight. */
    val cooldownTurns: Int,
    val kind: AbilityKind,
)

/** What unlocking a constellation node does. The §16.4 node classes. */
sealed interface NodeEffect {
    data class MaxHp(val amount: Int) : NodeEffect
    data class Attack(val amount: Int) : NodeEffect
    data class Defense(val amount: Int) : NodeEffect
    data class MaxStamina(val amount: Int) : NodeEffect
    data class Crit(val percent: Int) : NodeEffect
    data class DamageReduction(val amount: Int) : NodeEffect
    data class XpBonus(val percent: Int) : NodeEffect
    data class GoldFind(val percent: Int) : NodeEffect
    data class StaminaEfficiency(val percent: Int) : NodeEffect
    data class CooldownReduction(val turns: Int) : NodeEffect
    data class GrantAbility(val ability: AbilityId) : NodeEffect
    data class GrantSense(val sense: SenseId) : NodeEffect
    data class GrantVerb(val verb: VerbId) : NodeEffect
}

data class ConstellationNodeDef(
    val id: NodeId,
    val name: String,
    val description: String,
    /** Which constellation this node belongs to (Body, Mind, Senses, Craft, Voice). */
    val constellation: String,
    val cost: Int,
    val prereqs: List<NodeId>,
    val effect: NodeEffect,
)
