package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MoveDef
import com.cauldron.myriad.engine.model.MoveId
import com.cauldron.myriad.engine.model.PlayerState
import com.cauldron.myriad.engine.model.RoomId
import com.cauldron.myriad.engine.model.RoomState
import com.cauldron.myriad.engine.model.MeterId
import com.cauldron.myriad.engine.model.appendFeed
import com.cauldron.myriad.engine.model.meterFor
import com.cauldron.myriad.engine.model.roomStateFor
import com.cauldron.myriad.engine.rng.Dice
import com.cauldron.myriad.engine.rng.RngState
import com.cauldron.myriad.engine.rng.RngStream

data class StepResult(val state: GameState, val events: List<Event>)

/**
 * The deterministic rules core. Stateless apart from the content pack:
 * same state + same action always produce the same events and next state.
 * All rules math is integer-only with explicit clamps (MASTER_PLAN §9).
 *
 * Combat is tick-ATB (MASTER_PLAN M1a): gauges fill per tick, the player acts
 * at full gauge, monsters execute telegraphed intents when their gauge wraps.
 * Time advances *inside* one resolution — between two player choices, any
 * number of monster actions may fire. Events carry every rolled outcome and a
 * final CombatTicked sync, so reduce alone reconstructs the exact state.
 */
class Engine(val content: ContentPack) {

    fun newGame(seed: Long, playerName: String): GameState {
        val rooms = content.rooms.mapValues { (_, def) ->
            RoomState(monsterHp = def.monster?.let { content.monsters.getValue(it).maxHp })
        }
        val state = GameState(
            seed = seed,
            turn = 0,
            contentVersion = content.version,
            rng = RngState.seeded(seed),
            player = PlayerState(
                name = playerName,
                hp = STARTING_HP,
                maxHp = STARTING_HP,
                baseAttack = STARTING_ATTACK,
                baseDefense = STARTING_DEFENSE,
                gold = 0,
                inventory = emptyList(),
                equipped = null,
            ),
            currentRoom = content.startRoom,
            rooms = rooms,
            mode = Mode.Exploring,
            meters = content.meters.mapValues { (_, def) -> def.start },
        )
        return state.appendFeed(
            listOf(
                FeedKind.SYSTEM to "Seed $seed",
                FeedKind.NARRATION to content.intro,
                FeedKind.NARRATION to Narrator.describeRoom(state, content),
            )
        )
    }

    /**
     * Everything the player may do right now. Doubles as the softlock oracle:
     * empty iff the state is terminal (Dead/Victory) — the Gauntlet asserts this.
     * In combat, Brace and Flee are always legal regardless of stamina, so the
     * oracle holds by construction.
     */
    fun legalActions(state: GameState): List<Action> = when (val mode = state.mode) {
        Mode.Dead, Mode.Victory -> emptyList()
        is Mode.Combat -> buildList {
            if (mode.playerStamina >= staminaCost(state, STAMINA_QUICK)) add(Action.QuickStrike)
            if (mode.playerStamina >= staminaCost(state, STAMINA_HEAVY)) add(Action.HeavyStrike)
            for (ability in unlockedAbilities(state)) {
                val ready = (mode.abilityCooldowns[ability.id] ?: 0) == 0
                if (ready && mode.playerStamina >= staminaCost(state, ability.staminaCost)) add(Action.UseAbility(ability.id))
            }
            add(Action.Brace)
            add(Action.Flee)
        }
        Mode.Exploring -> buildList {
            add(Action.Look)
            val room = content.rooms.getValue(state.currentRoom)
            val roomState = state.roomStateFor(state.currentRoom, content)
            if (room.haven && content.meters.isNotEmpty()) add(Action.Camp)
            // Function-verbs: Forage/Kindle warm you without a full camp, where meters can rise.
            if (content.meters.isNotEmpty() && content.meters.keys.any { (state.meters[it] ?: 0) < content.meters.getValue(it).cap }) {
                for (verb in unlockedVerbs(state)) add(Action.UseVerb(verb))
            }
            if (room.hiddenItem != null && !roomState.searched) add(Action.Search)
            for (item in roomState.itemsOnFloor) add(Action.Take(item))
            for (item in state.player.inventory) {
                if (content.items.getValue(item).isEquippable && state.player.equipped != item) {
                    add(Action.Equip(item))
                }
            }
            for (exit in room.exits) add(Action.Move(exit.to))
        }
    }

    /**
     * Progression meta-actions are validated directly, not via legalActions —
     * legalActions stays the combat/exploring verb set AND the softlock oracle.
     */
    private fun isPlayable(state: GameState, action: Action): Boolean = when (action) {
        is Action.UnlockNode -> canUnlock(state, action.node)
        Action.Respec -> canRespec(state)
        else -> action in legalActions(state)
    }

    fun step(state: GameState, action: Action): StepResult {
        require(isPlayable(state, action)) {
            "Illegal action $action in mode ${state.mode} at ${state.currentRoom.value}"
        }
        val dice = Dice(state.rng)
        val actionEvents = resolve(state, action, dice)
        val runEnded = actionEvents.any { it is Event.PlayerDied || it is Event.GameWon }
        val events = if (runEnded) actionEvents else actionEvents + survivalTick(state, action, actionEvents)
        var next = state.copy(rng = dice.snapshot(), turn = state.turn + 1)
        for (event in events) next = reduce(next, event)
        val feedEntries = events.mapNotNull { event ->
            val text = Narrator.narrate(event, next, content)
            if (text.isBlank()) null else Narrator.kindOf(event) to text
        }
        return StepResult(state = next.appendFeed(feedEntries), events = events)
    }

