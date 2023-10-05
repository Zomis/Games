package net.zomis.games.components.resources

import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.Replayable
import net.zomis.games.dsl.Viewable
import net.zomis.games.dsl.events.EmptyEventFactory
import net.zomis.games.dsl.events.EventFactory

// TODO: DynamicResourceMap: Have two different resource maps, one for original and one for modifiers?

interface ResourceMap: Replayable {
    companion object {
        fun empty(): ResourceMap = ResourceMapImpl(mutableMapOf())
        fun of(vararg resources: Pair<GameResource, Int>) = resources.fold(empty()) { acc, pair ->
            acc + pair.first.toResourceMap(pair.second)
        }
        fun fromList(list: List<GameResource>): ResourceMap = list.fold(empty(), ResourceMap::plus)
    }
    operator fun get(resource: GameResource): Int?
    fun getOrDefault(resource: GameResource): Int
    fun getValue(resource: GameResource): Int
    operator fun plus(other: ResourceMap): ResourceMap
    operator fun plus(other: GameResource): ResourceMap
    operator fun minus(other: ResourceMap): ResourceMap
    operator fun times(value: Int): ResourceMap
    operator fun div(other: ResourceMap): Int {
        var times = 0
        var tmp = this
        while (tmp.has(other)) {
            times += 1
            tmp -= other
        }
        return times
    }

    fun has(resource: GameResource, value: Int): Boolean
    fun count(): Int = this.entries().sumOf { it.value }

    fun has(resources: ResourceMap): Boolean
    fun resources(): Set<GameResource>

    fun entries(): Set<ResourceEntry>
    fun merge(other: ResourceMap, merger: (owned: Int?, otherValue: Int?) -> Int): ResourceMap
    fun map(function: (ResourceEntry) -> Pair<GameResource, Int>): ResourceMap
    fun filter(function: (ResourceEntry) -> Boolean): ResourceMap
    fun <R> fold(initial: R, operation: (acc: R, ResourceEntry) -> R): R

    operator fun unaryMinus(): ResourceMap
    fun any(): Boolean = entries().any { it.value != 0 }
    fun toView(): Map<String, Int> = entries().associate { it.resource.name to it.value }
    fun isEmpty(): Boolean = entries().all { it.value == it.resource.defaultValue() }
    fun toMutableResourceMap(eventFactory: EventFactory<ResourceChange> = EmptyEventFactory()): MutableResourceMap
        = ResourceMapImpl(entries().map { it.resource to it.value }, eventFactory)

    fun toMap(): Map<GameResource, Int> = entries().associate { it.resource to it.value }
}

interface MutableResourceMap: ResourceMap {
    operator fun set(resource: GameResource, value: Int)
    operator fun plusAssign(other: ResourceMap)
    operator fun minusAssign(other: ResourceMap)
    operator fun timesAssign(value: Int)
    fun clear(resource: GameResource)

    /**
     * Moves all resources that are possible to move. Ignores any modifiers on this object.
     */
    fun payTo(resources: ResourceMap, destination: MutableResourceMap): Boolean
}

// "For the next 5 turns, you always have one wildcard to spend" --> Doesn't make sense if it would be moved, as it doesn't really exist.
// "For the next 5 turns, cards you buy costs one less of any color" --> Makes a lot more sense, has the same effect.

// Resource aliases (not immediate problem)

// Dynamic retrieval, modifiers.

// See Splendor Money and Caravan in Spice Road

data class ResourceChange(val resourceMap: ResourceMap, val resource: GameResource, val oldValue: Int, var newValue: Int) {
    val diff: Int = newValue - oldValue
}

