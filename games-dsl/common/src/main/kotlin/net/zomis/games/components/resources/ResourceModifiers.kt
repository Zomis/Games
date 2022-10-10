package net.zomis.games.components.resources

interface ResourceModifier {
    val resource: GameResource
    val priority: Int
    val description: Any?
    val activeCondition: () -> Boolean
    val modifier: (Int) -> Int
}

interface ModifiableResourceMap {
    fun hasModified(resources: ResourceMap): Boolean
    fun hasModified(resource: GameResource, value: Int): Boolean
    fun getModified(resource: GameResource): Int
}
