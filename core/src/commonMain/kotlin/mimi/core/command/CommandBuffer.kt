package mimi.core.command

interface CommandBuffer {
    fun add(command: Command)
    fun drain(): List<Command>
    fun isEmpty(): Boolean
}

class DefaultCommandBuffer : CommandBuffer {
    private val commands = mutableListOf<Command>()

    override fun add(command: Command) { commands.add(command) }

    override fun drain(): List<Command> {
        val snapshot = commands.toList()
        commands.clear()
        return snapshot
    }

    override fun isEmpty(): Boolean = commands.isEmpty()
}
