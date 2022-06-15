package net.zomis.games

import klog.KLoggers
import net.zomis.core.events.EventSystem
import kotlin.reflect.KClass

// TODO: Is it possible to use a FeatureKey<T> and pass that to dependent features? Then use this key to fetch with some typesafety
// TODO: Maybe even FeatureKey<T, R> to know what the feature was added *to* as well?
typealias Feature = (Features, EventSystem) -> Unit

/**
 * Acts as both a data storage and a way to plugin new event listeners.
 */
class Features(val events: EventSystem?) {

    private val logger = KLoggers.logger(this)
    val data = mutableSetOf<Any>()

    operator fun <T: Any> get(clazz: KClass<T>): T? {
        val value = data.find { clazz.isInstance(it) }
        return value as T?
    }

    fun <T: Any> addData(dataToAdd: T): T {
        logger.info("$this adding data $dataToAdd")
        data.add(dataToAdd)
        return dataToAdd
    }

    fun add(feature: Feature): Features {
        feature(this, events!!)
        return this
    }

}
