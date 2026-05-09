package mimi.core.crl

import mimi.core.command.Command
import mimi.core.model.*
import mimi.core.plugin.EngineFieldPolicies

object ConflictResolutionLayer {

    fun resolve(
        commands:       List<Command>,
        sceneOverrides: Map<PropertyKey, ResolutionPolicy> = emptyMap()
    ): Pair<List<Command>, List<Command.TriggerFeedback>> {
        val feedback  = commands.filterIsInstance<Command.TriggerFeedback>()
        val regular   = commands.filterNot { it is Command.TriggerFeedback }

        val resolved = regular
            .groupBy { it.target to it.field }
            .flatMap { (_, group) -> resolveGroup(group, sceneOverrides) }

        return resolved to feedback
    }

    private fun resolveGroup(
        group:          List<Command>,
        sceneOverrides: Map<PropertyKey, ResolutionPolicy>
    ): List<Command> {
        if (group.size == 1) return group

        // IncrementCounter commands always merge (summation is their semantic contract)
        if (group.all { it is Command.IncrementCounter }) return listOf(merge(group))

        val field  = group.first().field
        val policy = EngineFieldPolicies.resolve(field, sceneOverrides)

        return when (policy) {
            ResolutionPolicy.PRIORITY   -> listOf(group.maxByOrNull { it.priority } ?: group.first())
            ResolutionPolicy.MERGE      -> listOf(merge(group))
            ResolutionPolicy.LAST_WINS  -> listOf(group.last())
            ResolutionPolicy.FIRST_WINS -> listOf(group.first())
        }
    }

    private fun merge(group: List<Command>): Command {
        // IncrementCounter: sum all deltas
        val increments = group.filterIsInstance<Command.IncrementCounter>()
        if (increments.size == group.size) {
            return increments.first().copy(delta = increments.sumOf { it.delta })
        }

        // SetProperty with Num: sum values
        val setNums = group.filterIsInstance<Command.SetProperty>()
            .filter { it.value is PropertyValue.Num }
        if (setNums.size == group.size) {
            val total = setNums.sumOf { (it.value as PropertyValue.Num).value }
            return setNums.last().copy(value = PropertyValue.Num(total))
        }

        // SetProperty with Flag: OR
        val setFlags = group.filterIsInstance<Command.SetProperty>()
            .filter { it.value is PropertyValue.Flag }
        if (setFlags.size == group.size) {
            val result = setFlags.any { (it.value as PropertyValue.Flag).value }
            return setFlags.last().copy(value = PropertyValue.Flag(result))
        }

        // SetProperty with TextList: union
        val setLists = group.filterIsInstance<Command.SetProperty>()
            .filter { it.value is PropertyValue.TextList }
        if (setLists.size == group.size) {
            val union = setLists.flatMap { (it.value as PropertyValue.TextList).values }.distinct()
            return setLists.last().copy(value = PropertyValue.TextList(union))
        }

        // Fallback: last wins
        return group.last()
    }
}
