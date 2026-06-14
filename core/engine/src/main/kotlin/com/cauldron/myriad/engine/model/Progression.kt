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
    val DOOMSIGHT = SenseId("doomsight")
    val VIGILANCE = SenseId("vigilance")
}

/** What a sense reveals in combat narration — rendered generically by the Narrator. */
enum class SenseHint {
    EXACT_HP, DAMAGE_FORECAST, WEAKNESS, LOOT_SCENT, SPEED_READ, SOUL_COUNT, DEADLIEST_MOVE, READ_GAUGE,
    RAW_ATTACK, RAW_DEFENSE, HP_FRACTION, MOVE_COUNT, GOLD_SCENT, TIER_READ, INITIATIVE, RESILIENCE,
    MENACE, FRAILTY, PERSISTENCE, OMEN,
}

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

/**
 * 20 mechanically-distinct ability kinds, all resolved statelessly in the
 * tick-ATB engine (they touch HP / stamina / gauges / end-fight — never lingering
 * buffs, so no save-format cost). The first four keep a powerDen field; the rest
 * use an implicit /10 denominator.
 */
sealed interface AbilityKind {
    /** Weapon strike: attack × num/den, ignoring [defenseIgnored] of defence, with a flat crit-chance rider. */
    data class Strike(val powerNum: Int, val powerDen: Int, val defenseIgnored: Int, val critBonus: Int) : AbilityKind

    /** Restore [percentNum]/[percentDen] of effective max HP. */
    data class Heal(val percentNum: Int, val percentDen: Int) : AbilityKind

    /** A strike that heals you for [healPercent]% of the damage it deals. */
    data class LifeStrike(val powerNum: Int, val powerDen: Int, val healPercent: Int) : AbilityKind

    /** [chancePercent] to rout the foe outright — it flees, ending the fight (no loot, no XP). */
    data class Rout(val chancePercent: Int) : AbilityKind

    /** [hits] separate strikes in one action. */
    data class MultiStrike(val powerNum: Int, val hits: Int) : AbilityKind

    /** A finisher: ×[bonusPercent]/100 damage if the foe is at or below [thresholdPercent]% HP. */
    data class Execute(val powerNum: Int, val thresholdPercent: Int, val bonusPercent: Int) : AbilityKind

    /** A huge blow that costs you [selfDamagePercent]% of current HP (never lethal). */
    data class Berserk(val powerNum: Int, val selfDamagePercent: Int) : AbilityKind

    /** A strike that always crits. */
    data class Reckless(val powerNum: Int) : AbilityKind

    /** A strike that ignores all of the foe's defence. */
    data class Precise(val powerNum: Int) : AbilityKind

    /** A strike with [flatBonus] flat damage added after scaling. */
    data class Smite(val powerNum: Int, val flatBonus: Int) : AbilityKind

    /** A heavy single blow, no crit, slow to recover from. */
    data class Hew(val powerNum: Int) : AbilityKind

    /** A cheap fast counter with a crit rider and short recovery. */
    data class Riposte(val powerNum: Int, val critBonus: Int) : AbilityKind

    /** A strike that restores stamina equal to [staminaPercent]% of the damage dealt. */
    data class Drain(val powerNum: Int, val staminaPercent: Int) : AbilityKind

    /** A strike that pushes the foe's ATB gauge back by [gaugePush]. */
    data class Stagger(val powerNum: Int, val gaugePush: Int) : AbilityKind

    /** A strike that delays the foe ([gaugePush]) and restores [staminaGain] stamina. */
    data class Sap(val powerNum: Int, val gaugePush: Int, val staminaGain: Int) : AbilityKind

    /** A small hit with a [chancePercent] chance to also rout the foe. */
    data class Terror(val powerNum: Int, val chancePercent: Int) : AbilityKind

    /** A large heal ([percentNum]% of max HP) with long recovery — you drop your guard. */
    data class Channel(val percentNum: Int) : AbilityKind

    /** Heal [healPercentNum]% of max HP and restore [staminaGain] stamina — a defensive set. */
    data class Bulwark(val healPercentNum: Int, val staminaGain: Int) : AbilityKind

    /** Catch your breath: restore [staminaGain] stamina, short recovery, no attack. */
    data class Recover(val staminaGain: Int) : AbilityKind

    /** Refund [gaugeRefund] of recovery so you act again sooner. */
    data class Quicken(val gaugeRefund: Int) : AbilityKind

    /**
     * A composable kind: run [effects] in order. This is how the roster reaches 80
     * ability kinds — status-appliers and combos built from primitives, without 60
     * more bespoke resolver branches.
     */
    data class Composite(val effects: List<AbilityEffect>) : AbilityKind
}

/** Primitives a [AbilityKind.Composite] runs in order. */
sealed interface AbilityEffect {
    /** [hits] strikes; den fixed /10. */
    data class Damage(val powerNum: Int, val defenseIgnored: Int, val critBonus: Int, val forceCrit: Boolean, val hits: Int) : AbilityEffect
    data class Heal(val percentNum: Int) : AbilityEffect
    data class LifeLeech(val percent: Int) : AbilityEffect
    data class StaminaGain(val amount: Int) : AbilityEffect
    data class GaugeRefund(val amount: Int) : AbilityEffect
    data class FoeGauge(val push: Int) : AbilityEffect
    data class SelfDamage(val percent: Int) : AbilityEffect
    data class RoutChance(val percent: Int) : AbilityEffect
    /** Inflict a debuff on the foe. */
    data class Inflict(val status: StatusKind, val magnitude: Int, val turns: Int) : AbilityEffect
    /** Grant a buff to the player. */
    data class Empower(val status: StatusKind, val magnitude: Int, val turns: Int) : AbilityEffect
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
    data class Lifesteal(val percent: Int) : NodeEffect
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
