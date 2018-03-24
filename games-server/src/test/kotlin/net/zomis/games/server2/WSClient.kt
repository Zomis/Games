package net.zomis.games.server2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.IOException
import java.net.URI
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

fun ObjectNode.getInt(fieldName: String): Int {
    assert(this.hasNonNull(fieldName), { "ObjectNode does not have field '$fieldName': $this" })
    return this.get(fieldName).asInt()
}

fun ObjectNode.getText(fieldName: String): String {
    assert(this.hasNonNull(fieldName), { "ObjectNode does not have field '$fieldName': $this" })
    return this.get(fieldName).asText()
}

class WSClient(uri: URI): WebSocketClient(uri) {
    private val logger = klogging.KLoggers.logger(this)

    private val queue: BlockingQueue<String> = LinkedBlockingQueue<String>()
    private val mapper = ObjectMapper()

    override fun onOpen(handshakedata: ServerHandshake?) {
        logger.info { "Open: $handshakedata" }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        logger.info { "Close: code $code reason $reason remote $remote" }
    }

    override fun onMessage(message: String?) {
        logger.info { "Recieved: $message" }
        queue.offer(message)
    }

    override fun onError(ex: Exception?) {
        logger.info { "Error: $ex" }
    }

    fun expectExact(expected: String) {
        val text = queue.take()
        assert(text == expected, { "Expected '$expected' but was '$text'" })
    }

    override fun send(text: String?) {
        logger.info { "Sending $text" }
        super.send(text)
    }

    fun expectJsonObject(predicate: (ObjectNode) -> Boolean): ObjectNode {
        val text = queue.take()
        val node = mapper.readTree(text) as ObjectNode
        assert(predicate.invoke(node), { "Unexpected data for $node" })
        return node
    }

    fun takeUntilJson(predicate: (ObjectNode) -> Boolean): ObjectNode {
        while (true) {
            val text = queue.poll(1, TimeUnit.MINUTES)
            try {
                val node = mapper.readTree(text) as ObjectNode
                if (predicate.invoke(node)) {
                    return node
                }
            } catch (e: IOException) {
                // ignored
            }
        }
    }

    fun sendAndExpectResponse(data: String) {
        send(data)
        while (queue.isEmpty()) {
            Thread.sleep(10)
        }
    }

}
