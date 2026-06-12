package com.cauldron.myriad.engine

import com.cauldron.myriad.engine.model.Action
import com.cauldron.myriad.engine.model.ContentPack
import com.cauldron.myriad.engine.model.ExitDef
import com.cauldron.myriad.engine.model.GameState
import com.cauldron.myriad.engine.model.ItemDef
import com.cauldron.myriad.engine.model.ItemId
import com.cauldron.myriad.engine.model.MonsterDef
import com.cauldron.myriad.engine.model.MonsterId
import com.cauldron.myriad.engine.model.RoomDef
import com.cauldron.myriad.engine.model.RoomId
import com.cauldron.myriad.engine.rng.Pcg32

/** Self-contained packs so engine tests never depend on the content module. */
object TestWorlds {
    val CELLAR = RoomId("cellar")
    val PASSAGE = RoomId("passage")
    val STAIR = RoomId("stair")
    val SWORD = ItemId("sword")
    val RAT = MonsterId("rat")

    /** Mirrors the Ember Cellar topology: start (hidden sword) → monster room → goal. */
    fun cellarLike(
        ratAttack: Int = 3,
        ratHp: Int = 8,
        ratDefense: Int = 1,
        goldDrop: IntRange = 2..6,
    ): ContentPack = ContentPack(
        version = "test/1",
        intro = "Test intro.",
        startRoom = CELLAR,
        rooms = mapOf(
            CELLAR to RoomDef(
                id = CELLAR, name = "Cellar", description = "A test cellar.",
                exits = listOf(ExitDef("north", PASSAGE)),
                hiddenItem = SWORD,
            ),
            PASSAGE to RoomDef(
                id = PASSAGE, name = "Passage", description = "A test passage.",
                exits = listOf(ExitDef("south", CELLAR), ExitDef("north", STAIR)),
                monster = RAT,
            ),
            STAIR to RoomDef(
                id = STAIR, name = "Stair", description = "A test stair.",
                exits = listOf(ExitDef("south", PASSAGE)),
                isGoal = true,
            ),
        ),
        items = mapOf(SWORD to ItemDef(SWORD, "sword", "A test sword.", attackBonus = 2)),
        monsters = mapOf(
            RAT to MonsterDef(
                id = RAT, name = "rat", description = "A test rat.",
                maxHp = ratHp, attack = ratAttack, defense = ratDefense, goldDrop = goldDrop,
            ),
        ),
    )

    /** Monster with hostile stat extremes, for overflow and death-path tests. */
    fun brutal(
        attack: Int = Int.MAX_VALUE,
        hp: Int = 1_000,
        defense: Int = Int.MAX_VALUE,
    ): ContentPack = ContentPack(
        version = "test-brutal/1",
        intro = "Brutal test intro.",
        startRoom = CELLAR,
        rooms = mapOf(
            CELLAR to RoomDef(
                id = CELLAR, name = "Cellar", description = "A test cellar.",
                exits = listOf(ExitDef("north", PASSAGE)),
            ),
            PASSAGE to RoomDef(
                id = PASSAGE, name = "Passage", description = "A test passage.",
                exits = listOf(ExitDef("south", CELLAR)),
                monster = RAT, isGoal = true,
            ),
        ),
        items = emptyMap(),
        monsters = mapOf(
            RAT to MonsterDef(
                id = RAT, name = "horror", description = "A stat-maxed horror.",
                maxHp = hp, attack = attack, defense = defense, goldDrop = 0..0,
            ),
        ),
    )

    /** One weak monster sitting directly on the goal room. */
    fun monsterOnGoal(): ContentPack = ContentPack(
        version = "test-goal/1",
        intro = "Goal test intro.",
        startRoom = CELLAR,
        rooms = mapOf(
            CELLAR to RoomDef(
                id = CELLAR, name = "Cellar", description = "A test cellar.",
                exits = listOf(ExitDef("north", PASSAGE)),
            ),
            PASSAGE to RoomDef(
                id = PASSAGE, name = "Passage", description = "A test passage.",
                exits = listOf(ExitDef("south", CELLAR)),
                monster = RAT, isGoal = true,
            ),
        ),
        items = emptyMap(),
        monsters = mapOf(
            RAT to MonsterDef(
                id = RAT, name = "wisp", description = "A dying wisp.",
                maxHp = 1, attack = 1, defense = 0, goldDrop = 1..1,
            ),
        ),
    )
}

data class WalkResult(val state: GameState, val actions: List<Action>)

/** Deterministic random bot: picks uniformly among legal actions with its own PCG stream. */
fun randomWalk(engine: Engine, seed: Long, maxSteps: Int, botSequence: Long = 999): WalkResult {
    var state = engine.newGame(seed, "Bot")
    val picker = Pcg32.seeded(seed, botSequence)
    val actions = mutableListOf<Action>()
    repeat(maxSteps) {
        val legal = engine.legalActions(state)
        if (legal.isEmpty()) return WalkResult(state, actions)
        val action = legal[picker.nextBelow(legal.size)]
        actions += action
        state = engine.step(state, action).state
    }
    return WalkResult(state, actions)
}