    /** Sentinel- and tombstone-tolerant intent lookup (MASTER_PLAN §10: ids never break saves). */
    fun moveFor(monster: MonsterDef, id: MoveId): MoveDef =
        monster.moves.firstOrNull { it.id == id } ?: monster.moves.first()

    private fun resolve(state: GameState, action: Action, dice: Dice): List<Event> = when (action) {
        Action.Look -> listOf(Event.LookedAround(state.currentRoom))

        Action.Search -> {
            val room = content.rooms.getValue(state.currentRoom)
            listOf(Event.ItemFound(checkNotNull(room.hiddenItem)))
        }

        Action.Camp -> listOf(
            Event.Camped(content.meters.mapValues { (_, def) -> def.cap })
        )

        is Action.Take -> listOf(Event.ItemTaken(action.item))

        is Action.Equip -> listOf(Event.Equipped(action.item))

        is Action.Move -> buildList {
            add(Event.MovedTo(action.to))
            val dest = content.rooms.getValue(action.to)
            val destState = state.roomStateFor(action.to, content)
            val monsterAlive = dest.monster != null && (destState.monsterHp ?: 0) > 0
            when {
                monsterAlive -> {
                    val monsterId = checkNotNull(dest.monster)
                    add(Event.CombatStarted(monsterId))
                    val intent = drawIntent(content.monsters.getValue(monsterId), dice)
                    add(Event.MonsterIntentDrawn(monsterId, intent.id))
                }
                dest.isGoal -> add(Event.GameWon)
            }
        }

        Action.QuickStrike -> resolveCombat(state, dice, CombatChoice.Quick)
        Action.HeavyStrike -> resolveCombat(state, dice, CombatChoice.Heavy)
        Action.Brace -> resolveCombat(state, dice, CombatChoice.Brace)
        Action.Flee -> resolveCombat(state, dice, CombatChoice.Flee)
        is Action.UseAbility -> resolveCombat(state, dice, CombatChoice.Ability(content.abilities.getValue(action.ability)))

        is Action.UseVerb -> {
            // Forage/Kindle scrape warmth back without a full camp.
            val gain = if (action.verb == com.cauldron.myriad.engine.model.Verbs.KINDLE) FORAGE_KINDLE else FORAGE_GAIN
            val values = content.meters.mapValues { (id, def) ->
                ((state.meterFor(id, content)) + gain).coerceAtMost(def.cap)
            }
            listOf(Event.Foraged(action.verb, values))
        }

        is Action.UnlockNode -> listOf(Event.NodeUnlocked(action.node))
        Action.Respec -> {
            val refunded = state.player.unlockedNodes.sumOf { content.nodes.getValue(it).cost }
            listOf(Event.Respecced(refunded, respecCost(state)))
        }
    }

    /**
     * The Ember-age survival clock: every action burns the meters; empty meters
     * draw blood. Camp is restful — it restores instead of burning. Pure math,
     * no dice; appended after the action's own events. If those events already
     * ended the run (death/victory), time does not get a second bite.
     */
    private fun survivalTick(state: GameState, action: Action, actionEvents: List<Event>): List<Event> {
        if (content.meters.isEmpty()) return emptyList()
        // Survival meters track WORLD time, not combat time — combat has its own
        // resource (stamina), and a long deep fight should not freeze you. Resting
        // and menu meta-actions also do not burn the clock.
        val burnsClock = when (action) {
            Action.Look, Action.Search, is Action.Move, is Action.Take, is Action.Equip -> true
            else -> false
        }
        if (!burnsClock) return emptyList()

        val values = mutableMapOf<MeterId, Int>()
        var chill = 0L
        for (def in content.meters.values) {
            val burned = (state.meterFor(def.id, content) - def.burnPerAction).coerceAtLeast(0)
            values[def.id] = burned
            if (burned == 0) chill += def.emptyDamagePerAction
        }
        if (values.isEmpty()) return emptyList()

        val damage = chill.coerceAtMost(DAMAGE_CAP.toLong()).toInt()
        // Cold death must respect wounds already taken this turn, or hp could
        // floor at zero while the mode stays Combat.
        val hpAfterAction = state.player.hp.toLong() -
            actionEvents.filterIsInstance<Event.MonsterStruckPlayer>().sumOf { it.damage.toLong() }
        return buildList {
            add(Event.MetersTicked(values, damage))
            if (damage > 0 && hpAfterAction - damage <= 0) add(Event.PlayerDied)
        }
    }

    private sealed interface CombatChoice {
        data object Quick : CombatChoice
        data object Heavy : CombatChoice
        data object Brace : CombatChoice
        data object Flee : CombatChoice
        data class Ability(val def: com.cauldron.myriad.engine.model.AbilityDef) : CombatChoice
    }

