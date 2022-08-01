package net.zomis.games.server2

typealias MessageRouterDynamic<T> = (String) -> MessageRouter<T>
typealias MessageRouterHandler<T> = (T) -> Unit

private const val DELIMITER = '/'
class MessageRouter<T>(owner: T?) {

    private var dynamic: MessageRouterDynamic<Any>? = null
    private val routes: MutableMap<String, MessageRouter<Any>> = mutableMapOf()
    private val handlers: MutableMap<String, MessageRouterHandler<Any>> = mutableMapOf()

    fun <U : Any> route(group: String, next: MessageRouter<U>): MessageRouter<T> {
        routes[group] = next as MessageRouter<Any>
        return this
    }

    fun <U : Any> handler(key: String, handler: MessageRouterHandler<U>): MessageRouter<T> {
        if (key.contains(DELIMITER)) {
            val next = key.substringBefore(DELIMITER)
            val nextRouter = this.routes[next] ?: this.dynamic?.invoke(next) ?: throw IllegalArgumentException("No router for $key. Routes: ${routes.keys}")
            nextRouter.handler(key.substringAfter(DELIMITER), handler)
            return this
        }
        this.handlers[key] = handler as MessageRouterHandler<Any>
        return this
    }

    fun <U : Any> dynamic(dynamicHandler: MessageRouterDynamic<U>): MessageRouter<T> {
        this.dynamic = dynamicHandler as MessageRouterDynamic<Any>
        return this
    }

    fun <U : Any> handle(message: String, data: U) {
        if (message.contains(DELIMITER)) {
            val next = message.substringBefore(DELIMITER)
            val nextRouter = this.routes[next] ?: this.dynamic?.invoke(next) ?: throw IllegalArgumentException("Unable to route: $message. Routes: ${routes.keys}")
            nextRouter.handle(message.substringAfter(DELIMITER), data)
            return
        }
        synchronized(this.handlers) {
            this.handlers[message]?.invoke(data) ?: throw IllegalArgumentException("Unable to handle: $message. Handlers: ${handlers.keys}")
        }
    }

}