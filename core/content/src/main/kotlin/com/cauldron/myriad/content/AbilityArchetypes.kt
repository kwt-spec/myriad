package com.cauldron.myriad.content

import com.cauldron.myriad.engine.model.AbilityEffect
import com.cauldron.myriad.engine.model.AbilityKind
import com.cauldron.myriad.engine.model.StatusKind

/**
 * 60 composite ability archetypes — named effect-compositions built from the
 * status-effect primitives. With the engine's 20 stateless sealed kinds that
 * makes **80 ability kinds** total. Each archetype scales by tier (0-based).
 */
object AbilityArchetypes {

    const val COUNT = 60

    private fun dmg(p: Int, di: Int = 0, cb: Int = 0, fc: Boolean = false, hits: Int = 1) =
        AbilityEffect.Damage(p, di, cb, fc, hits)
    private fun bleed(m: Int, t: Int) = AbilityEffect.Inflict(StatusKind.BLEED, m, t)
    private fun stun(t: Int) = AbilityEffect.Inflict(StatusKind.STUN, 1, t)
    private fun weaken(m: Int, t: Int) = AbilityEffect.Inflict(StatusKind.WEAKEN, m, t)
    private fun sunder(m: Int, t: Int) = AbilityEffect.Inflict(StatusKind.SUNDER, m, t)
    private fun mark(m: Int, t: Int) = AbilityEffect.Inflict(StatusKind.MARK, m, t)
    private fun guard(m: Int, t: Int) = AbilityEffect.Empower(StatusKind.GUARD, m, t)
    private fun rage(m: Int, t: Int) = AbilityEffect.Empower(StatusKind.RAGE, m, t)
    private fun regen(m: Int, t: Int) = AbilityEffect.Empower(StatusKind.REGEN, m, t)
    private fun focus(m: Int, t: Int) = AbilityEffect.Empower(StatusKind.FOCUS, m, t)
    private fun haste(m: Int, t: Int) = AbilityEffect.Empower(StatusKind.HASTE, m, t)
    private fun heal(p: Int) = AbilityEffect.Heal(p)
    private fun leech(p: Int) = AbilityEffect.LifeLeech(p)
    private fun sta(a: Int) = AbilityEffect.StaminaGain(a)
    private fun push(a: Int) = AbilityEffect.FoeGauge(a)
    private fun selfHurt(p: Int) = AbilityEffect.SelfDamage(p)
    private fun rout(p: Int) = AbilityEffect.RoutChance(p)

    private class Spec(val word: String, val stamina: Int, val cd: Int, val build: (Int) -> List<AbilityEffect>)