    /** The kill sequence shared by basic strikes and ability strikes. */
    private fun MutableList<Event>.addSlain(state: GameState, monsterDef: MonsterDef, dice: Dice) {
        val baseGold = dice.roll(RngStream.LOOT, monsterDef.goldDrop)
        val gold = (baseGold.toLong() * (100 + goldFind(state)) / 100).toInt()
        add(Event.MonsterSlain(monsterDef.id, gold))
        rollLoot(monsterDef, dice)?.let { add(Event.ItemDropped(monsterDef.id, it)) }
        val gained = xpFor(monsterDef) * (100 + xpBonus(state)) / 100
        add(Event.XpGained(gained))
        var total = state.player.xp + gained
        var level = state.player.level
        while (level < LEVEL_CAP && total >= xpToReach(level + 1)) {
            level++
            add(Event.LeveledUp(level, LEVEL_HP_GAIN, LEVEL_ATTACK_GAIN, LEVEL_DEFENSE_GAIN, LEVEL_SKILL_POINTS))
        }
        if (content.rooms.getValue(state.currentRoom).isGoal) add(Event.GameWon)
    }

    private fun playerCrit(state: GameState, dice: Dice, baseCrit: Boolean, abilityBonus: Int = 0): Boolean {
        if (baseCrit) return true
        val chance = critBonus(state) + abilityBonus
        return chance > 0 && dice.chance(RngStream.COMBAT, chance)
    }

