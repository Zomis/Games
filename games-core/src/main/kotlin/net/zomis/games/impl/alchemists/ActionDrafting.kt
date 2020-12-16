package net.zomis.games.impl.alchemists

typealias SpacePlacementRule<T, U> = ActionDrafting.SpacePlacementScope<T, U>.() -> ActionDrafting.PlacementResult
/**
 * Common classes for placing units into spots
 *
 * With support for different types of units, putting multiple units into a spot
 */
object ActionDrafting {
    class Drafting<T, U>(val spaces: List<Space<T, U>>)
    enum class PlacementResult {
        REJECTED,
        PARTIAL,
        ACCEPTED,
        ;
    }
    interface SpacePlacementScope<T, U> {
        val playerIndex: Int
        val space: Space<T, U>
        val unit: U
        val zone: T get() = space.zone
    }
    class Space<T, U>(val zone: T, val placementRule: SpacePlacementRule<T, U>) {
        val placementsMade: MutableList<Placement<T, U>> = mutableListOf()

        fun placementsByPlayer(playerIndex: Int): List<Placement<T, U>> = placementsMade.filter { it.playerIndex == playerIndex }
    }
    class Placement<T, U>(val playerIndex: Int, val space: Space<T, U>, val unit: U)

}
class AlchemistsActionChoice(val playerIndex: Int, val assistants: Int, val placements: List<ActionDrafting.Placement<AlchemistsModel.ActionType, Boolean>>)
