package net.zomis.games.components.resources

// Triggers: "When you lose more than 3 gold", get 1 gold.
// "When you gain gold", draw a card.
// "When you gain gold", also gain 2 trees.

interface ResourceListener {
    fun onChange(resource: GameResource, oldValue: Int, newValue: Int): Int = newValue
    fun keepListener(): Boolean = true
}

interface ListenableResourceMap {
    fun addListener(listener: ResourceListener)
}
