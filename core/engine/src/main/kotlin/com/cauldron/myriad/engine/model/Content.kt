package com.cauldron.myriad.engine.model

data class ItemDef(
    val id: ItemId,
    val name: String,
    val description: String,
    val attackBonus: Int = 0,
    val defenseBonus: Int = 0,
) {
    val isEquippable: Boolean get() = attackBonus != 0 || defenseBonus != 0
}

data class MonsterDef(
    val id: MonsterId,
    val name: String,
    val description: String,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val goldDrop: IntRange,
)

data class ExitDef(val label: String, val to: RoomId)

data class RoomDef(
    val id: RoomId,
    val name: String,
    val description: String,
    val exits: List<ExitDef> = emptyList(),
    val monster: MonsterId? = null,
    val hiddenItem: ItemId? = null,
    val searchText: String? = null,
    val isGoal: Boolean = false,
)

class ContentValidationException(message: String) : IllegalStateException(message)

/**
 * Static world definition. Validates itself on construction — the seed of the
 * Forge linters (MASTER_PLAN §10): a pack that constructs is a pack with no
 * dangling references, no unreachable rooms, and a reachable goal.
 */
data class ContentPack(
    val version: String,
    val intro: String,
    val startRoom: RoomId,
    val rooms: Map<RoomId, RoomDef>,
    val items: Map<ItemId, ItemDef>,
    val monsters: Map<MonsterId, MonsterDef>,
) {
    companion object {
        const val GOLD_DROP_CAP = 1_000_000
    }

    init {
        val problems = mutableListOf<String>()

        if (startRoom !in rooms) problems += "startRoom '${startRoom.value}' is not in rooms"
        for (room in rooms.values) {
            if (room.id !in rooms || rooms[room.id] !== room) {
                problems += "room '${room.id.value}' keyed inconsistently"
            }
            for (exit in room.exits) {
                if (exit.to !in rooms) {
                    problems += "room '${room.id.value}': exit '${exit.label}' leads to missing room '${exit.to.value}'"
                }
            }
            room.monster?.let {
                if (it !in monsters) problems += "room '${room.id.value}': missing monster '${it.value}'"
            }
            room.hiddenItem?.let {
                if (it !in items) problems += "room '${room.id.value}': missing item '${it.value}'"
            }
        }

        for (monster in monsters.values) {
            if (monster.maxHp <= 0) problems += "monster '${monster.id.value}': maxHp must be positive"
            if (monster.goldDrop.isEmpty()) problems += "monster '${monster.id.value}': empty goldDrop range"
            if (monster.goldDrop.first < 0) problems += "monster '${monster.id.value}': negative goldDrop"
            if (monster.goldDrop.last > GOLD_DROP_CAP) {
                // Also keeps the RNG bound math (range width + 1) far from Int overflow.
                problems += "monster '${monster.id.value}': goldDrop exceeds cap $GOLD_DROP_CAP"
            }
        }

        // Reachability from the start room (BFS over exits).
        val reachable = mutableSetOf(startRoom)
        val queue = ArrayDeque(listOf(startRoom))
        while (queue.isNotEmpty()) {
            val current = rooms[queue.removeFirst()] ?: continue
            for (exit in current.exits) {
                if (exit.to in rooms && reachable.add(exit.to)) queue.add(exit.to)
            }
        }
        val unreachable = rooms.keys - reachable
        if (unreachable.isNotEmpty()) {
            problems += "unreachable rooms: ${unreachable.joinToString { it.value }}"
        }

        val goals = rooms.values.filter { it.isGoal }
        if (goals.isEmpty()) {
            problems += "no goal room defined"
        } else if (goals.none { it.id in reachable }) {
            problems += "no goal room is reachable from start"
        }

        if (problems.isNotEmpty()) {
            throw ContentValidationException(
                "Content pack '$version' failed validation:\n - " + problems.joinToString("\n - ")
            )
        }
    }
}
