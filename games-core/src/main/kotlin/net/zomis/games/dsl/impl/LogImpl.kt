package net.zomis.games.dsl.impl

import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.LogActionScope
import net.zomis.games.dsl.LogScope
import net.zomis.games.dsl.LogSecretActionScope
import net.zomis.games.dsl.LogSecretScope

data class LogPartPlayer(val playerIndex: PlayerIndex): LogPart {
    override val type: String = "player"
}
data class LogPartText(val text: String): LogPart {
    override val type: String = "text"
}
data class LogPartHighlight(val value: Any): LogPart {
    override val type: String = "highlight"
}
data class LogPartInline(val viewType: String, val data: Any): LogPart {
    override val type: String = "inline"
}
data class LogPartLink(val text: String, val viewType: String, val value: Any): LogPart {
    override val type: String = "link"
}

interface LogPart { val type: String }
data class LogEntry(val parts: List<LogPart>, val highlights: List<Any>, val private: Boolean)
interface ActionLogEntry {
    val playerIndex: PlayerIndex
    val secret: LogEntry?
    val public: LogEntry?

    fun forPlayer(index: Int): LogEntry? {
        return if (index == playerIndex) secret?:public else public
    }

}

class LogContext<T : Any>(
    override val game: T,
    override val playerIndex: PlayerIndex
): LogScope<T>, LogSecretScope<T>, ActionLogEntry {

    var counter: Int = 0
    val parts = mutableListOf<LogPart>()
    @Deprecated("to be removed")
    val highlights = mutableListOf<Any>()
    override val public: LogEntry? get() = publicEntry
    override val secret: LogEntry? get() = secretEntry
    var secretEntry: LogEntry? = null
    var publicEntry: LogEntry? = null
    fun part(partFunction: () -> LogPart): String {
        val oldCounter = counter++
        parts.add(partFunction())
        return "part:{{$oldCounter}}"
    }
    fun postProcess(entry: String?, secret: Boolean): LogEntry? {
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
        return LogEntry(parts.toList(), highlights.toList(), secret)
    }

    override fun players(playerIndices: Iterable<Int>): String {
        return playerIndices.joinToString("") { player(it) }
    }

    override fun obj(value: Any): String = part { LogPartHighlight(value) }
    override fun inline(type: String, data: Any): String = part { LogPartInline(type, data) }
    override fun player(value: PlayerIndex): String = part { LogPartPlayer(value) }
    override fun viewLink(text: String, type: String, view: Any): String = part { LogPartLink(text, type, view) }
    override fun highlight(values: List<Any>) {
        highlights.addAll(values)
    }
    override fun publicLog(logging: LogScope<T>.() -> String) { log(logging) }

    fun log(logging: LogScope<T>.() -> String): ActionLogEntry {
        highlights.clear()
        publicEntry = postProcess(logging(this), false)
        return this
    }

    fun secretLog(secretPlayer: PlayerIndex, logging: LogScope<T>.() -> String): LogContext<T> {
        highlights.clear()
        if (this.playerIndex == secretPlayer) {
            secretEntry = postProcess(logging(this), true)
        }
        return this
    }

    override fun toString(): String = "LogContext[private $secretEntry public $publicEntry]"

}

class LogActionContext<T : Any, A : Any>(
    override val game: T,
    override val playerIndex: PlayerIndex,
    override val action: A
): LogActionScope<T, A>, LogSecretActionScope<T, A>, ActionLogEntry {

    private val logScope = LogContext(game, playerIndex)

    override val player: String get() = this.player(playerIndex)
    override fun publicLog(logging: LogActionScope<T, A>.() -> String) { log(logging) }

    fun log(logging: LogActionScope<T, A>.() -> String): ActionLogEntry {
        logScope.highlights.clear()
        logScope.publicEntry = logScope.postProcess(logging(this), false)
        return this
    }

    fun secretLog(secretPlayer: PlayerIndex, logging: LogActionScope<T, A>.() -> String): LogActionContext<T, A> {
        logScope.highlights.clear()
        if (this.playerIndex == secretPlayer) {
            logScope.secretEntry = logScope.postProcess(logging(this), true)
        }
        return this
    }

    override val secret: LogEntry? get() = logScope.secret
    override val public: LogEntry? get() = logScope.public

    override fun toString(): String = "LogContext[private $secret public $public]"
    override fun obj(value: Any): String = logScope.obj(value)
    override fun inline(type: String, data: Any): String = logScope.inline(type, data)
    override fun player(value: PlayerIndex): String = logScope.player(value)
    override fun players(playerIndices: Iterable<Int>): String = logScope.players(playerIndices)
    override fun viewLink(text: String, type: String, view: Any): String = logScope.viewLink(text, type, view)
    override fun highlight(values: List<Any>) = logScope.highlight(values)

}
