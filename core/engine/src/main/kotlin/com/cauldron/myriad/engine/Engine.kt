package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.Event
import com.cauldron.myriad.engine.model.FeedKind
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.model.PlayerState
import com.cauldron.myriad.engine.model.RoomId
import com.cauldron.myriad.engine.model.RoomState
import com.cauldron.myriad.engine.model.appendFeed
import com.cauldron.myriad.engine.rng.Dice
import com.cauldron.myriad.engine.rng.RngState
import com.cauldron.myriad.engine.rng.RngStream

data class StepResult(val state: GameState, val events: List<Event>)

/**
 * The deterministic rules core. Stateless apart from the content pack:
 * same state + same action always produce the same events and next state.
 * All rules math is integer-only with explicit clamps (MASTER_PLAN §9).
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
     */
    fun legalActions(state: GameState): List<Action> = when (state.mode) {
        Mode.Dead, Mode.Victory -> emptyList()
        is Mode.Combat -> listOf(Action.Attack, Action.Defend, Action.Flee)
        Mode.Exploring -> buildList {
            add(Action.Look)
            val room = content.rooms.getValue(state.currentRoom)
            val roomState = state.rooms.getValue(state.currentRoom)
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
        return StepResult(
            state = next.appendFeed(events.map { Narrator.kindOf(it) to Narrator.narrate(it, next, content) }),
            events = events,
        )
    }

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
            val destState = state.rooms.getValue(action.to)
            val monsterAlive = dest.monster != null && (destState.monsterHp ?: 0) > 0
            when {
                monsterAlive -> add(Event.CombatStarted(checkNotNull(dest.monster)))
                dest.isGoal -> add(Event.GameWon)
            }
        }

        Action.Attack -> resolveCombatRound(state, dice, defending = false, fleeing = false)
        Action.Defend -> resolveCombatRound(state, dice, defending = true, fleeing = false)
        Action.Flee -> resolveCombatRound(state, dice, defending = false, fleeing = true)
    }

    private fun resolveCombatRound(
        state: GameState,
        dice: Dice,
        defending: Boolean,
        fleeing: Boolean,
    ): List<Event> = buildList {
        val mode = state.mode as Mode.Combat
        val monster = content.monsters.getValue(mode.monster)
        val monsterHp = state.rooms.getValue(state.currentRoom).monsterHp ?: 0

        if (fleeing) {
            if (dice.chance(RngStream.COMBAT, FLEE_CHANCE_PERCENT)) {
                add(Event.FleeSucceeded(state.lastRoom ?: content.startRoom))
                return@buildList
            }
            add(Event.FleeFailed(mode.monster))
        } else if (defending) {
            add(Event.PlayerDefended)
        } else {
            val roll = dice.roll(RngStream.COMBAT, 1..6)
            val crit = roll == 6
            val dealt = damage(playerAttack(state), roll, monster.defense, crit, halved = false)
            add(Event.PlayerStruckMonster(mode.monster, dealt, crit))
            if (monsterHp - dealt <= 0) {
                val gold = dice.roll(RngStream.LOOT, monster.goldDrop)
                add(Event.MonsterSlain(mode.monster, gold))
                if (content.rooms.getValue(state.currentRoom).isGoal) add(Event.GameWon)
                return@buildList
            }
        }

        // The monster strikes back (also the price of a failed flee).
        val roll = dice.roll(RngStream.COMBAT, 1..6)
        val crit = roll == 6
        val dealt = damage(monster.attack, roll, playerDefense(state), crit, halved = defending)
        add(Event.MonsterStruckPlayer(mode.monster, dealt, crit, defended = defending))
        if (state.player.hp - dealt <= 0) add(Event.PlayerDied)
    }

    private fun reduce(state: GameState, event: Event): GameState = when (event) {
        is Event.LookedAround -> state

        is Event.MovedTo -> state.copy(lastRoom = state.currentRoom, currentRoom = event.room)

        is Event.CombatStarted -> state.copy(mode = Mode.Combat(event.monster))

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

        Event.PlayerDefended -> state

        is Event.MonsterStruckPlayer -> state.copy(
            player = state.player.copy(hp = (state.player.hp - event.damage).coerceAtLeast(0))
        )

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
    }
}

private fun GameState.updateRoom(room: RoomId, transform: (RoomState) -> RoomState): GameState =
    copy(rooms = rooms + (room to transform(rooms.getValue(room))))

/**
 * Integer-only damage: atk + roll − def, crit doubles before the clamp,
 * defending halves after (round up). Long internally so hostile stat values
 * can never overflow (MASTER_PLAN §9); result clamped to 1..DAMAGE_CAP.
 */
internal fun damage(attack: Int, roll: Int, defense: Int, crit: Boolean, halved: Boolean): Int {
    var dmg = attack.toLong() + roll.toLong() - defense.toLong()
    if (crit) dmg *= 2
    if (halved) dmg = (dmg + 1) / 2
    return dmg.coerceIn(1L, Engine.DAMAGE_CAP.toLong()).toInt()
}

internal fun addClamped(a: Int, b: Int): Int =
    (a.toLong() + b.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
