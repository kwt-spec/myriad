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
import com.cauldron.myriad.engine.model.Senses
import com.cauldron.myriad.engine.model.Verbs

/**
 * The five constellations (MASTER_PLAN §16.4): Body, Mind, Senses, Craft, Voice —
 * the Wanderer's universal trees. Exercises every node-effect class (stat ranks,
 * XP/gold/stamina/cooldown modifiers, ability grants, sense grants, verb grants)
 * across a deep, prereq-chained roster. Built with a small generator for the rank
 * chains so the trees stay large without losing distinct prose.
 *
 * Binding floors (test-enforced): ≥ NODE_FLOOR nodes total, all 5 constellations
 * present, every node-effect class used.
 */
object Constellations {

    const val NODE_FLOOR = 60

    // ── Senses (the perception layer) ────────────────────────────────────────
    val senses: Map<SenseId, SenseDef> = listOf(
        SenseDef(Senses.DEATHSIGHT, "Deathsight", "Read a foe's exact remaining life.", SenseHint.EXACT_HP),
        SenseDef(Senses.FORESIGHT, "Foresight", "Feel the weight of the blow before it lands.", SenseHint.DAMAGE_FORECAST),
        SenseDef(Senses.AURASIGHT, "Aurasight", "See where a foe's guard is thinnest.", SenseHint.WEAKNESS),
        SenseDef(Senses.GREEDSENSE, "Greedsense", "Smell whether a kill is worth making.", SenseHint.LOOT_SCENT),
        SenseDef(Senses.TREMORSENSE, "Tremorsense", "Judge a foe's quickness by the air it moves.", SenseHint.SPEED_READ),
        SenseDef(Senses.SOULSIGHT, "Soulsight", "Weigh the insight a death will yield.", SenseHint.SOUL_COUNT),
    ).associateBy { it.id }

    // ── Abilities ─────────────────────────────────────────────────────────────
    val SUNDER = AbilityId("sunder")
    val SECOND_WIND = AbilityId("second_wind")
    val CINDERBRAND = AbilityId("cinderbrand")
    val FOCUSED_STRIKE = AbilityId("focused_strike")
    val MENDING_TRANCE = AbilityId("mending_trance")
    val PREEMPT = AbilityId("preempt")
    val PINPOINT = AbilityId("pinpoint")
    val BLOODLETTING = AbilityId("bloodletting")
    val CAUSTIC_BRAND = AbilityId("caustic_brand")
    val INTIMIDATE = AbilityId("intimidate")
    val DREAD_HOWL = AbilityId("dread_howl")
    val RALLY_CRY = AbilityId("rally_cry")
    val WAR_CHANT = AbilityId("war_chant")
    val EXECUTE = AbilityId("execute")

    val abilities: Map<AbilityId, AbilityDef> = listOf(
        AbilityDef(SUNDER, "Sunder", "A guard-breaking blow that ignores much of the foe's armour.", 35, 2,
            AbilityKind.Strike(18, 10, defenseIgnored = 6, critBonus = 10)),
        AbilityDef(SECOND_WIND, "Second Wind", "Draw on banked warmth to close your wounds.", 45, 4,
            AbilityKind.Heal(35, 100)),
        AbilityDef(CINDERBRAND, "Cinderbrand", "A furnace-hot overhand that lands like a falling beam.", 55, 3,
            AbilityKind.Strike(30, 10, defenseIgnored = 2, critBonus = 15)),
        AbilityDef(FOCUSED_STRIKE, "Focused Strike", "A precise, economical cut with a keen edge.", 20, 1,
            AbilityKind.Strike(12, 10, defenseIgnored = 1, critBonus = 25)),
        AbilityDef(MENDING_TRANCE, "Mending Trance", "A brief, cheap moment of inward focus that knits flesh.", 25, 2,
            AbilityKind.Heal(20, 100)),
        AbilityDef(PREEMPT, "Pre-emptive Strike", "You saw it coming — strike the opening.", 30, 2,
            AbilityKind.Strike(16, 10, defenseIgnored = 3, critBonus = 35)),
        AbilityDef(PINPOINT, "Pinpoint", "Find the one seam no armour covers.", 35, 2,
            AbilityKind.Strike(15, 10, defenseIgnored = 12, critBonus = 10)),
        AbilityDef(BLOODLETTING, "Bloodletting", "A cruel cut that feeds your own warmth on the foe's.", 40, 3,
            AbilityKind.LifeStrike(17, 10, healPercent = 50)),
        AbilityDef(CAUSTIC_BRAND, "Caustic Brand", "A searing alchemical mark that eats through hide.", 45, 3,
            AbilityKind.Strike(22, 10, defenseIgnored = 8, critBonus = 10)),
        AbilityDef(INTIMIDATE, "Intimidate", "Bare your nerve; lesser things break and run.", 30, 3,
            AbilityKind.Rout(35)),
        AbilityDef(DREAD_HOWL, "Dread Howl", "A sound from somewhere old; the dark itself flinches.", 55, 5,
            AbilityKind.Rout(60)),
        AbilityDef(RALLY_CRY, "Rally Cry", "Speak yourself upright; the wounds matter less.", 50, 4,
            AbilityKind.Heal(45, 100)),
        AbilityDef(WAR_CHANT, "War Chant", "A rhythm that turns fear into a swung blade.", 35, 2,
            AbilityKind.Strike(19, 10, defenseIgnored = 2, critBonus = 20)),
        AbilityDef(EXECUTE, "Execute", "End a faltering thing before it recovers.", 50, 4,
            AbilityKind.Strike(34, 10, defenseIgnored = 10, critBonus = 20)),
    ).associateBy { it.id }

