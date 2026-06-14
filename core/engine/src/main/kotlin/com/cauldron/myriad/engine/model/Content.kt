package com.cauldron.myriad.engine.model

data class ItemDef(
    val id: ItemId,
    val name: String,
    val description: String,
    val attackBonus: Int = 0,
    val defenseBonus: Int = 0,
    /** Family tag for the Hundredfold floors ("dagger", "sword", "staff", …). */
    val family: String = "",
    /** Generation tier 1..5; drives loot-table assignment and stat envelopes. */
    val tier: Int = 1,
) {
    val isEquippable: Boolean get() = attackBonus != 0 || defenseBonus != 0
}

data class LootEntry(val item: ItemId, val weight: Int)

/** Rolled on MonsterSlain from the LOOT stream: chance gate, then weighted pick. */
data class LootTable(val chancePercent: Int, val entries: List<LootEntry>)

/**
 * One telegraphed monster move. Damage = attack × powerNum/powerDen, fed into
 * the shared damage formula. The telegraph is shown to the player BEFORE the
 * move lands — readable combat is the whole point (MASTER_PLAN M1a).
 */
data class MoveDef(
    val id: MoveId,
    val name: String,
    val telegraph: String,
    val powerNum: Int,
    val powerDen: Int,
    val weight: Int,
)

data class MonsterDef(
    val id: MonsterId,
    val name: String,
    val description: String,
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    /** ATB gauge fill per tick; player is 100. 240 means it acts ~2.4× as often. */
    val speed: Int,
    val moves: List<MoveDef>,
    val goldDrop: IntRange,
    val loot: LootTable? = null,
)

data class ExitDef(val label: String, val to: RoomId)

/**
 * A survival meter on the Ember-age game-time clock (MASTER_PLAN §16.10):
 * burns per action taken; while empty, each action costs hp. Real-time clocks
 * (Soot/Neon ages) extend this framework at M2+ — same defs, different regen
 * source.
 */
data class MeterDef(
    val id: MeterId,
    val name: String,
    val glyph: String,
    val cap: Int,
    val start: Int,
    val burnPerAction: Int,
    val emptyDamagePerAction: Int,
)

