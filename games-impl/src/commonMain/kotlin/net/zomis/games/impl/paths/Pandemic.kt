package net.zomis.games.impl.paths

import net.zomis.games.api.GamesApi
import net.zomis.games.components.Graph
import net.zomis.games.components.Paths

object Pandemic {

    class Node(val name: String)
    class Model() {
        lateinit var graph: Graph<Node, Unit>
    }

    val factory = GamesApi.gameCreator(Model::class)
    val game = factory.game("Pandemic") {
        setup {
            players(2..4)
            init {
                Model().also { model ->
                    /*
                    * Blue:
                    * San Francisco, Chicago, Montreal, Atlanta, Washington, New York,
                    * London, Madrid, Paris, Essen, St. Petersburg, Milan
                    *
                    * Yellow:
                    * Los Angeles, Mexico City, Miami, Bogota, Lima, Santiago, Buenos Aires,
                    * Sao Paulo, Lagos, Karthoum, Johannesburg, Kinshasa
                    *
                    * Red:
                    *
                    *
                    * Black:
                    */
                    // https://public.tableau.com/app/profile/kevin.flerlage/viz/PandemicBoardGameMapCreatedFeb2020/Map
                    model.graph = Paths.graph<String, Unit>().edgeValues { _, _ -> Unit }
                        .connectBothWays("Santiago" to "Lima")
                        .connectBothWays("Lima" to "Mexico City", "Bogota")
                        .connectBothWays("Mexico City" to "Bogota", "Los Angeles", "Miami", "Chicago")
                        .connectBothWays("Los Angeles" to "San Francisco", "Chicago", "Mexico City")
                        .connectBothWays("Miami" to "Atlanta", "Washington", "Bogota")
                        .connectBothWays("Bogota" to "Mexico City", "Miami", "Lima", "Sao Paulo", "Buenos Aires")
                        .connectBothWays("Sao Paulo" to "Madrid", "Lagos", "Buenos Aires")
                        .connectBothWays("Lagos" to "Kinshasa", "Khartoum")
                        .connectBothWays("Chicago" to "San Francisco", "Montreal", "Atlanta", "Los Angeles", "Mexico City")
                        .connectBothWays("Kinshasa" to "Lagos", "Khartoum", "Johannesburg")
                        .chain(true, listOf("Johannesburg", "Khartoum", "Cairo"))
                        .build().transform({ Node(it) }, {  })

                }
            }
        }
    }

}