    // ── Node generation ──────────────────────────────────────────────────────
    private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")
    private val acc = LinkedHashMap<NodeId, ConstellationNodeDef>()

    private fun node(
        id: String, name: String, desc: String, tree: String, cost: Int,
        prereqs: List<NodeId>, effect: NodeEffect,
    ): NodeId {
        val nid = NodeId(id)
        acc[nid] = ConstellationNodeDef(nid, name, desc, tree, cost, prereqs, effect)
        return nid
    }

    /** A prereq-chained rank line: name I, II, … each with escalating value + distinct prose. */
    private fun chain(
        tree: String, slug: String, display: String,
        values: List<Int>, costs: List<Int>, root: NodeId?,
        prose: (rank: Int, value: Int) -> String, effect: (value: Int) -> NodeEffect,
    ): NodeId {
        var prev = root
        var last = root
        values.forEachIndexed { i, value ->
            val prereqs = listOfNotNull(prev)
            last = node("${tree.lowercase()}_${slug}_${i + 1}", "$display ${ROMAN[i]}", prose(i + 1, value), tree, costs[i], prereqs, effect(value))
            prev = last
        }
        return last!!
    }

    private fun build(): Map<NodeId, ConstellationNodeDef> {
        // ── BODY — the flesh: HP, mitigation, raw might, martial finishers ──
        val hardy = chain("Body", "hardy", "Hardy", listOf(8, 12, 18), listOf(1, 1, 2), null,
            { r, v -> "Your frame thickens against the deep cold. +$v max HP (rank $r)." }, { NodeEffect.MaxHp(it) })
        val iron = chain("Body", "iron_skin", "Iron Skin", listOf(1, 2, 3), listOf(1, 2, 2), hardy,
            { _, v -> "Old burns turn your hide toward iron. −$v damage taken." }, { NodeEffect.DamageReduction(it) })
        val brute = chain("Body", "brute_force", "Brute Force", listOf(2, 3), listOf(1, 2), null,
            { _, v -> "Every blow carries more of you behind it. +$v attack." }, { NodeEffect.Attack(it) })
        node("body_deep_lungs", "Deep Lungs", "You pace your breath for the long fight. +40 max stamina.", "Body", 1, listOf(hardy), NodeEffect.MaxStamina(40))
        node("body_bloodied_edge", "Bloodied Edge", "You find the seams in things. +12% critical chance.", "Body", 2, listOf(brute), NodeEffect.Crit(12))
        val sunderN = node("body_sunder", "Sunder", "Learn to break a guard outright.", "Body", 2, listOf(brute), NodeEffect.GrantAbility(SUNDER))
        node("body_second_wind", "Second Wind", "Learn to close your own wounds mid-fight.", "Body", 2, listOf(hardy), NodeEffect.GrantAbility(SECOND_WIND))
        node("body_cinderbrand", "Cinderbrand", "A furnace-hot finisher for the things below.", "Body", 3, listOf(sunderN), NodeEffect.GrantAbility(CINDERBRAND))
        node("body_unbroken", "Unbroken", "Nothing in the dark will put you down. +30 max HP.", "Body", 3, listOf(iron), NodeEffect.MaxHp(30))

        // ── MIND — the mover: XP, stamina economy, cooldowns, precise strikes ──
        val study = chain("Mind", "quick_study", "Quick Study", listOf(10, 15, 25), listOf(1, 2, 2), null,
            { _, v -> "You learn faster from every kill. +$v% experience." }, { NodeEffect.XpBonus(it) })
        val economy = chain("Mind", "economy", "Economy of Motion", listOf(10, 15), listOf(1, 2), null,
            { _, v -> "No wasted movement. −$v% stamina cost." }, { NodeEffect.StaminaEfficiency(it) })
        node("mind_focus", "Focus", "Hold the whole fight in your head at once. +30 max stamina.", "Mind", 1, listOf(economy), NodeEffect.MaxStamina(30))
        node("mind_quicken", "Quicken", "Your readied tricks come round sooner. −1 ability cooldown.", "Mind", 2, listOf(economy), NodeEffect.CooldownReduction(1))
        node("mind_keen_focus", "Keen Focus", "A clear mind finds the killing angle. +10% critical chance.", "Mind", 2, listOf(study), NodeEffect.Crit(10))
        node("mind_focused_strike", "Focused Strike", "A precise, cheap cut you can repeat.", "Mind", 2, listOf(economy), NodeEffect.GrantAbility(FOCUSED_STRIKE))
        node("mind_mending_trance", "Mending Trance", "A brief inward focus that knits flesh.", "Mind", 2, listOf(study), NodeEffect.GrantAbility(MENDING_TRANCE))
        node("mind_overclock", "Overclock", "Drive your tricks relentlessly. −1 ability cooldown.", "Mind", 3, listOf(node("mind_quicken_b", "Tempo", "You settle into a fighting rhythm. −5% stamina cost.", "Mind", 2, listOf(economy), NodeEffect.StaminaEfficiency(5))), NodeEffect.CooldownReduction(1))
        node("mind_mastermind", "Mastermind", "Every foe is a lesson paid in ember. +25% experience.", "Mind", 3, listOf(study), NodeEffect.XpBonus(25))

        // ── SENSES — the perceiver: the sense layer + opportunist strikes ──
        node("senses_deathsight", "Deathsight", "Read a foe's exact remaining life.", "Senses", 1, emptyList(), NodeEffect.GrantSense(Senses.DEATHSIGHT))
        val fore = node("senses_foresight", "Foresight", "Feel the weight of a blow before it lands.", "Senses", 2, listOf(NodeId("senses_deathsight")), NodeEffect.GrantSense(Senses.FORESIGHT))
        node("senses_aurasight", "Aurasight", "See where a guard is thinnest.", "Senses", 2, listOf(NodeId("senses_deathsight")), NodeEffect.GrantSense(Senses.AURASIGHT))
        node("senses_tremorsense", "Tremorsense", "Judge a foe's quickness by the air it moves.", "Senses", 2, listOf(fore), NodeEffect.GrantSense(Senses.TREMORSENSE))
        node("senses_greedsense", "Greedsense", "Smell whether a kill is worth making.", "Senses", 1, emptyList(), NodeEffect.GrantSense(Senses.GREEDSENSE))
        node("senses_soulsight", "Soulsight", "Weigh the insight a death will yield.", "Senses", 2, listOf(fore), NodeEffect.GrantSense(Senses.SOULSIGHT))
        val edge = chain("Senses", "sharp_eyes", "Sharp Eyes", listOf(8, 12), listOf(1, 2), null,
            { _, v -> "Nothing the firelight touches escapes you. +$v% critical chance." }, { NodeEffect.Crit(it) })
        node("senses_preempt", "Pre-emptive Strike", "Strike the opening you foresaw.", "Senses", 2, listOf(fore), NodeEffect.GrantAbility(PREEMPT))
        node("senses_pinpoint", "Pinpoint", "Find the one seam no armour covers.", "Senses", 2, listOf(edge), NodeEffect.GrantAbility(PINPOINT))
        node("senses_evasion", "Evasion", "What you see coming, you can slip. −2 damage taken.", "Senses", 3, listOf(edge), NodeEffect.DamageReduction(2))
        node("senses_execute", "Execute", "See the moment a thing falters, and end it.", "Senses", 3, listOf(node("senses_eyes_iii", "Sharp Eyes III", "Your eye misses nothing at all. +14% critical chance.", "Senses", 2, listOf(edge), NodeEffect.Crit(14))), NodeEffect.GrantAbility(EXECUTE))

        // ── CRAFT — the provider: gold, forage verbs, life-feeding strikes ──
        val greed = chain("Craft", "scavenger", "Scavenger", listOf(15, 25, 40), listOf(1, 2, 2), null,
            { _, v -> "You leave nothing of worth behind. +$v% gold found." }, { NodeEffect.GoldFind(it) })
        val forageN = node("craft_forage", "Forage", "Learn to scrape warmth from the deep places without a camp.", "Craft", 1, emptyList(), NodeEffect.GrantVerb(Verbs.FORAGE))
        node("craft_kindle", "Kindle", "Coax a real flame from almost nothing.", "Craft", 2, listOf(forageN), NodeEffect.GrantVerb(Verbs.KINDLE))
        node("craft_toughness", "Field Toughness", "A scavenger's hardiness. +14 max HP.", "Craft", 1, emptyList(), NodeEffect.MaxHp(14))
        node("craft_bloodletting", "Bloodletting", "A cut that feeds your warmth on the foe's.", "Craft", 2, listOf(node("craft_butcher", "Butcher", "You know exactly where to cut. +2 attack.", "Craft", 1, emptyList(), NodeEffect.Attack(2))), NodeEffect.GrantAbility(BLOODLETTING))
        node("craft_caustic", "Caustic Brand", "Brew a searing mark that eats through hide.", "Craft", 3, listOf(greed), NodeEffect.GrantAbility(CAUSTIC_BRAND))
        node("craft_efficiency", "Make Do", "Waste nothing, not even effort. −10% stamina cost.", "Craft", 2, listOf(greed), NodeEffect.StaminaEfficiency(10))
        node("craft_hoarder", "Hoarder's Eye", "The richest caches all but glow. +30% gold found.", "Craft", 3, listOf(greed), NodeEffect.GoldFind(30))

        // ── VOICE — the breaker: rout the weak, rally the self, chant to war ──
        val intimidateN = node("voice_intimidate", "Intimidate", "Bare your nerve; lesser things break and run.", "Voice", 1, emptyList(), NodeEffect.GrantAbility(INTIMIDATE))
        node("voice_dread_howl", "Dread Howl", "A sound from somewhere old; even the dark flinches.", "Voice", 3, listOf(intimidateN), NodeEffect.GrantAbility(DREAD_HOWL))
        node("voice_rally", "Rally Cry", "Speak yourself upright; the wounds matter less.", "Voice", 2, listOf(intimidateN), NodeEffect.GrantAbility(RALLY_CRY))
        node("voice_war_chant", "War Chant", "Turn your own fear into a swung blade.", "Voice", 2, listOf(intimidateN), NodeEffect.GrantAbility(WAR_CHANT))
        val presence = chain("Voice", "presence", "Presence", listOf(2, 3), listOf(1, 2), null,
            { _, v -> "You take up more of the room than your body should. +$v attack." }, { NodeEffect.Attack(it) })
        node("voice_unflinching", "Unflinching", "You do not give ground, and it shows. −1 damage taken.", "Voice", 1, emptyList(), NodeEffect.DamageReduction(1))
        node("voice_inspire", "Inspire", "You narrate your own legend, and learn from it. +15% experience.", "Voice", 2, listOf(presence), NodeEffect.XpBonus(15))
        node("voice_commanding", "Commanding", "A voice that expects to be obeyed. +12% critical chance.", "Voice", 2, listOf(presence), NodeEffect.Crit(12))
        node("voice_warlord", "Warlord", "They follow the loudest certainty in the dark. +20 max HP.", "Voice", 3, listOf(presence), NodeEffect.MaxHp(20))

        return acc
    }

    val nodes: Map<NodeId, ConstellationNodeDef> = build()

    // Legacy ids kept for the M1c tests that named specific Body nodes.
    val HARDY_1 = NodeId("body_hardy_1")
    val HARDY_2 = NodeId("body_hardy_2")
    val BRUTE_FORCE_1 = NodeId("body_brute_force_1")
    val UNBROKEN = NodeId("body_unbroken")
}
