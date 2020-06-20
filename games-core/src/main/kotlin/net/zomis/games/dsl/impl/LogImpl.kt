package net.zomis.games.dsl.impl

import net.zomis.games.dsl.LogActionScope
import net.zomis.games.dsl.PlayerIndex
import net.zomis.games.dsl.SecretLogging

data class LogPartPlayer(val playerIndex: PlayerIndex): LogPart {
    override val type: String = "player"
}
data class LogPartText(val text: String): LogPart {
    override val type: String = "text"
}
data class LogPartHighlight(val value: Any): LogPart {
    override val type: String = "highlight"
}
data class LogPartLink(val text: String, val viewType: String, val value: Any): LogPart {
    override val type: String = "link"
}

interface LogPart { val type: String }
data class LogEntry(val parts: List<LogPart>)
interface ActionLogEntry {
    val playerIndex: PlayerIndex
    val secret: LogEntry?
    val public: LogEntry?

    fun forPlayer(index: Int): LogEntry? {
        return if (index == playerIndex) secret?:public else public
    }

}

class LogActionContext<T : Any, A : Any>(
    override val game: T,
    override val playerIndex: PlayerIndex,
    override val action: A
): LogActionScope<T, A>, SecretLogging<T, A>, ActionLogEntry {

    var counter: Int = 0
    val parts = mutableListOf<LogPart>()
    override val public: LogEntry? get() = postProcess(publicEntry)
    override val secret: LogEntry? get() = postProcess(secretEntry)
    var secretEntry: String? = null
    var publicEntry: String? = null
    fun part(partFunction: () -> LogPart): String {
        val oldCounter = counter++
        parts.add(partFunction())
        return "part:{{$oldCounter}}"
    }
    private fun postProcess(entry: String?): LogEntry? {
        if (entry == null) return null
        val parts = mutableListOf<LogPart>()
        var textToParse = entry!!
        while (textToParse.contains("part:{{")) {
            val prefix = textToParse.substringBefore("part:{{")
            if (prefix.isNotEmpty()) parts.add(LogPartText(prefix))

            textToParse = textToParse.substringAfter("part:{{")
            val nextPart = this.parts[textToParse.substringBefore("}}").toInt()]
            parts.add(nextPart)
            textToParse = textToParse.substringAfter("}}")
        }
        if (textToParse.isNotEmpty()) parts.add(LogPartText(textToParse))
        return LogEntry(parts.toList())
    }

    override val player: String get() = this.player(playerIndex)

    override fun obj(value: Any): String = part { LogPartHighlight(value) }
    override fun player(value: PlayerIndex): String = part { LogPartPlayer(value) }
    override fun viewLink(text: String, type: String, view: Any): String = part { LogPartLink(text, type, view) }
    override fun highlight(values: List<*>) = TODO()
    override fun publicLog(logging: LogActionScope<T, A>.() -> String) { log(logging) }

    fun log(logging: LogActionScope<T, A>.() -> String): ActionLogEntry {
        publicEntry = logging(this)
        return this
    }

    fun secretLog(secretPlayer: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogActionContext<T, A> {
        if (this.playerIndex == secretPlayer) {
            secretEntry = logging(this)
        }
        return this
    }

}
