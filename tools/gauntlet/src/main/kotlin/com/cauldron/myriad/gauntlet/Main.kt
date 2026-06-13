package com.cauldron.myriad.gauntlet

import com.cauldron.myriad.content.EmberCellar
import com.cauldron.myriad.content.Hundredfold
import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.rng.Pcg32
import kotlin.system.exitProcess

/**
 * The Gauntlet (MASTER_PLAN §11): headless bots play complete runs.
 * Hard-fails on any crash or softlock, printing the seed that reproduces it —
 * event-sourced determinism means seed + strategy + version IS the bug report.
 *
 * Strategies:
 *  - random  : uniform legal action (softlock/crash fuzzing across the world).
 *  - delver  : a progressing optimal-ish bot — equips upgrades, fights smart with
 *              abilities, levels, spends points, camps, and pushes down the Depths.
 *              Reports the deepest floor reached. This is the balance probe.
 */
fun main(args: Array<String>) {
    val runs = argValue(args, "--runs")?.toIntOrNull() ?: 1_000
    val baseSeed = argValue(args, "--seed")?.toLongOrNull() ?: 20_260_611L
    val maxTurns = argValue(args, "--max-turns")?.toIntOrNull() ?: 300
    val strategy = argValue(args, "--strategy") ?: "random"

    val engine = Engine(EmberCellar.pack)
    val pack = EmberCellar.pack
    val seeder = Pcg32.seeded(baseSeed, 0)

    if (argValue(args, "--trace") != null) {
        traceOne(engine, baseSeed, maxTurns)
        return
    }

    val weaponsByFamily = pack.items.values.filter { it.family.isNotEmpty() }
        .groupingBy { it.family }.eachCount().toSortedMap()
    println("The Gauntlet — $strategy bot · ${pack.version} · $runs runs · max $maxTurns turns")
    println("world: ${pack.rooms.size} rooms · ${pack.monsters.size} monsters · ${pack.items.size} items")
    println("weapons: ${weaponsByFamily.values.sum()} across ${weaponsByFamily.size} families")

    var wins = 0
    var deaths = 0
    var timeouts = 0
    var totalWinTurns = 0L
    var maxLevel = 0
    var deepestFloor = 0
    var floorClears = 0 // runs that reached floor 100's hoard

    for (run in 0 until runs) {
        val seed = (seeder.nextUInt().toLong() shl 32) or seeder.nextUInt().toLong()
        val picker = Pcg32.seeded(seed, 99)
        var state = engine.newGame(seed, "Gauntlet")
        var turns = 0
        var runDeepest = 0

        try {
            while (turns < maxTurns) {
                val terminal = state.mode is Mode.Dead || state.mode is Mode.Victory
                val legal = engine.legalActions(state)
                if (terminal) {
                    if (legal.isNotEmpty()) fail(seed, turns, "terminal state offers actions: $legal")
                    break
                }
                if (legal.isEmpty()) fail(seed, turns, "SOFTLOCK in mode ${state.mode} at ${state.currentRoom.value}")

                runDeepest = maxOf(runDeepest, floorOf(state))
                state = when (strategy) {
                    "delver" -> engine.step(state, delverChoice(engine, state, legal)).state
                    else -> engine.step(state, legal[picker.nextBelow(legal.size)]).state
                }
                turns++
            }
        } catch (e: Exception) {
            System.err.println("CRASH on run $run, seed $seed, turn $turns")
            e.printStackTrace()
            exitProcess(1)
        }

        maxLevel = maxOf(maxLevel, state.player.level)
        deepestFloor = maxOf(deepestFloor, runDeepest)
        if (runDeepest >= Hundredfold.FLOORS) floorClears++
        when (state.mode) {
            is Mode.Victory -> { wins++; totalWinTurns += turns }
            is Mode.Dead -> deaths++
            else -> timeouts++
        }
    }

    println("results")
    println("  wins:     $wins (${percent(wins, runs)})")
    println("  deaths:   $deaths (${percent(deaths, runs)})")
    println("  timeouts: $timeouts (${percent(timeouts, runs)})")
    if (wins > 0) println("  avg turns to win: ${totalWinTurns / wins}")
    println("  max level reached: $maxLevel")
    println("  deepest floor reached: $deepestFloor / ${Hundredfold.FLOORS}")
    if (strategy == "delver") println("  runs reaching the bottom: $floorClears")
    println("verdict: no crashes, no softlocks across $runs runs")
}

private fun traceOne(engine: Engine, seed: Long, maxTurns: Int) {
    var state = engine.newGame(seed, "Trace")
    var turns = 0
    var lastRoom = ""
    while (turns < maxTurns && state.mode !is Mode.Dead && state.mode !is Mode.Victory) {
        val legal = engine.legalActions(state)
        if (legal.isEmpty()) break
        val action = delverChoice(engine, state, legal)
        if (state.currentRoom.value != lastRoom) {
            lastRoom = state.currentRoom.value
            println("t$turns ${lastRoom.padEnd(22)} L${state.player.level} hp ${state.player.hp}/${engine.effectiveMaxHp(state)} xp ${state.player.xp} pts ${state.player.skillPoints} warmth ${state.meters}")
        }
        state = engine.step(state, action).state
        turns++
    }
    println("ENDED ${state.mode} at ${state.currentRoom.value} turn $turns L${state.player.level} hp ${state.player.hp}")
    println("last feed:")
    state.feed.takeLast(8).forEach { println("  ${it.text.replace("\n", " ")}") }
}

