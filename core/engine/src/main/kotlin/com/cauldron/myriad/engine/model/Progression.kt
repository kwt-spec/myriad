package com.cauldron.myriad.engine.model

/**
 * Progression content (MASTER_PLAN §16.4): abilities, constellation nodes, and
 * senses. All pure data — the engine reads these to derive stats, grant verbs,
 * and resolve combat. Validated at pack construction by [ContentPack].
 */

/** Well-known senses the Narrator reacts to. New senses extend this set. */
object Senses {
    val DEATHSIGHT = SenseId("deathsight")
}

sealed interface AbilityKind {
    /** A weapon strike: attack × num/den, ignoring [defenseIgnored] of the target's defense, with a flat crit-chance rider. */
    data class Strike(val powerNum: Int, val powerDen: Int, val defenseIgnored: Int, val critBonus: Int) : AbilityKind

    /** Restore [percentNum]/[percentDen] of effective max HP. */
    data class Heal(val percentNum: Int, val percentDen: Int) : AbilityKind
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

/** What unlocking a constellation node does. The four §16.4 node classes. */
sealed interface NodeEffect {
    data class MaxHp(val amount: Int) : NodeEffect
    data class Attack(val amount: Int) : NodeEffect
    data class Defense(val amount: Int) : NodeEffect
    data class MaxStamina(val amount: Int) : NodeEffect
    data class Crit(val percent: Int) : NodeEffect
    data class DamageReduction(val amount: Int) : NodeEffect
    data class GrantAbility(val ability: AbilityId) : NodeEffect
    data class GrantSense(val sense: SenseId) : NodeEffect
}

data class ConstellationNodeDef(
    val id: NodeId,
    val name: String,
    val description: String,
    /** Which constellation this node belongs to (Body, Mind, …). */
    val constellation: String,
    val cost: Int,
    val prereqs: List<NodeId>,
    val effect: NodeEffect,
)
