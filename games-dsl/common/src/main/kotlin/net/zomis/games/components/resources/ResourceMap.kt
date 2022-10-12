package net.zomis.games.components.resources

interface ResourceMap {
    // val owner: T // TODO: Does this make any sense? Considering the messes of generics that would occur below? What would the concrete use-case be?
    operator fun get(resource: GameResource): Int?
    fun getOrDefault(resource: GameResource): Int
    fun getValue(resource: GameResource): Int
    operator fun plus(other: ResourceMap): ResourceMap
    operator fun minus(other: ResourceMap): ResourceMap
    operator fun times(value: Int): ResourceMap
    fun has(resource: GameResource, value: Int): Boolean

    fun has(resources: ResourceMap): Boolean
    fun resources(): Set<GameResource>

    fun entries(): Set<ResourceEntry>
    fun merge(other: ResourceMap, merger: (owned: Int?, otherValue: Int?) -> Int): ResourceMap
    fun map(function: (ResourceEntry) -> Pair<GameResource, Int>): ResourceMap
    fun filter(function: (ResourceEntry) -> Boolean): ResourceMap
    fun <R> fold(initial: R, operation: (acc: R, ResourceEntry) -> R): R

    operator fun unaryMinus(): ResourceMap
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

class ResourceMapImpl(
    private val resources: MutableMap<GameResource, ResourceEntryImpl> = mutableMapOf()
): MutableResourceMap, ResourceMap {

    override fun get(resource: GameResource): Int? = resources[resource]?.value
    override fun getOrDefault(resource: GameResource): Int = resources[resource]?.value ?: resource.defaultValue()
    override fun getValue(resource: GameResource): Int = resources.getValue(resource).value

    override fun plus(other: ResourceMap): ResourceMap = this.merge(other) { owned, otherValue ->
        (owned ?: 0) + (otherValue ?: 0)
    }

    override fun minus(other: ResourceMap): ResourceMap = this.merge(other) { owned, otherValue ->
        (owned ?: 0) - (otherValue ?: 0)
    }

    override fun times(value: Int): ResourceMap = this.map { it.resource to it.value * value }

    override fun has(resource: GameResource, value: Int): Boolean = this.getOrDefault(resource) >= value

    override fun has(resources: ResourceMap): Boolean = this.resources.all { it.value.value >= resources.getOrDefault(it.key) }

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

    private inline fun enforceResource(resource: GameResource): ResourceEntryImpl = this.resources.getOrPut(resource) { ResourceEntryImpl(resource, resource.defaultValue()) }

    override fun set(resource: GameResource, value: Int) {
        enforceResource(resource).value = value
    }

    override fun plusAssign(other: ResourceMap) {
        other.entries().forEach {
            this.enforceResource(it.resource) += it.value
        }
    }

    override fun minusAssign(other: ResourceMap) {
        other.entries().forEach {
            this.enforceResource(it.resource) -= it.value
        }
    }

    override fun timesAssign(value: Int) {
        entries().map { it as MutableResourceEntry }.forEach {
            it.value *= value
        }
    }

    override fun clear(resource: GameResource) {
        this.resources.remove(resource)
    }

    override fun payTo(resources: ResourceMap, destination: MutableResourceMap): Boolean {
        if (!this.has(resources)) return false
        this -= resources
        destination += resources
        return true
    }

}

