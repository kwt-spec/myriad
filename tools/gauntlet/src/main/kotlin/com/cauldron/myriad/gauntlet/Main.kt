package com.cauldron.myriad.gauntlet

import com.cauldron.myriad.content.EmberCellar
import com.cauldron.myriad.engine.Engine
import com.cauldron.myriad.engine.model.Mode
import com.cauldron.myriad.engine.rng.Pcg32
import kotlin.system.exitProcess

/**
 * The Gauntlet (MASTER_PLAN §11): headless bots play complete runs.
 * Hard-fails on any crash or softlock, printing the seed that reproduces it —
 * event-sourced determinism means seed + strategy + version IS the bug report.
 */
fun main(args: Array<String>) {
    val runs = argValue(args, "--runs")?.toIntOrNull() ?: 1_000
    val baseSeed = argValue(args, "--seed")?.toLongOrNull() ?: 20_260_611L
    val maxTurns = argValue(args, "--max-turns")?.toIntOrNull() ?: 300

    val engine = Engine(EmberCellar.pack)
    val seeder = Pcg32.seeded(baseSeed, 0)

    var wins = 0
    var deaths = 0
    var timeouts = 0
    var totalWinTurns = 0L
    var minGold = Int.MAX_VALUE
    var maxGold = 0
    var totalGold = 0L

    val pack = EmberCellar.pack
    val weaponsByFamily = pack.items.values.filter { it.family.isNotEmpty() }
        .groupingBy { it.family }.eachCount().toSortedMap()
    println("The Gauntlet — random bot · ${pack.version} · $runs runs · max $maxTurns turns")
    println("world: ${pack.rooms.size} rooms · ${pack.monsters.size} monsters · ${pack.items.size} items")
    println("weapons: ${weaponsByFamily.values.sum()} across ${weaponsByFamily.size} families " +
        "(${weaponsByFamily.entries.joinToString(" ") { "${it.key}=${it.value}" }})")

    for (run in 0 until runs) {
        val seed = (seeder.nextUInt().toLong() shl 32) or seeder.nextUInt().toLong()
        val picker = Pcg32.seeded(seed, 99)
        var state = engine.newGame(seed, "Gauntlet")
        var turns = 0

        try {
            while (turns < maxTurns) {
                val legal = engine.legalActions(state)
                val terminal = state.mode is Mode.Dead || state.mode is Mode.Victory
                if (terminal) {
                    if (legal.isNotEmpty()) fail(seed, turns, "terminal state offers actions: $legal")
                    break
                }
                if (legal.isEmpty()) fail(seed, turns, "SOFTLOCK: no legal actions in mode ${state.mode} at ${state.currentRoom.value}")
                state = engine.step(state, legal[picker.nextBelow(legal.size)]).state
                turns++
            }
        } catch (e: Exception) {
            System.err.println("CRASH on run $run, seed $seed, turn $turns")
            e.printStackTrace()
            exitProcess(1)
        }

        when (state.mode) {
            is Mode.Victory -> {
                wins++
                totalWinTurns += turns
                minGold = minOf(minGold, state.player.gold)
                maxGold = maxOf(maxGold, state.player.gold)
                totalGold += state.player.gold
            }
            is Mode.Dead -> deaths++
            else -> timeouts++
        }
    }

    println("results")
    println("  wins:     $wins (${percent(wins, runs)})")
    println("  deaths:   $deaths (${percent(deaths, runs)})")
    println("  timeouts: $timeouts (${percent(timeouts, runs)})  [random bot wandering, not softlocks]")
    if (wins > 0) {
        println("  avg turns to win: ${totalWinTurns / wins}")
        println("  gold on win: min $minGold · avg ${totalGold / wins} · max $maxGold")
    }
    println("verdict: no crashes, no softlocks across $runs runs")
}

private fun argValue(args: Array<String>, name: String): String? =
    args.toList().zipWithNext().firstOrNull { it.first == name }?.second

private fun percent(n: Int, total: Int): String =
    if (total == 0) "0%" else "${(n * 1000L / total).toDouble() / 10}%"

private fun fail(seed: Long, turn: Int, message: String): Nothing {
    System.err.println("GAUNTLET FAILURE seed=$seed turn=$turn: $message")
    exitProcess(1)
}
