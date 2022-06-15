package net.zomis.games.impl.alchemists

typealias SpacePlacementRule<T, U> = ActionDrafting.SpacePlacementScope<T, U>.() -> Boolean

/**
 * Common classes for placing units into spots
 *
 * With support for different types of units and putting multiple units into a spot
 */
object ActionDrafting {
    class Drafting<T, U>(val spaces: List<Space<T, U>>) {
        fun zoneOptions(playerIndex: Int, units: List<U>): Iterable<Placement<T, U>> {
            val approvedContexts = allContexts(playerIndex, units).filter { it.isAllowed() }
            return approvedContexts.map { it.toPlacement() }
        }

        fun allowed(playerIndex: Int, zone: T, unit: U): Boolean {
            val context = SpacePlacementContext(playerIndex, spaces.single { it.zone == zone }, unit)
            return context.space.placementRule.invoke(context)
        }

        fun makePlacement(playerIndex: Int, zone: T, unit: U) {
            val context = SpacePlacementContext(playerIndex, spaces.single { it.zone == zone }, unit)
            context.space.placementsMade.add(context.toPlacement())
        }

        private fun allContexts(playerIndex: Int, units: List<U>): Iterable<SpacePlacementContext<T, U>> {
            return spaces.flatMap {space ->
                units.map {unit ->
                    SpacePlacementContext(playerIndex, space, unit)
                }
            }
        }
    }

    interface SpacePlacementScope<T, U> {
        val playerIndex: Int
        val space: Space<T, U>
        val unit: U
        val zone: T get() = space.zone
    }
    class SpacePlacementContext<T, U>(
        override val playerIndex: Int, override val space: Space<T, U>, override val unit: U
    ): SpacePlacementScope<T, U> {
        fun isAllowed(): Boolean = space.placementRule.invoke(this)
        fun toPlacement(): Placement<T, U> = Placement(playerIndex, space, unit)
    }

    class Space<T, U>(val zone: T, val placementRule: SpacePlacementRule<T, U>) {
        val placementsMade: MutableList<Placement<T, U>> = mutableListOf()

        fun placementsByPlayer(playerIndex: Int): List<Placement<T, U>> = placementsMade.filter { it.playerIndex == playerIndex }
    }
    class Placement<T, U>(val playerIndex: Int, val space: Space<T, U>, val unit: U)

}
