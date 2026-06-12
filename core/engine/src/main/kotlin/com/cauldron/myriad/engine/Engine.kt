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
import com.cauldron.myriad.engine.model.appendFeed
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
            if (mode.playerStamina >= STAMINA_QUICK) add(Action.QuickStrike)
            if (mode.playerStamina >= STAMINA_HEAVY) add(Action.HeavyStrike)
            add(Action.Brace)
            add(Action.Flee)
        }
        Mode.Exploring -> buildList {
            add(Action.Look)
            val room = content.rooms.getValue(state.currentRoom)
            val roomState = state.roomStateFor(state.currentRoom, content)
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

    fun step(state: GameState, action: Action): StepResult {
        require(action in legalActions(state)) {
            "Illegal action $action in mode ${state.mode} at ${state.currentRoom.value}"
        }
        val dice = Dice(state.rng)
        val events = resolve(state, action, dice)
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

        Action.QuickStrike -> resolveCombat(state, dice, CombatChoice.QUICK)
        Action.HeavyStrike -> resolveCombat(state, dice, CombatChoice.HEAVY)
        Action.Brace -> resolveCombat(state, dice, CombatChoice.BRACE)
        Action.Flee -> resolveCombat(state, dice, CombatChoice.FLEE)
    }

    private enum class CombatChoice { QUICK, HEAVY, BRACE, FLEE }

    private fun resolveCombat(state: GameState, dice: Dice, choice: CombatChoice): List<Event> = buildList {
        val mode = state.mode as Mode.Combat
        require(mode.playerGauge >= Mode.Combat.GAUGE_MAX) {
            "player acted with unfilled gauge ${mode.playerGauge}"
        }
        val monsterDef = content.monsters.getValue(mode.monster)
        var monsterHp = state.roomStateFor(state.currentRoom, content).monsterHp ?: 0
        var playerHp = state.player.hp
        var stamina = mode.playerStamina
        var braced = mode.braced
        var intent = moveFor(monsterDef, mode.monsterIntent)
        var playerGauge: Int
        var monsterGauge = mode.monsterGauge

        when (choice) {
            CombatChoice.QUICK, CombatChoice.HEAVY -> {
                val heavy = choice == CombatChoice.HEAVY
                stamina -= if (heavy) STAMINA_HEAVY else STAMINA_QUICK
                val roll = dice.roll(RngStream.COMBAT, 1..6)
                val crit = roll == 6 || (heavy && roll == 5)
                val damage = scaledDamage(
                    attack = playerAttack(state),
                    powerNum = if (heavy) HEAVY_POWER_NUM else QUICK_POWER_NUM,
                    powerDen = POWER_DEN,
                    roll = roll,
                    defense = monsterDef.defense,
                    crit = crit,
                    halved = false,
                )
                add(Event.PlayerStruckMonster(mode.monster, damage, crit, heavy))
                monsterHp -= damage
                if (monsterHp <= 0) {
                    val gold = dice.roll(RngStream.LOOT, monsterDef.goldDrop)
                    add(Event.MonsterSlain(mode.monster, gold))
                    if (content.rooms.getValue(state.currentRoom).isGoal) add(Event.GameWon)
                    return@buildList
                }
                playerGauge = Mode.Combat.GAUGE_MAX - if (heavy) RECOVERY_HEAVY else RECOVERY_QUICK
            }

            CombatChoice.BRACE -> {
                add(Event.PlayerBraced)
                braced = true
                stamina = (stamina + STAMINA_BRACE_RESTORE).coerceAtMost(Mode.Combat.STAMINA_MAX)
                playerGauge = Mode.Combat.GAUGE_MAX - RECOVERY_BRACE
            }

            CombatChoice.FLEE -> {
                if (dice.chance(RngStream.COMBAT, FLEE_CHANCE_PERCENT)) {
                    add(Event.FleeSucceeded(state.lastRoom ?: content.startRoom))
                    return@buildList
                }
                add(Event.FleeFailed(mode.monster))
                playerGauge = Mode.Combat.GAUGE_MAX - RECOVERY_FLEE_FAIL
            }
        }

        // Advance time until the player is ready again. The monster's gauge may
        // wrap multiple times — each wrap executes the telegraphed intent and
        // draws the next one.
        var guard = 0
        while (playerGauge < Mode.Combat.GAUGE_MAX) {
            check(++guard <= ADVANCE_GUARD) { "ATB advancement runaway (speeds: player $PLAYER_SPEED, monster ${monsterDef.speed})" }
            playerGauge += PLAYER_SPEED
            monsterGauge += monsterDef.speed
            while (monsterGauge >= Mode.Combat.GAUGE_MAX) {
                monsterGauge -= Mode.Combat.GAUGE_MAX
                val roll = dice.roll(RngStream.COMBAT, 1..6)
                val crit = roll == 6
                val damage = scaledDamage(
                    attack = monsterDef.attack,
                    powerNum = intent.powerNum,
                    powerDen = intent.powerDen,
                    roll = roll,
                    defense = playerDefense(state),
                    crit = crit,
                    halved = braced,
                )
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
        add(
            Event.CombatTicked(
                playerGauge = playerGauge.coerceAtMost(Mode.Combat.GAUGE_MAX),
                monsterGauge = monsterGauge,
                playerStamina = stamina,
                braced = braced,
                monsterIntent = intent.id,
            )
        )
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

        is Event.CombatStarted -> state.copy(mode = Mode.Combat(event.monster))

        is Event.MonsterIntentDrawn -> {
            val mode = state.mode as? Mode.Combat
            if (mode != null) state.copy(mode = mode.copy(monsterIntent = event.move)) else state
        }

        is Event.PlayerStruckMonster -> state.updateRoom(state.currentRoom) {
            it.copy(monsterHp = ((it.monsterHp ?: 0) - event.damage).coerceAtLeast(0))
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
                    )
                )
            } else state
        }

        Event.PlayerDied -> state.copy(mode = Mode.Dead)

        is Event.FleeFailed -> state

        is Event.FleeSucceeded -> state.copy(
            lastRoom = state.currentRoom,
            currentRoom = event.to,
            mode = Mode.Exploring,
        )

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

    fun playerAttack(state: GameState): Int =
        state.player.baseAttack + (state.player.equipped?.let { content.items.getValue(it).attackBonus } ?: 0)

    fun playerDefense(state: GameState): Int =
        state.player.baseDefense + (state.player.equipped?.let { content.items.getValue(it).defenseBonus } ?: 0)

    companion object {
        const val STARTING_HP = 20
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
