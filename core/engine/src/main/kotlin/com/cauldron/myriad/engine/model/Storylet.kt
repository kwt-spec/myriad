package com.cauldron.myriad.engine.model

/**
 * Storylets (MASTER_PLAN §16.9) — the tap-a-choice narrative scenes that break the
 * tap-fight loop. A scene of prose offering choices; a choice may gate on a
 * requirement, roll a visible skill-check, and apply an outcome. Pure data,
 * validated at pack construction; resolved deterministically by the engine.
 */

/** Which player attribute a skill-check draws on. */
enum class Attribute { MIGHT, AGILITY, CUNNING, PERCEPTION, RESOLVE }

/** A gate that hides or disables a choice until it is met. */
sealed interface Requirement {
    /** A quality/flag at or above [value]. */
    data class FlagAtLeast(val flag: String, val value: Int) : Requirement
    data class HasItem(val item: ItemId) : Requirement
    /** An equipped weapon family (e.g. a "heavy" weapon to force a door). */
    data class EquippedFamily(val family: String) : Requirement
    data class AttributeAtLeast(val attribute: Attribute, val value: Int) : Requirement
    /** Owns a particular constellation node. */
    data class HasNode(val node: NodeId) : Requirement
}

/** A risk: success chance derives from the attribute (+level) versus [difficulty]. */
data class SkillCheck(val attribute: Attribute, val difficulty: Int)

/** One thing a choice's outcome does. Applied in order as events. */
sealed interface OutcomeEffect {
    data class Narrate(val text: String) : OutcomeEffect
    data class SetFlag(val flag: String, val delta: Int) : OutcomeEffect
    data class GiveItem(val item: ItemId) : OutcomeEffect
    data class TakeItem(val item: ItemId) : OutcomeEffect
    data class Heal(val amount: Int) : OutcomeEffect
    data class Hurt(val amount: Int) : OutcomeEffect
    data class GainGold(val amount: Int) : OutcomeEffect
    data class GainXp(val amount: Int) : OutcomeEffect
    data class StartCombat(val monster: MonsterId) : OutcomeEffect
    /** Chain into another storylet. */
    data class Goto(val storylet: StoryletId) : OutcomeEffect
    /** Leave story mode, back to exploring. */
    data object EndStory : OutcomeEffect
}

typealias Outcome = List<OutcomeEffect>

data class ChoiceDef(
    val id: ChoiceId,
    val label: String,
    val requirement: Requirement? = null,
    val check: SkillCheck? = null,
    val success: Outcome,
    val failure: Outcome? = null,
)

data class StoryletDef(
    val id: StoryletId,
    val body: String,
    val choices: List<ChoiceDef>,
)
