package net.zomis.games.server2.invites

import net.zomis.core.events.EventSystem
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class EventsExpect : AfterEachCallback {
    override fun afterEach(context: ExtensionContext?) {
        expectations.forEach { it.checkCondition() }
    }

    class Expectation<T> {

        private var triggered = AtomicInteger()
        private var condition: (T) -> Boolean = { true }

        fun condition(condition: (T) -> Boolean): Expectation<T> {
            val previousCondition = this.condition
            this.condition = { previousCondition(it) && condition.invoke(it) }
            return this
        }

        fun after(block: () -> Unit) {
            block.invoke()
            checkCondition()
        }

        fun eventTriggered(event: T) {
            if (!condition.invoke(event)) {
                return
            }
            triggered.getAndIncrement()
        }

        fun checkCondition() {
            Assertions.assertEquals(1, triggered.get())
        }

    }

    private val expectations = mutableListOf<Expectation<*>>()

    fun <T : Any> event(event: Pair<EventSystem, KClass<T>>): Expectation<T> {
        val expectation = Expectation<T>()
        expectations.add(expectation)
        event.first.listen("expect $expectation", event.second, { true }, expectation::eventTriggered)
        return expectation
    }

}