    private fun resolveCombat(state: GameState, dice: Dice, choice: CombatChoice): List<Event> = buildList {
        val mode = state.mode as Mode.Combat
        require(mode.playerGauge >= Mode.Combat.GAUGE_MAX) {
            "player acted with unfilled gauge ${mode.playerGauge}"
        }
        val monsterDef = content.monsters.getValue(mode.monster)
        var monsterHp = state.roomStateFor(state.currentRoom, content).monsterHp ?: 0
        var playerHp = state.player.hp
        val maxHp = effectiveMaxHp(state)
        val maxStamina = effectiveMaxStamina(state)
        var stamina = mode.playerStamina
        var braced = mode.braced
        var intent = moveFor(monsterDef, mode.monsterIntent)
        var playerGauge: Int
        var monsterGauge = mode.monsterGauge
        // Every player action ticks cooldowns down by one before any new one is set.
        var cooldowns = mode.abilityCooldowns.mapValues { (_, t) -> (t - 1).coerceAtLeast(0) }
            .filterValues { it > 0 }

        // Active status effects (read from the pre-action snapshot — debuffs/buffs
        // applied THIS turn take effect on subsequent turns). New ones append here.
        val statuses = mode.statuses.toMutableList()
        fun foeStatus(k: com.cauldron.myriad.engine.model.StatusKind) =
            statuses.filter { it.onFoe && it.kind == k && it.turnsLeft > 0 }.sumOf { it.magnitude }
        fun selfStatus(k: com.cauldron.myriad.engine.model.StatusKind) =
            statuses.filter { !it.onFoe && it.kind == k && it.turnsLeft > 0 }.sumOf { it.magnitude }
        val rageBonus = selfStatus(com.cauldron.myriad.engine.model.StatusKind.RAGE)
        val focusBonus = selfStatus(com.cauldron.myriad.engine.model.StatusKind.FOCUS)
        val markBonus = foeStatus(com.cauldron.myriad.engine.model.StatusKind.MARK)
        val sunderAmt = foeStatus(com.cauldron.myriad.engine.model.StatusKind.SUNDER)
        val weakenPct = foeStatus(com.cauldron.myriad.engine.model.StatusKind.WEAKEN).coerceAtMost(90)
        val guardAmt = selfStatus(com.cauldron.myriad.engine.model.StatusKind.GUARD)
        val regenAmt = selfStatus(com.cauldron.myriad.engine.model.StatusKind.REGEN)
        val bleedAmt = foeStatus(com.cauldron.myriad.engine.model.StatusKind.BLEED)
        val hasteAmt = selfStatus(com.cauldron.myriad.engine.model.StatusKind.HASTE)
        val foeStunned = statuses.any { it.onFoe && it.kind == com.cauldron.myriad.engine.model.StatusKind.STUN && it.turnsLeft > 0 }
        val playerStrikeAttack = playerAttack(state) + rageBonus
        val foeStrikeDefense = { extraIgnore: Int -> (monsterDef.defense - sunderAmt - extraIgnore).coerceAtLeast(0) }

        when (choice) {
            CombatChoice.Quick, CombatChoice.Heavy -> {
                val heavy = choice == CombatChoice.Heavy
                stamina -= staminaCost(state, if (heavy) STAMINA_HEAVY else STAMINA_QUICK)
                val roll = dice.roll(RngStream.COMBAT, 1..6)
                val crit = playerCrit(state, dice, baseCrit = roll == 6 || (heavy && roll == 5), abilityBonus = focusBonus + markBonus)
                val damage = scaledDamage(
                    attack = playerStrikeAttack,
                    powerNum = if (heavy) HEAVY_POWER_NUM else QUICK_POWER_NUM,
                    powerDen = POWER_DEN,
                    roll = roll,
                    defense = foeStrikeDefense(0),
                    crit = crit,
                    halved = false,
                )
                add(Event.PlayerStruckMonster(mode.monster, damage, crit, heavy))
                monsterHp -= damage
                if (monsterHp <= 0) {
                    addSlain(state, monsterDef, dice)
                    return@buildList
                }
                val steal = (damage.toLong() * lifesteal(state) / 100).toInt()
                    .coerceAtMost(maxHp - playerHp).coerceAtLeast(0)
                if (steal > 0) { playerHp += steal; add(Event.PlayerHealed(steal)) }
                playerGauge = Mode.Combat.GAUGE_MAX - if (heavy) RECOVERY_HEAVY else RECOVERY_QUICK
            }

            CombatChoice.Brace -> {
                add(Event.PlayerBraced)
                braced = true
                stamina = (stamina + STAMINA_BRACE_RESTORE).coerceAtMost(maxStamina)
                playerGauge = Mode.Combat.GAUGE_MAX - RECOVERY_BRACE
            }

            CombatChoice.Flee -> {
                if (dice.chance(RngStream.COMBAT, FLEE_CHANCE_PERCENT)) {
                    add(Event.FleeSucceeded(state.lastRoom ?: content.startRoom))
                    return@buildList
                }
                add(Event.FleeFailed(mode.monster))
                playerGauge = Mode.Combat.GAUGE_MAX - RECOVERY_FLEE_FAIL
            }

            is CombatChoice.Ability -> {
                val ability = choice.def
                stamina -= staminaCost(state, ability.staminaCost)
                add(Event.AbilityUsed(ability.id))
                var recovery = RECOVERY_ABILITY
                var lastHit = 0

                // One scaled strike (den fixed /10 for the new kinds). Returns true
                // if it killed — caller must then `return@buildList`. Mutates the
                // captured combat vars; `add`/`addSlain` resolve to the buildList list.
                fun strike(powNum: Int, defIgnore: Int, critBonus: Int, forceCrit: Boolean): Boolean {
                    val roll = dice.roll(RngStream.COMBAT, 1..6)
                    val crit = forceCrit || playerCrit(state, dice, baseCrit = roll == 6, abilityBonus = critBonus + focusBonus + markBonus)
                    val dmg = scaledDamage(
                        playerStrikeAttack, powNum, 10, roll,
                        foeStrikeDefense(defIgnore), crit, false,
                    )
                    add(Event.PlayerStruckMonster(mode.monster, dmg, crit, heavy = true))
                    monsterHp -= dmg
                    lastHit = dmg
                    if (monsterHp <= 0) { addSlain(state, monsterDef, dice); return true }
                    return false
                }
                fun healBy(amount: Int) {
                    val h = amount.coerceAtMost(maxHp - playerHp).coerceAtLeast(0)
                    if (h > 0) { playerHp += h; add(Event.PlayerHealed(h)) }
                }
                fun gainStamina(amount: Int) { stamina = (stamina + amount).coerceAtMost(maxStamina) }

                when (val kind = ability.kind) {
                    is com.cauldron.myriad.engine.model.AbilityKind.Strike ->
                        if (strike(kind.powerNum, kind.defenseIgnored, kind.critBonus, false)) return@buildList
                    is com.cauldron.myriad.engine.model.AbilityKind.Precise ->
                        if (strike(kind.powerNum, 99_999, 0, false)) return@buildList
                    is com.cauldron.myriad.engine.model.AbilityKind.Reckless ->
                        if (strike(kind.powerNum, 0, 0, true)) return@buildList
                    is com.cauldron.myriad.engine.model.AbilityKind.Riposte -> {
                        if (strike(kind.powerNum, 2, kind.critBonus, false)) return@buildList
                        recovery = RECOVERY_RIPOSTE
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Hew -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        recovery = RECOVERY_HEW
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.MultiStrike ->
                        repeat(kind.hits) { if (strike(kind.powerNum, 0, 0, false)) return@buildList }
                    is com.cauldron.myriad.engine.model.AbilityKind.Smite -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        val bonus = kind.flatBonus.coerceAtLeast(0)
                        if (bonus > 0) {
                            add(Event.PlayerStruckMonster(mode.monster, bonus, false, true))
                            monsterHp -= bonus
                            if (monsterHp <= 0) { addSlain(state, monsterDef, dice); return@buildList }
                        }
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Execute -> {
                        val threshold = monsterDef.maxHp * kind.thresholdPercent / 100
                        val pow = if (monsterHp <= threshold) kind.powerNum * kind.bonusPercent / 100 else kind.powerNum
                        if (strike(pow, 0, 0, false)) return@buildList
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Berserk -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        val self = (playerHp.toLong() * kind.selfDamagePercent / 100).toInt()
                            .coerceAtMost(playerHp - 1).coerceAtLeast(0)
                        if (self > 0) { add(Event.PlayerSelfHarm(self)); playerHp -= self }
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.LifeStrike -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        healBy((lastHit.toLong() * kind.healPercent / 100).toInt())
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Drain -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        gainStamina(lastHit * kind.staminaPercent / 100)
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Stagger -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        monsterGauge = (monsterGauge - kind.gaugePush).coerceAtLeast(0)
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Sap -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        monsterGauge = (monsterGauge - kind.gaugePush).coerceAtLeast(0)
                        gainStamina(kind.staminaGain)
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Terror -> {
                        if (strike(kind.powerNum, 0, 0, false)) return@buildList
                        if (dice.chance(RngStream.COMBAT, kind.chancePercent)) {
                            add(Event.MonsterRouted(mode.monster)); return@buildList
                        }
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Heal ->
                        healBy((maxHp.toLong() * kind.percentNum / kind.percentDen).toInt())
                    is com.cauldron.myriad.engine.model.AbilityKind.Channel -> {
                        healBy(maxHp * kind.percentNum / 100)
                        recovery = RECOVERY_CHANNEL
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Bulwark -> {
                        healBy(maxHp * kind.healPercentNum / 100)
                        gainStamina(kind.staminaGain)
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Recover -> {
                        gainStamina(kind.staminaGain)
                        recovery = RECOVERY_RECOVER
                    }
                    is com.cauldron.myriad.engine.model.AbilityKind.Quicken ->
                        recovery = (RECOVERY_ABILITY - kind.gaugeRefund).coerceAtLeast(200)
                    is com.cauldron.myriad.engine.model.AbilityKind.Rout ->
                        if (dice.chance(RngStream.COMBAT, kind.chancePercent)) {
                            add(Event.MonsterRouted(mode.monster)); return@buildList
                        }
                    is com.cauldron.myriad.engine.model.AbilityKind.Composite -> {
                        for (effect in kind.effects) {
                            when (effect) {
                                is com.cauldron.myriad.engine.model.AbilityEffect.Damage ->
                                    repeat(effect.hits.coerceAtLeast(1)) {
                                        if (strike(effect.powerNum, effect.defenseIgnored, effect.critBonus, effect.forceCrit)) return@buildList
                                    }
                                is com.cauldron.myriad.engine.model.AbilityEffect.Heal ->
                                    healBy(maxHp * effect.percentNum / 100)
                                is com.cauldron.myriad.engine.model.AbilityEffect.LifeLeech ->
                                    healBy(lastHit * effect.percent / 100)
                                is com.cauldron.myriad.engine.model.AbilityEffect.StaminaGain -> gainStamina(effect.amount)
                                is com.cauldron.myriad.engine.model.AbilityEffect.GaugeRefund ->
                                    recovery = (recovery - effect.amount).coerceAtLeast(200)
                                is com.cauldron.myriad.engine.model.AbilityEffect.FoeGauge ->
                                    monsterGauge = (monsterGauge - effect.push).coerceAtLeast(0)
                                is com.cauldron.myriad.engine.model.AbilityEffect.SelfDamage -> {
                                    val self = (playerHp * effect.percent / 100).coerceAtMost(playerHp - 1).coerceAtLeast(0)
                                    if (self > 0) { add(Event.PlayerSelfHarm(self)); playerHp -= self }
                                }
                                is com.cauldron.myriad.engine.model.AbilityEffect.RoutChance ->
                                    if (dice.chance(RngStream.COMBAT, effect.percent)) { add(Event.MonsterRouted(mode.monster)); return@buildList }
                                is com.cauldron.myriad.engine.model.AbilityEffect.Inflict ->
                                    statuses += com.cauldron.myriad.engine.model.ActiveStatus(effect.status, effect.magnitude, effect.turns, onFoe = true)
                                is com.cauldron.myriad.engine.model.AbilityEffect.Empower ->
                                    statuses += com.cauldron.myriad.engine.model.ActiveStatus(effect.status, effect.magnitude, effect.turns, onFoe = false)
                            }
                        }
                    }
                }
                val cd = (ability.cooldownTurns - cooldownReduction(state)).coerceAtLeast(if (ability.cooldownTurns > 0) 1 else 0)
                if (cd > 0) cooldowns = cooldowns + (ability.id to cd)
                playerGauge = Mode.Combat.GAUGE_MAX - recovery
            }
        }

        // Haste: a buffed player recovers faster (gauge closer to ready).
        if (hasteAmt > 0) playerGauge = (playerGauge + hasteAmt).coerceAtMost(Mode.Combat.GAUGE_MAX)

        // Advance time until the player is ready again. The monster's gauge may
        // wrap multiple times — each wrap executes the telegraphed intent and
        // draws the next one. WEAKEN cuts its attack, GUARD softens the blow, and
        // STUN makes it forfeit its actions this turn.
        val reduction = damageReduction(state) + guardAmt
        val foeAttack = (monsterDef.attack.toLong() * (100 - weakenPct) / 100).toInt().coerceAtLeast(1)
        var guard = 0
        while (playerGauge < Mode.Combat.GAUGE_MAX) {
            check(++guard <= ADVANCE_GUARD) { "ATB advancement runaway (speeds: player $PLAYER_SPEED, monster ${monsterDef.speed})" }
            playerGauge += PLAYER_SPEED
            monsterGauge += monsterDef.speed
            while (monsterGauge >= Mode.Combat.GAUGE_MAX) {
                monsterGauge -= Mode.Combat.GAUGE_MAX
                if (foeStunned) continue // stunned: the foe forfeits this action
                val roll = dice.roll(RngStream.COMBAT, 1..6)
                val crit = roll == 6
                val raw = scaledDamage(
                    attack = foeAttack,
                    powerNum = intent.powerNum,
                    powerDen = intent.powerDen,
                    roll = roll,
                    defense = playerDefense(state),
                    crit = crit,
                    halved = braced,
                )
                val damage = (raw - reduction).coerceAtLeast(1)
                add(Event.MonsterStruckPlayer(mode.monster, intent.id, damage, crit, braced))
                braced = false
                playerHp -= damage
                if (playerHp <= 0) {
                    add(Event.PlayerDied)
                    return@buildList
                }
                intent = drawIntent(monsterDef, dice)
                add(Event.MonsterIntentDrawn(mode.monster, intent.id))
            }
        }

        // End-of-turn status ticks: REGEN heals, BLEED bites (can finish a foe),
        // then every status loses a turn and the spent ones fall away.
        if (regenAmt > 0 && playerHp < maxHp) {
            val h = regenAmt.coerceAtMost(maxHp - playerHp)
            playerHp += h
            add(Event.PlayerHealed(h))
        }
        if (bleedAmt > 0) {
            add(Event.MonsterBled(mode.monster, bleedAmt))
            monsterHp -= bleedAmt
            if (monsterHp <= 0) { addSlain(state, monsterDef, dice); return@buildList }
        }
        val tickedStatuses = statuses.map { it.copy(turnsLeft = it.turnsLeft - 1) }.filter { it.turnsLeft > 0 }

        add(
            Event.CombatTicked(
                playerGauge = playerGauge.coerceAtMost(Mode.Combat.GAUGE_MAX),
                monsterGauge = monsterGauge,
                playerStamina = stamina,
                braced = braced,
                monsterIntent = intent.id,
                abilityCooldowns = cooldowns,
                statuses = tickedStatuses,
            )
        )
    }

    /** Chance gate then weighted pick, both from the LOOT stream — save-stable. */
    private fun rollLoot(monster: MonsterDef, dice: Dice): com.cauldron.myriad.engine.model.ItemId? {
        val loot = monster.loot ?: return null
        if (!dice.chance(RngStream.LOOT, loot.chancePercent)) return null
        val total = loot.entries.sumOf { it.weight }
        var pick = dice.roll(RngStream.LOOT, 1..total)
        for (entry in loot.entries) {
            pick -= entry.weight
            if (pick <= 0) return entry.item
        }
        return loot.entries.last().item
    }

    private fun drawIntent(monster: MonsterDef, dice: Dice): MoveDef {
        val total = monster.moves.sumOf { it.weight }
        var pick = dice.roll(RngStream.COMBAT, 1..total)
        for (move in monster.moves) {
            pick -= move.weight
            if (pick <= 0) return move
        }
        return monster.moves.last()
    }

    private fun reduce(state: GameState, event: Event): GameState = when (event) {
        is Event.LookedAround -> state

        is Event.MovedTo -> state.copy(lastRoom = state.currentRoom, currentRoom = event.room)

        is Event.CombatStarted ->
            state.copy(mode = Mode.Combat(event.monster, playerStamina = effectiveMaxStamina(state)))

        is Event.MonsterIntentDrawn -> {
            val mode = state.mode as? Mode.Combat
            if (mode != null) state.copy(mode = mode.copy(monsterIntent = event.move)) else state
        }

        is Event.PlayerStruckMonster -> {
            // Land a blow → train the equipped weapon family (use-texture).
            val family = state.player.equipped?.let { content.items.getValue(it).family }
            val trained = if (family.isNullOrEmpty()) state.player else {
                state.player.copy(mastery = state.player.mastery + (family to ((state.player.mastery[family] ?: 0) + 1)))
            }
            state.copy(player = trained).updateRoom(state.currentRoom) {
                it.copy(monsterHp = ((it.monsterHp ?: 0) - event.damage).coerceAtLeast(0))
            }
        }

        is Event.MonsterSlain -> {
            val withRoom = state.updateRoom(state.currentRoom) { it.copy(monsterHp = null) }
            withRoom.copy(
                mode = Mode.Exploring,
                player = withRoom.player.copy(gold = addClamped(withRoom.player.gold, event.gold)),
            )
        }

        Event.PlayerBraced -> {
            val mode = state.mode as? Mode.Combat
            if (mode != null) state.copy(mode = mode.copy(braced = true)) else state
        }

        is Event.MonsterStruckPlayer -> state.copy(
            player = state.player.copy(hp = (state.player.hp - event.damage).coerceAtLeast(0))
        )

        is Event.CombatTicked -> {
            val mode = state.mode as? Mode.Combat
            if (mode != null) {
                state.copy(
                    mode = mode.copy(
                        playerGauge = event.playerGauge,
                        monsterGauge = event.monsterGauge,
                        playerStamina = event.playerStamina,
                        braced = event.braced,
                        monsterIntent = event.monsterIntent,
                        abilityCooldowns = event.abilityCooldowns,
                        statuses = event.statuses,
                    )
                )
            } else state
        }

        is Event.AbilityUsed -> state

        is Event.MonsterRouted -> state
            .updateRoom(state.currentRoom) { it.copy(monsterHp = null) }
            .copy(mode = Mode.Exploring)

        is Event.Foraged -> state.copy(meters = state.meters + event.values)

        is Event.PlayerHealed -> state.copy(
            player = state.player.copy(hp = (state.player.hp + event.amount).coerceAtMost(effectiveMaxHp(state)))
        )

        is Event.MonsterBled -> state.updateRoom(state.currentRoom) {
            it.copy(monsterHp = ((it.monsterHp ?: 0) - event.damage).coerceAtLeast(0))
        }

        is Event.PlayerSelfHarm -> state.copy(
            player = state.player.copy(hp = (state.player.hp - event.amount).coerceAtLeast(0))
        )

        is Event.XpGained -> state.copy(player = state.player.copy(xp = state.player.xp + event.amount))

        is Event.LeveledUp -> {
            val leveled = state.player.copy(
                level = event.level,
                maxHp = state.player.maxHp + event.hpGain,
                baseAttack = state.player.baseAttack + event.attackGain,
                baseDefense = state.player.baseDefense + event.defenseGain,
                skillPoints = state.player.skillPoints + event.skillPoints,
            )
            // Leveling heals to full — a real moment of relief mid-delve.
            val healed = leveled.copy(hp = state.copy(player = leveled).let { effectiveMaxHp(it) })
            state.copy(player = healed)
        }

        is Event.NodeUnlocked -> {
            val cost = content.nodes.getValue(event.node).cost
            state.copy(
                player = state.player.copy(
                    unlockedNodes = state.player.unlockedNodes + event.node,
                    skillPoints = (state.player.skillPoints - cost).coerceAtLeast(0),
                )
            )
        }

        is Event.Respecced -> {
            val refunded = state.player.copy(
                unlockedNodes = emptyList(),
                skillPoints = state.player.skillPoints + event.refundedPoints,
                gold = (state.player.gold - event.goldCost).coerceAtLeast(0),
            )
            // Respeccing away +maxHp nodes can lower the ceiling; clamp hp down.
            state.copy(player = refunded.copy(hp = refunded.hp.coerceAtMost(effectiveMaxHp(state.copy(player = refunded)))))
        }

        is Event.MetersTicked -> state.copy(
            meters = state.meters + event.values,
            player = state.player.copy(hp = (state.player.hp - event.chillDamage).coerceAtLeast(0)),
        )

        is Event.Camped -> {
            // Rest restores meters AND closes wounds — the fight→camp→heal→descend
            // loop that makes the deep Depths sustainable for careful delvers.
            val rested = state.copy(meters = state.meters + event.restored)
            rested.copy(player = rested.player.copy(hp = effectiveMaxHp(rested)))
        }

        Event.PlayerDied -> state.copy(mode = Mode.Dead)

        is Event.FleeFailed -> state

        is Event.FleeSucceeded -> state.copy(
            lastRoom = state.currentRoom,
            currentRoom = event.to,
            mode = Mode.Exploring,
        )

        is Event.ItemDropped -> state.updateRoom(state.currentRoom) {
            it.copy(itemsOnFloor = it.itemsOnFloor + event.item)
        }

        is Event.ItemFound -> state.updateRoom(state.currentRoom) {
            it.copy(searched = true, itemsOnFloor = it.itemsOnFloor + event.item)
        }

        is Event.ItemTaken -> {
            val withRoom = state.updateRoom(state.currentRoom) {
                it.copy(itemsOnFloor = it.itemsOnFloor - event.item)
            }
            withRoom.copy(player = withRoom.player.copy(inventory = withRoom.player.inventory + event.item))
        }

        is Event.Equipped -> state.copy(player = state.player.copy(equipped = event.item))

        Event.GameWon -> state.copy(mode = Mode.Victory)
    }

    private fun GameState.updateRoom(room: RoomId, transform: (RoomState) -> RoomState): GameState =
        copy(rooms = rooms + (room to transform(roomStateFor(room, content))))

    // ── Derived stats (MASTER_PLAN §16.4) ───────────────────────────────────
    // Level gains are STORED on PlayerState; constellation node bonuses and
    // weapon mastery are DERIVED here, never stored — so respec just changes the
    // unlocked-node list and every number follows.

    private inline fun <reified E : com.cauldron.myriad.engine.model.NodeEffect> nodeSum(
        state: GameState,
        amount: (E) -> Int,
    ): Int = state.player.unlockedNodes.sumOf { id ->
        val effect = content.nodes[id]?.effect
        if (effect is E) amount(effect) else 0
    }

    fun masteryBonus(state: GameState): Int {
        val family = state.player.equipped?.let { content.items.getValue(it).family } ?: return 0
        if (family.isEmpty()) return 0
        val swings = state.player.mastery[family] ?: 0
        return (swings / MASTERY_PER_RANK).coerceAtMost(MASTERY_CAP)
    }

    fun playerAttack(state: GameState): Int =
        state.player.baseAttack +
            (state.player.equipped?.let { content.items.getValue(it).attackBonus } ?: 0) +
            nodeSum<com.cauldron.myriad.engine.model.NodeEffect.Attack>(state) { it.amount } +
            masteryBonus(state)

    fun playerDefense(state: GameState): Int =
        state.player.baseDefense +
            (state.player.equipped?.let { content.items.getValue(it).defenseBonus } ?: 0) +
            nodeSum<com.cauldron.myriad.engine.model.NodeEffect.Defense>(state) { it.amount }

    fun effectiveMaxHp(state: GameState): Int =
        state.player.maxHp + nodeSum<com.cauldron.myriad.engine.model.NodeEffect.MaxHp>(state) { it.amount }

    fun effectiveMaxStamina(state: GameState): Int =
        Mode.Combat.STAMINA_MAX + nodeSum<com.cauldron.myriad.engine.model.NodeEffect.MaxStamina>(state) { it.amount }

    fun critBonus(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.Crit>(state) { it.percent }

    fun damageReduction(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.DamageReduction>(state) { it.amount }

    fun xpBonus(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.XpBonus>(state) { it.percent }

    fun goldFind(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.GoldFind>(state) { it.percent }

    fun staminaEfficiency(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.StaminaEfficiency>(state) { it.percent }.coerceAtMost(80)

    fun cooldownReduction(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.CooldownReduction>(state) { it.turns }

    fun lifesteal(state: GameState): Int =
        nodeSum<com.cauldron.myriad.engine.model.NodeEffect.Lifesteal>(state) { it.percent }

    /** A strike/ability stamina cost after Mind-tree efficiency. */
    fun staminaCost(state: GameState, base: Int): Int =
        (base.toLong() * (100 - staminaEfficiency(state)) / 100).toInt().coerceAtLeast(1)

    fun unlockedVerbs(state: GameState): List<com.cauldron.myriad.engine.model.VerbId> =
        state.player.unlockedNodes.mapNotNull {
            (content.nodes[it]?.effect as? com.cauldron.myriad.engine.model.NodeEffect.GrantVerb)?.verb
        }

    fun unlockedAbilities(state: GameState): List<com.cauldron.myriad.engine.model.AbilityDef> =
        state.player.unlockedNodes.mapNotNull { id ->
            (content.nodes[id]?.effect as? com.cauldron.myriad.engine.model.NodeEffect.GrantAbility)
                ?.let { content.abilities[it.ability] }
        }

    fun hasSense(state: GameState, sense: com.cauldron.myriad.engine.model.SenseId): Boolean =
        state.player.unlockedNodes.any {
            (content.nodes[it]?.effect as? com.cauldron.myriad.engine.model.NodeEffect.GrantSense)?.sense == sense
        }

    // ── Progression queries (used by step, the UI, and the Gauntlet) ─────────

    fun xpFor(monster: MonsterDef): Long =
        (monster.maxHp / 3L) + monster.attack * 4L + monster.defense * 2L + 8L

    /** Total XP needed to BE [level] (triangular curve). */
    fun xpToReach(level: Int): Long = 18L * (level - 1) * level

    fun canUnlock(state: GameState, nodeId: com.cauldron.myriad.engine.model.NodeId): Boolean {
        if (state.mode is Mode.Dead || state.mode is Mode.Victory) return false
        val node = content.nodes[nodeId] ?: return false
        if (nodeId in state.player.unlockedNodes) return false
        if (state.player.skillPoints < node.cost) return false
        return node.prereqs.all { it in state.player.unlockedNodes }
    }

    fun unlockableNodes(state: GameState): List<com.cauldron.myriad.engine.model.NodeId> =
        content.nodes.keys.filter { canUnlock(state, it) }

    fun respecCost(state: GameState): Int = RESPEC_COST_PER_LEVEL * state.player.level

    fun canRespec(state: GameState): Boolean =
        state.mode is Mode.Exploring &&
            content.rooms.getValue(state.currentRoom).haven &&
            state.player.unlockedNodes.isNotEmpty() &&
            state.player.gold >= respecCost(state)

    companion object {
        const val STARTING_HP = 26
        const val STARTING_ATTACK = 2
        const val STARTING_DEFENSE = 1
        const val FLEE_CHANCE_PERCENT = 50
        const val DAMAGE_CAP = 9_999

        /** Player gauge fill per tick; monster speeds are relative to this. */
        const val PLAYER_SPEED = 100

        /** Gauge subtracted after acting — bigger swing, slower return. */
        const val RECOVERY_QUICK = 700
        const val RECOVERY_HEAVY = 1500
        const val RECOVERY_BRACE = 500
        const val RECOVERY_FLEE_FAIL = 800

        const val STAMINA_QUICK = 15
        const val STAMINA_HEAVY = 40
        const val STAMINA_BRACE_RESTORE = 25
        const val RECOVERY_ABILITY = 1300
        const val RECOVERY_RIPOSTE = 700
        const val RECOVERY_HEW = 1700
        const val RECOVERY_CHANNEL = 1800
        const val RECOVERY_RECOVER = 600
        const val FORAGE_GAIN = 12
        const val FORAGE_KINDLE = 25

        // Progression
        const val MASTERY_PER_RANK = 12
        const val MASTERY_CAP = 3
        const val LEVEL_CAP = 99
        const val LEVEL_HP_GAIN = 12
        const val LEVEL_ATTACK_GAIN = 2
        const val LEVEL_DEFENSE_GAIN = 1
        const val LEVEL_SKILL_POINTS = 1
        const val RESPEC_COST_PER_LEVEL = 50

        /** Strike power as attack × num/den. */
        const val QUICK_POWER_NUM = 7
        const val HEAVY_POWER_NUM = 16
        const val POWER_DEN = 10

        /** Hard upper bound on advancement iterations; validation makes this unreachable. */
        const val ADVANCE_GUARD = 100_000
    }
}

/**
 * Integer-only damage: (attack × powerNum/powerDen) + roll − defense; crit
 * doubles before the clamp, bracing halves after (round up). Long internally
 * so hostile stat values can never overflow (MASTER_PLAN §9); result clamped
 * to 1..DAMAGE_CAP.
 */
internal fun scaledDamage(
    attack: Int,
    powerNum: Int,
    powerDen: Int,
    roll: Int,
    defense: Int,
    crit: Boolean,
    halved: Boolean,
): Int {
    val effective = attack.toLong() * powerNum / powerDen
    var damage = effective + roll - defense.toLong()
    if (crit) damage *= 2
    if (halved) damage = (damage + 1) / 2
    return damage.coerceIn(1L, Engine.DAMAGE_CAP.toLong()).toInt()
}

internal fun addClamped(a: Int, b: Int): Int =
    (a.toLong() + b.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