class ResourceMapImpl(
    private val resources: MutableMap<GameResource, ResourceEntryImpl> = mutableMapOf(),
    private val eventFactory: EventFactory<ResourceChange> = EmptyEventFactory(),
): MutableResourceMap, ResourceMap, Viewable {

    constructor(resources: Iterable<Pair<GameResource, Int>>, eventFactory: EventFactory<ResourceChange>) : this(
        resources.associate { it.first to ResourceEntryImpl(it.first, it.second) }.toMutableMap(),
        eventFactory
    )

    override fun get(resource: GameResource): Int? = resources[resource]?.value
    override fun getOrDefault(resource: GameResource): Int = resources[resource]?.value ?: resource.defaultValue()
    override fun getValue(resource: GameResource): Int = resources.getValue(resource).value

    override fun plus(other: ResourceMap): ResourceMap = this.merge(other) { owned, otherValue ->
        (owned ?: 0) + (otherValue ?: 0)
    }

    override fun plus(other: GameResource): ResourceMap = this.plus(other.toResourceMap())

    override fun minus(other: ResourceMap): ResourceMap = this.merge(other) { owned, otherValue ->
        (owned ?: 0) - (otherValue ?: 0)
    }

    override fun times(value: Int): ResourceMap = this.map { it.resource to it.value * value }

    override fun has(resource: GameResource, value: Int): Boolean = this.getOrDefault(resource) >= value

    override fun has(resources: ResourceMap): Boolean = resources.entries().all { this.has(it.resource, it.value) }

    override fun resources(): Set<GameResource> = this.resources.keys.toSet()
    override fun entries(): Set<ResourceEntry> = this.resources.values.toSet()

    override fun merge(other: ResourceMap, merger: (owned: Int?, otherValue: Int?) -> Int): ResourceMap {
        val allResources = this.resources() + other.resources()
        return ResourceMapImpl(allResources.associateWith { resource ->
            ResourceEntryImpl(resource, merger.invoke(this[resource], other[resource]))
        }.toMutableMap())
    }

    override fun map(function: (ResourceEntry) -> Pair<GameResource, Int>): ResourceMap {
        return ResourceMapImpl(this.resources.entries.associate { entry ->
            function.invoke(entry.value).let { it.first to ResourceEntryImpl(it.first, it.second) }
        }.toMutableMap())
    }

    override fun filter(function: (ResourceEntry) -> Boolean): ResourceMap {
        return ResourceMapImpl(this.resources.filter { function.invoke(it.value) }.toMutableMap())
    }

    override fun <R> fold(initial: R, operation: (acc: R, ResourceEntry) -> R): R {
        return entries().fold(initial, operation)
    }

    override fun unaryMinus(): ResourceMap = this.map { it.resource to -it.value }
    override fun toStateString(): String = this.resources.entries.sortedBy { it.key.toString() }.joinToString(",") {
        "${it.key}/${it.value.value}"
    }

    private fun enforceResource(resource: GameResource): ResourceEntryImpl = this.resources.getOrPut(resource) { ResourceEntryImpl(resource, resource.defaultValue()) }

    private fun changeResource(resource: GameResource, change: (Int) -> Int) {
        val oldValue = getOrDefault(resource)
        this.eventFactory.invoke(ResourceChange(this, resource, oldValue, change.invoke(oldValue))) {
            enforceResource(resource).value = it.newValue
        }
    }

    override fun set(resource: GameResource, value: Int) = changeResource(resource) { value }

    override fun plusAssign(other: ResourceMap) {
        other.entries().forEach { entry ->
            changeResource(entry.resource) { it + entry.value }
        }
    }

    override fun minusAssign(other: ResourceMap) {
        other.entries().forEach { entry ->
            changeResource(entry.resource) { it - entry.value }
        }
    }

    override fun timesAssign(value: Int) {
        entries().forEach { entry ->
            changeResource(entry.resource) { it * value }
        }
    }

    override fun clear(resource: GameResource) {
        eventFactory.invoke(ResourceChange(this, resource, getOrDefault(resource), resource.defaultValue())) {
            resources.remove(resource)
        }
    }

    override fun payTo(resources: ResourceMap, destination: MutableResourceMap): Boolean {
        if (!this.has(resources)) return false
        // NOTE: This may cause issues when using events that modify values. This is not performed as a single transaction.
        this -= resources
        destination += resources
        return true
    }

    override fun hashCode(): Int {
        return this.resources.hashCode()
    }

    override fun toView(viewer: PlayerIndex): Map<GameResource, Int>
        = this.entries().associate { it.resource to it.value }

    override fun equals(other: Any?): Boolean {
        if (other !is ResourceMap) return false
        return other.entries() == this.entries()
    }

    override fun toString(): String = entries().toString()

}