    private val SPECS: List<Spec> = listOf(
        Spec("Bleed", 30, 2) { s -> listOf(dmg(12 + s * 2), bleed(2 + s, 3)) },
        Spec("Poison", 28, 3) { s -> listOf(dmg(8 + s), bleed(2 + s, 5)) },
        Spec("Rupture", 42, 3) { s -> listOf(dmg(18 + s * 2), bleed(3 + s, 2)) },
        Spec("Stun", 34, 3) { s -> listOf(dmg(12 + s * 2), stun(1 + s / 3)) },
        Spec("Daze", 26, 2) { s -> listOf(stun(1), push(150 + s * 20)) },
        Spec("Concuss", 40, 3) { s -> listOf(dmg(14 + s * 2), stun(2)) },
        Spec("Weaken", 24, 3) { s -> listOf(weaken(15 + s * 3, 3)) },
        Spec("Cripple", 32, 3) { s -> listOf(dmg(11 + s * 2), weaken(12 + s * 2, 3)) },
        Spec("Enfeeble", 36, 4) { s -> listOf(weaken(20 + s * 3, 3), sunder(2 + s, 3)) },
        Spec("Sunder", 32, 2) { s -> listOf(dmg(13 + s * 2), sunder(3 + s, 3)) },
        Spec("Shatterguard", 30, 3) { s -> listOf(sunder(5 + s, 4)) },
        Spec("Mark", 22, 3) { s -> listOf(mark(15 + s * 3, 3)) },
        Spec("Hex", 30, 3) { s -> listOf(mark(12 + s * 2, 3), weaken(10 + s, 3)) },
        Spec("Curse", 34, 3) { s -> listOf(dmg(10 + s), mark(14 + s * 2, 5)) },
        Spec("Guard", 22, 2) { s -> listOf(guard(2 + s, 3)) },
        Spec("Fortify", 28, 3) { s -> listOf(guard(2 + s, 3), heal(10 + s * 2)) },
        Spec("Aegis", 34, 3) { s -> listOf(guard(3 + s, 4), sta(20 + s * 3)) },
        Spec("Rage", 24, 3) { s -> listOf(rage(2 + s, 3)) },
        Spec("Bloodfury", 34, 3) { s -> listOf(rage(3 + s, 3), selfHurt(8)) },
        Spec("Warcry", 30, 3) { s -> listOf(rage(2 + s, 3), weaken(10 + s, 3)) },
        Spec("Regen", 22, 3) { s -> listOf(regen(4 + s, 4)) },
        Spec("Renew", 30, 3) { s -> listOf(regen(4 + s, 3), heal(8 + s)) },
        Spec("Focus", 22, 3) { s -> listOf(focus(10 + s * 2, 3)) },
        Spec("Sharpen", 30, 3) { s -> listOf(focus(8 + s * 2, 3), rage(1 + s, 3)) },
        Spec("Haste", 26, 3) { s -> listOf(haste(120 + s * 20, 3)) },
        Spec("Quickstep", 24, 2) { s -> listOf(haste(100 + s * 20, 2), sta(15 + s * 2)) },
        Spec("Venomstrike", 38, 3) { s -> listOf(dmg(13 + s * 2), bleed(2 + s, 3), weaken(8 + s, 2)) },
        Spec("Hamstring", 34, 3) { s -> listOf(dmg(12 + s), weaken(12 + s, 3), push(150 + s * 20)) },
        Spec("Disarm", 34, 3) { s -> listOf(dmg(12 + s), sunder(3 + s, 3), push(120 + s * 20)) },
        Spec("Lifebloom", 36, 3) { s -> listOf(dmg(12 + s), leech(40 + s * 3), regen(3 + s, 3)) },
        Spec("Vampire", 42, 4) { s -> listOf(dmg(16 + s * 2), leech(60 + s * 2), regen(3 + s, 2)) },
        Spec("Soulrend", 44, 4) { s -> listOf(dmg(18 + s * 2, di = 6), mark(12 + s, 3)) },
        Spec("Bloodrage", 38, 3) { s -> listOf(dmg(14 + s), selfHurt(6), rage(3 + s, 3)) },
        Spec("Stoneguard", 30, 4) { s -> listOf(guard(4 + s, 5)) },
        Spec("Ironhide", 32, 3) { s -> listOf(guard(2 + s, 3), regen(3 + s, 3)) },
        Spec("Warding", 32, 3) { s -> listOf(heal(14 + s * 2), guard(2 + s, 3)) },
        Spec("Smokebomb", 28, 4) { s -> listOf(stun(1), haste(120 + s * 20, 2)) },
        Spec("Terrorize", 36, 3) { s -> listOf(dmg(10 + s), rout(20 + s * 3), weaken(8 + s, 2)) },
        Spec("Onslaught", 44, 3) { s -> listOf(dmg(12 + s * 2, hits = 2), rage(2 + s, 3)) },
        Spec("Reaver", 40, 3) { s -> listOf(dmg(14 + s * 2), leech(45 + s * 2), bleed(2 + s, 3)) },
        Spec("Tempest", 46, 3) { s -> listOf(dmg(9 + s, hits = 3)) },
        Spec("Lacerate", 38, 3) { s -> listOf(dmg(13 + s), bleed(4 + s, 5)) },
        Spec("Mindspike", 40, 4) { s -> listOf(dmg(12 + s), stun(1), mark(10 + s, 3)) },
        Spec("Drainstrike", 34, 3) { s -> listOf(dmg(13 + s), sta(18 + s * 2), push(120 + s * 20)) },
        Spec("Channelguard", 44, 4) { s -> listOf(heal(28 + s * 4), guard(3 + s, 3)) },
        Spec("Rally", 34, 4) { s -> listOf(heal(16 + s * 2), rage(2 + s, 3)) },
        Spec("Berserker", 48, 4) { s -> listOf(dmg(18 + s * 2), selfHurt(12), rage(4 + s, 3)) },
        Spec("Pin", 30, 4) { s -> listOf(stun(2), push(250 + s * 30)) },
        Spec("Wither", 36, 4) { s -> listOf(weaken(18 + s * 2, 5), bleed(2 + s, 3)) },
        Spec("Anoint", 30, 3) { s -> listOf(focus(8 + s, 3), guard(2 + s, 3)) },
        Spec("Exsanguinate", 40, 4) { s -> listOf(dmg(15 + s * 2), leech(70 + s * 2)) },
        Spec("Cinderburst", 44, 3) { s -> listOf(dmg(18 + s * 2), bleed(3 + s, 2)) },
        Spec("Frostbite", 30, 3) { s -> listOf(weaken(14 + s * 2, 3), push(150 + s * 20)) },
        Spec("Shockwave", 46, 3) { s -> listOf(dmg(11 + s, hits = 2), stun(1)) },
        Spec("Doom", 40, 4) { s -> listOf(mark(16 + s * 2, 5), sunder(3 + s, 3)) },
        Spec("Sanctuary", 46, 4) { s -> listOf(heal(30 + s * 4), regen(4 + s, 3)) },
        Spec("Bloodpact", 40, 4) { s -> listOf(selfHurt(10), rage(4 + s, 3), focus(8 + s, 3)) },
        Spec("Eviscerate", 46, 4) { s -> listOf(dmg(20 + s * 2, di = 4, cb = 20), bleed(3 + s, 3)) },
        Spec("Cataclysm", 52, 5) { s -> listOf(dmg(16 + s * 2, hits = 2), stun(1), sunder(3 + s, 3)) },
        Spec("Maelstrom", 48, 4) { s -> listOf(dmg(13 + s, hits = 2), weaken(10 + s, 3)) },
    )

    init {
        require(SPECS.size == COUNT) { "expected $COUNT archetypes, have ${SPECS.size}" }
    }

    fun word(i: Int): String = SPECS[i % COUNT].word
    fun composite(i: Int, tier: Int): AbilityKind = AbilityKind.Composite(SPECS[i % COUNT].build(tier))
    fun stamina(i: Int, tier: Int): Int = SPECS[i % COUNT].stamina + tier * 3
    fun cd(i: Int): Int = SPECS[i % COUNT].cd
    fun flavor(i: Int): String = "the ${SPECS[i % COUNT].word.lowercase()} art"
}