data class RoomDef(
    val id: RoomId,
    val name: String,
    val description: String,
    val exits: List<ExitDef> = emptyList(),
    val monster: MonsterId? = null,
    val hiddenItem: ItemId? = null,
    val searchText: String? = null,
    /** Camp is legal here: restores all meters to cap. */
    val haven: Boolean = false,
    val campText: String? = null,
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
    val meters: Map<MeterId, MeterDef> = emptyMap(),
    val abilities: Map<AbilityId, AbilityDef> = emptyMap(),
    val nodes: Map<NodeId, ConstellationNodeDef> = emptyMap(),
    val senses: Map<SenseId, SenseDef> = emptyMap(),
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
            if (monster.speed <= 0) problems += "monster '${monster.id.value}': speed must be positive"
            if (monster.speed > 1_000) problems += "monster '${monster.id.value}': speed above 1000 acts more than once per tick"
            if (monster.goldDrop.isEmpty()) problems += "monster '${monster.id.value}': empty goldDrop range"
            if (monster.goldDrop.first < 0) problems += "monster '${monster.id.value}': negative goldDrop"
            if (monster.goldDrop.last > GOLD_DROP_CAP) {
                // Also keeps the RNG bound math (range width + 1) far from Int overflow.
                problems += "monster '${monster.id.value}': goldDrop exceeds cap $GOLD_DROP_CAP"
            }
            if (monster.moves.isEmpty()) problems += "monster '${monster.id.value}': needs at least one move"
            if (monster.moves.map { it.id }.toSet().size != monster.moves.size) {
                problems += "monster '${monster.id.value}': duplicate move ids"
            }
            for (move in monster.moves) {
                if (move.weight <= 0) problems += "move '${move.id.value}': weight must be positive"
                if (move.powerNum <= 0 || move.powerDen <= 0) problems += "move '${move.id.value}': power must be positive"
                if (move.telegraph.isBlank()) problems += "move '${move.id.value}': blank telegraph"
                if (move.name.isBlank()) problems += "move '${move.id.value}': blank name"
            }
            if (monster.moves.sumOf { it.weight.toLong() } > 1_000_000L) {
                problems += "monster '${monster.id.value}': move weights sum too large"
            }
            monster.loot?.let { loot ->
                if (loot.chancePercent !in 1..100) problems += "monster '${monster.id.value}': loot chance outside 1..100"
                if (loot.entries.isEmpty()) problems += "monster '${monster.id.value}': empty loot table"
                for (entry in loot.entries) {
                    if (entry.weight <= 0) problems += "monster '${monster.id.value}': non-positive loot weight"
                    if (entry.item !in items) problems += "monster '${monster.id.value}': loot references missing item '${entry.item.value}'"
                }
                if (loot.entries.sumOf { it.weight.toLong() } > 1_000_000L) {
                    problems += "monster '${monster.id.value}': loot weights sum too large"
                }
            }
        }

        for (meter in meters.values) {
            if (meter.cap <= 0) problems += "meter '${meter.id.value}': cap must be positive"
            if (meter.start !in 0..meter.cap) problems += "meter '${meter.id.value}': start outside 0..cap"
            if (meter.burnPerAction < 0) problems += "meter '${meter.id.value}': negative burn"
            if (meter.emptyDamagePerAction < 0) problems += "meter '${meter.id.value}': negative empty damage"
            if (meter.name.isBlank() || meter.glyph.isBlank()) problems += "meter '${meter.id.value}': blank name or glyph"
        }
        if (meters.isNotEmpty() && rooms.values.none { it.haven }) {
            problems += "meters exist but no haven room to camp in — guaranteed death by attrition"
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

        for (ability in abilities.values) {
            if (ability.staminaCost < 0) problems += "ability '${ability.id.value}': negative stamina cost"
            if (ability.cooldownTurns < 0) problems += "ability '${ability.id.value}': negative cooldown"
            if (ability.name.isBlank()) problems += "ability '${ability.id.value}': blank name"
            when (val kind = ability.kind) {
                is AbilityKind.Strike -> {
                    if (kind.powerNum <= 0 || kind.powerDen <= 0) problems += "ability '${ability.id.value}': non-positive power"
                    if (kind.defenseIgnored < 0) problems += "ability '${ability.id.value}': negative defenseIgnored"
                }
                is AbilityKind.Heal -> {
                    if (kind.percentNum <= 0 || kind.percentDen <= 0) problems += "ability '${ability.id.value}': non-positive heal"
                }
                is AbilityKind.LifeStrike -> {
                    if (kind.powerNum <= 0 || kind.powerDen <= 0) problems += "ability '${ability.id.value}': non-positive power"
                    if (kind.healPercent !in 1..100) problems += "ability '${ability.id.value}': heal percent out of range"
                }
                is AbilityKind.Rout -> {
                    if (kind.chancePercent !in 1..100) problems += "ability '${ability.id.value}': rout chance out of range"
                }
                is AbilityKind.Terror -> {
                    if (kind.chancePercent !in 1..100) problems += "ability '${ability.id.value}': terror chance out of range"
                }
                is AbilityKind.MultiStrike -> {
                    if (kind.hits !in 1..12) problems += "ability '${ability.id.value}': hits out of range"
                }
                else -> {
                    // The remaining stateless kinds carry only positive scalars; a light guard.
                    if (ability.staminaCost > 500) problems += "ability '${ability.id.value}': stamina cost absurd"
                }
            }
        }

        for (node in nodes.values) {
            if (node.cost <= 0) problems += "node '${node.id.value}': cost must be positive"
            if (node.constellation.isBlank()) problems += "node '${node.id.value}': blank constellation"
            if (node.name.isBlank()) problems += "node '${node.id.value}': blank name"
            for (prereq in node.prereqs) {
                if (prereq !in nodes) problems += "node '${node.id.value}': missing prereq '${prereq.value}'"
            }
            when (val effect = node.effect) {
                is NodeEffect.GrantAbility ->
                    if (effect.ability !in abilities) problems += "node '${node.id.value}': grants missing ability '${effect.ability.value}'"
                is NodeEffect.GrantSense ->
                    if (effect.sense !in senses) problems += "node '${node.id.value}': grants missing sense '${effect.sense.value}'"
                is NodeEffect.GrantVerb ->
                    if (effect.verb != Verbs.FORAGE && effect.verb != Verbs.KINDLE)
                        problems += "node '${node.id.value}': grants unknown verb '${effect.verb.value}'"
                else -> {}
            }
        }
        // Prereq graph must be acyclic (DFS three-colour).
        val color = HashMap<NodeId, Int>()
        fun cyclic(id: NodeId): Boolean {
            when (color[id]) {
                1 -> return true
                2 -> return false
            }
            color[id] = 1
            for (prereq in nodes[id]?.prereqs.orEmpty()) {
                if (prereq in nodes && cyclic(prereq)) return true
            }
            color[id] = 2
            return false
        }
        for (node in nodes.values) {
            if (cyclic(node.id)) {
                problems += "node '${node.id.value}': prerequisite cycle"
                break
            }
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
