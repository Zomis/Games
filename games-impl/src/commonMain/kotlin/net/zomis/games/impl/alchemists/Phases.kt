package net.zomis.games.impl.alchemists

object Phases {

    sealed interface Phase {
        object Setup : Phase
        class ChooseTurnOrder(val round: Int) : Phase
        class PlaceActions(val round: Int) : Phase
        class ResolveActions(val round: Int) : Phase
        object BigRevelation : Phase
    }

    val phases: Sequence<Phase> = sequence {
        yield(Phase.Setup)
        for (it in 1 until 6) {
            yield(Phase.ChooseTurnOrder(it))
            yield(Phase.PlaceActions(it))
            yield(Phase.ResolveActions(it))
        }
        yield(Phase.BigRevelation)
    }

}