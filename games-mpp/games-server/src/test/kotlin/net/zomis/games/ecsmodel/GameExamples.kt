package net.zomis.games.ecsmodel

object GameExamples {

    context(Game)
    class ObservableStateExample : GameModelEntity() {
        var value by property { 0 }
    }

    val game = EcsGameApi.create("observable-state") {
        playerCount(1..2)
        create {
            ObservableStateExample()
        }
    }

}