/** Current Depths floor (0 = not in the Depths). */
private fun floorOf(state: GameState): Int {
    val id = state.currentRoom.value
    if (!id.startsWith("depths_")) return 0
    return id.substringAfterLast('_').toIntOrNull() ?: 0
}

/**
 * A competent delver: spend skill points the moment you have them, equip the
 * best weapon you hold, camp when cold or hurt at a haven, fight with abilities,
 * and always push deeper.
 */
private fun delverChoice(engine: Engine, state: GameState, legal: List<Action>): Action {
    // 1) Spend points — prefer teeth (abilities, attack, crit) over pure padding,
    //    so the bot gains Sunder/Second Wind and real offense as it levels.
    engine.unlockableNodes(state).minByOrNull { id ->
        val node = engine.content.nodes.getValue(id)
        val priority = when (node.effect) {
            is com.cauldron.myriad.engine.model.NodeEffect.GrantAbility -> 0
            is com.cauldron.myriad.engine.model.NodeEffect.Attack,
            is com.cauldron.myriad.engine.model.NodeEffect.Crit -> 1
            is com.cauldron.myriad.engine.model.NodeEffect.GrantSense -> 3
            else -> 2
        }
        priority * 100 + node.cost
    }?.let { return Action.UnlockNode(it) }

    val player = state.player
    val room = engine.content.rooms.getValue(state.currentRoom)

    when (state.mode) {
        is Mode.Combat -> {
            val mode = state.mode as Mode.Combat
            val monster = engine.content.monsters.getValue(mode.monster)
            val intent = engine.moveFor(monster, mode.monsterIntent)
            val hurt = player.hp * 3 < engine.effectiveMaxHp(state)
            // Heal when badly hurt and able.
            if (hurt) legal.filterIsInstance<Action.UseAbility>().firstOrNull { isHeal(engine, it) }?.let { return it }
            // Otherwise hit with the biggest affordable strike ability, else heavy/quick.
            legal.filterIsInstance<Action.UseAbility>().firstOrNull { !isHeal(engine, it) }?.let { return it }
            // Only brace the truly big blows (≥1.6×) and never brace twice in a row —
            // attack through medium hits rather than stalemating.
            val bigIncoming = intent.powerNum * 10 >= intent.powerDen * 16
            if (bigIncoming && !mode.braced && Action.Brace in legal) return Action.Brace
            if (Action.HeavyStrike in legal) return Action.HeavyStrike
            if (Action.QuickStrike in legal) return Action.QuickStrike
            return Action.Brace
        }

        Mode.Exploring -> {
            // Equip the strongest weapon on hand if it beats current.
            bestUpgrade(engine, state, legal)?.let { return it }
            // Rest at every camp you pass when not at full health — free healing
            // is the intended sustain loop.
            if (Action.Camp in legal && player.hp < engine.effectiveMaxHp(state)) return Action.Camp
            // Grab loot and search.
            legal.filterIsInstance<Action.Take>().firstOrNull()?.let { return it }
            if (Action.Search in legal && room.hiddenItem != null) return Action.Search
            // With the First Ember in hand, the delve is done — climb back to the
            // surface stair and claim victory. Otherwise push deeper.
            val hasCapstone = Hundredfold.FIRST_EMBER in player.inventory
            val moves = legal.filterIsInstance<Action.Move>()
            if (moves.isNotEmpty()) {
                val forward = moves.filter { it.to != state.lastRoom }.ifEmpty { moves }
                if (hasCapstone) {
                    forward.firstOrNull { engine.content.rooms[it.to]?.isGoal == true }?.let { return it }
                    return forward.minByOrNull { floorTarget(it) }!! // ascend
                }
                return forward.maxByOrNull { floorTarget(it) }!! // descend
            }
            return legal.first()
        }

        else -> return legal.first()
    }
}

private fun isHeal(engine: Engine, action: Action.UseAbility): Boolean =
    engine.content.abilities.getValue(action.ability).kind is com.cauldron.myriad.engine.model.AbilityKind.Heal

private fun bestUpgrade(engine: Engine, state: GameState, legal: List<Action>): Action? {
    val current = engine.playerAttack(state)
    return legal.filterIsInstance<Action.Equip>()
        .maxByOrNull { engine.content.items.getValue(it.item).attackBonus }
        ?.takeIf { engine.playerAttack(engine.step(state, it).state) > current }
}

private fun floorTarget(move: Action.Move): Int {
    val id = move.to.value
    if (!id.startsWith("depths_")) return -1
    return id.substringAfterLast('_').toIntOrNull() ?: -1
}

private fun argValue(args: Array<String>, name: String): String? =
    args.toList().zipWithNext().firstOrNull { it.first == name }?.second

private fun percent(n: Int, total: Int): String =
    if (total == 0) "0%" else "${(n * 1000L / total).toDouble() / 10}%"

private fun fail(seed: Long, turn: Int, message: String): Nothing {
    System.err.println("GAUNTLET FAILURE seed=$seed turn=$turn: $message")
    exitProcess(1)
}
