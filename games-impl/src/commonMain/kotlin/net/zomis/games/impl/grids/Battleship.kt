package net.zomis.games.impl.grids

import net.zomis.games.WinResult
import net.zomis.games.api.Games
import net.zomis.games.api.components
import net.zomis.games.common.Point
import net.zomis.games.common.Rect

object Battleship {

    data class ShipSpecification(val name: String, val size: Int)
    data class ShipSpecifications(val ships: List<ShipSpecification>)
    class Player(size: Point, val playerIndex: Int, ships: ShipSpecifications) {
        val field = Games.components.grid(size.x, size.y) { _, _ -> "" } // Use a better data type at some point?
        val placementsRemaining = ships.ships.toMutableList()
        var turnsTaken = 0
    }
    class Model(ships: ShipSpecifications, size: Point, playerCount: Int) {
        val players = (0 until playerCount).map { Player(size, it, ships) }
    }

    data class PlayerPosition(val targetPlayer: Int, val x: Int, val y: Int) {
        fun point(): Point = Point(x, y)
    }

    data class ShipPlacement(val name: String, val placement: Rect) {
        fun points() = placement.points()
    }

    data class ShipPlacements(val ships: List<ShipPlacement>) {
        fun overlap(): Boolean {
            return ships.any { a ->
                ships.minus(a).any { b ->
                    a.placement.intersects(b.placement)
                }
            }
        }
    }

    val factory = Games.api.gameCreator(Model::class)
    val place = factory.action("place", ShipPlacements::class)
    val shoot = factory.action("shoot", PlayerPosition::class)
    val game = factory.game("Battleship") {
        val size = config("size") { Point(10, 10) }
        val ships = config("ships") {
            ShipSpecifications(listOf("Carrier" to 5, "Battleship" to 4, "Destroyer" to 3, "Submarine" to 3, "Patrol Boat" to 2)
                .map { ShipSpecification(it.first, it.second) })
        }
        val revealExplodedShips = config("reveal_exploded") { true }.mutable()
        // mines, explodes in 8 positions around where the attacker shot, for the attacker's field. 5 mines per player
        // moveShips = config("move_ships_every_n_moves") { 0 } // 4 / 5
        // allowBentShips
        // multipleTargets = config("shots") { "summary" (2 hits/3 miss), static number 5, dynamic one for each ship, one for each ship of types XYZ }
        // Idea: With multipleTargets different ships are only allowed to shoot different ways
        //  - Submarine can only shoot in a straight line from the area it covers, or in a straight line to its sides (width 3)
        //  - Carrier can shoot 2 shots everywhere
        //  - Battleship can shoot 1 shot everywhere
        //  - Destroyer can shoot 1 shot in a 5x5 area around its center
        //  - Patrol Boat can give vision of an area, but cannot shoot
        // - Instead of shooting, a boat can move (but not into areas already shot?)
        setup {
            players(2..8)
            init { Model(config(ships), config(size), playerCount) }
        }
        gameFlow {
            step("place") {
                yieldAction(place) {
                    precondition { game.players[playerIndex].placementsRemaining.isNotEmpty() }
                    requires {
                        !action.parameter.overlap() &&
                            action.parameter.ships.all { it.placement.isWithin(game.players[playerIndex].field.border()) }
                    }
                    choose {
                        recursive(emptyList<ShipPlacement>()) {
                            until { game.players[playerIndex].placementsRemaining.size == chosen.size }
                            parameter { ShipPlacements(chosen) }
                            options({ game.players[playerIndex].placementsRemaining.map { it.name } - chosen.map { it.name }.toSet() }) { ship ->
                                options({ listOf(false, true) }) { vertical ->
                                    options({ game.players[playerIndex].field.points() - chosen.flatMap { it.points() }.toSet() }) { point ->
                                        val rectPoint = Rect(left = point.x, top = point.y, right = point.x, bottom = point.y)
                                        val chosenPoints = chosen.flatMap { it.points() }.toSet()
                                        val player = game.players[playerIndex]
                                        if (rectPoint.points().none { chosenPoints.contains(it) } && rectPoint.isWithin(player.field.border())) {
                                            val shipSpec = player.placementsRemaining.first { it.name == ship }
                                            val rect = if (vertical) rectPoint.include(point.x, point.y + shipSpec.size - 1)
                                                else rectPoint.include(point.x + shipSpec.size - 1, point.y)
                                            recursion(ShipPlacement(ship, rect)) { list, e -> list + e }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    perform {
                        val player = game.players[playerIndex]
                        action.parameter.ships.toMutableList().forEach { ship ->
                            val shipSpecification = player.placementsRemaining.first { it.name == ship.name }
                            check(ship.placement.area() == shipSpecification.size)
                            ship.placement.points().forEach { point ->
                                player.field.set(point.x, point.y, ship.name)
                            }
                            player.placementsRemaining.remove(shipSpecification)
                        }
                    }
                }
            }.loopUntil { game.players.all { it.placementsRemaining.isEmpty() } }

            loop {
                step("attack") {
                    yieldAction(shoot) {
                        precondition { game.players[playerIndex].turnsTaken <= game.players.minOf { it.turnsTaken } }
                        requires { game.players[action.parameter.targetPlayer].field.point(action.parameter.point()).value != "x" }
                        requires { action.parameter.targetPlayer != playerIndex }
                        choose {
                            options({ game.players.indices - playerIndex }) { playerTarget ->
                                options({ game.players[playerTarget].field.points() }) { point ->
                                    parameter(PlayerPosition(playerTarget, point.x, point.y))
                                }
                            }
                        }
                        perform {
                            game.players[playerIndex].turnsTaken++
                            val target = game.players[action.parameter.targetPlayer].field.point(action.parameter.point())
                            val previousValue = target.value
                            val result = if (previousValue != "") "HIT" else "MISS"
                            target.value = "x"
                            log { "$player attacked player ${player(action.targetPlayer)} at ${action.x},${action.y} which was a $result" }
                            if (previousValue != "" && config(revealExplodedShips)) {
                                game.players[action.parameter.targetPlayer].field.all().count { it.value == previousValue }
                                log { "${player(action.targetPlayer)}'s $previousValue has been destroyed" }
                            }
                        }
                    }
                }.loopUntil {
                    game.players.filter { eliminations.isAlive(it.playerIndex) }
                        .all { curr -> curr.turnsTaken == game.players.maxOf { it.playerIndex } }
                }

                step("eliminations") {
                    val gone = eliminations.remainingPlayers().map { game.players[it] }
                        .filter { player -> player.field.all().none { it.value != "x" && it.value != "" } }.map { it.playerIndex }
                    eliminations.eliminateMany(gone, WinResult.LOSS)
                }
            }
        }
        gameFlowRules {
            rules.players.lastPlayerStanding()
            beforeReturnRule("view") {
                view("your_board") { game.players.getOrNull(viewer ?: -1)?.field?.view { it } }
                view("players") { game.players.map { player -> player.field.view { tile ->
                    if (tile == "x") "x" else "."
                } } }
            }
        }
    }

}