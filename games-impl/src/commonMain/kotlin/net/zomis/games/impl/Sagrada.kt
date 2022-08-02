package net.zomis.games.impl

import net.zomis.games.api.Games
import net.zomis.games.api.GamesApi
import net.zomis.games.api.components
import net.zomis.games.cards.CardZone
import net.zomis.games.common.shifted
import net.zomis.games.components.Grid
import net.zomis.games.dsl.flow.GameFlowScope

object Sagrada {

    object Data {
        val privateGoals = listOf<Pair<String, Objective>>()
//            SagradaColor.values().map { color -> "Shades of ${color.name.toLowerCase()}" to Objective { 42 } }
        // sum of dice on shaded areas ...
        // _____ #_#_# #_#_# _____ Fence
        // _###_ _____ _____ _###_ Tunnel
        // _#_#_ __#__ __#__ _#_#_ Brace
        // __#__ _#_#_ _#_#_ __#__ Port
        // ##_## #___# _____ _____ Brackets
        // _____ _###_ _###_ _____ Wall

        val tools = listOf(
            Tool("Grozing Pliers", Color.PURPLE, "After drafting, increase or decrease the value of the drafted die by 1 (1 may not change to 6, or 6 to 1)"),
            Tool("Eglomise Brush", Color.BLUE, "Move any one die in your window ignoring color restrictions. You must obey all other placement restrictions, including color adjacency restrictions."),
            Tool("Copper Foil Burnisher", Color.RED, "Move any one die in your window ignoring value restrictions. You must obey all other placement restrictions, including value adjacency restrictions."),
            Tool("Lathekin", Color.YELLOW, "Move exactly two dice, obeying all placement restrictions."),
            Tool("Lens Cutter", Color.GREEN, "After drafting, swap the drafted die with a die from the Round Track."),
            Tool("Flux Brush", Color.PURPLE, "After drafting, re-roll the drafted die. If it cannot be placed, return it to the Draft Pool."),
            Tool("Glazing Hammer", Color.BLUE, "Re-roll all dice in the Draft Pool. This may only be used on your second turn before drafting."),
            Tool("Running Pliers", Color.RED, "After your first turn, immediately draft a die. Skip your next turn this round."),
            Tool("Cork-backed Straightedge", Color.YELLOW, "After drafting, place the die in a spot that is not adjacent to another die. You must obey all other placement restrictions."),
            Tool("Grinding Stone", Color.GREEN, "After drafting, flip the die to its opposite side. 6-1, 5-2, 4-3."),
            Tool("Flux Remover", Color.PURPLE, "After drafting, return the die to the Dice Bag and pull 1 die from the bag. Choose a value and place the new die, obeying all placement restrictions, or return it to the Draft Pool."),
            Tool("Tap Wheel", Color.BLUE, "Move up to two dice of the same color that match the color of a die on the Round Track. You must obey all placement restrictions."),
            Tool("Strip Cutter", Color.RED, "After drafting, swap the drafted die with a die from your Private Dice Pool (requires private dice pool variant from 5-6 player expansion)"),
            Tool("Lead Came Nippers", Color.YELLOW, "Re-Roll up to 2 dice in your Private Dice Pool (requires private dice pool variant from 5-6 player expansion)")
        )

        val publicGoals = listOf<Pair<String, Objective>>(

//            "Color Diagonals" to "Count of diagonally adjacent same-color dice (1 point per dice)"
//            "Medium shades" to Objective { min(it.count(3), it.count(4)) }
/*
        Medium shades: sets of 3 & 4 values anywhere (2 points per set)
        Light shades: sets of 1 & 2 values anywhere (2 points per set)
        Deep shades: sets of 5 & 6 values anywhere (2 points per set)
        Row shade variety: Rows with no repeated values (5 points per row)
        Row color variety: Rows with no repeated colors (6 points per row)
        Column shade variety: Columns with no repeated values (4 points per column)
        Column color variety: Columns with no repeated colors (5 points per column)
        Color variety: Sets of one of each color anywhere (4 points per set)
        Shade variety: Sets of one of each value anywhere (5 points per set)
 */
        )

        val facades = listOf(
            window("Bellesguard", 3, "B6__Y _3B__ _562_ _4_1G"),
            window("Fractal Drops", 3, "_4_Y6 R_2__ __RP1 BY___"),
            window("Sun Catcher", 3, "_B2_Y _4_R_ __5Y_ G3__P"),
            window("Luz celestial", 3, "__R5_ P4_G3 6__B_ _Y2__"),
            window("Aurora Sagradis", 4, "R_B_Y 4P3G2 _1_5_ __6__"),
            window("Generosidad", 3, "__3_Y _2G_4 B__R_ 6_1_2"),
            window("Ligero", 3, "_5G__ _B1_6 2_BP_ __3_1"),
            window("Sol", 3, "1__Y_ __Y5_ _1__6 Y3_2G"),
            window("Esperanca", 3, "1GP__ 3__P6 R_5__ _4__G"),
            window("Romanesque", 3, "__R__ _R4B_ 1_P_2 _Y6R_"),
            window("Apricitas", 3, "__Y3_ G4__B 2_R_6 _P_Y_"),
            window("Chromatic Splendor", 4, "__G__ 2Y5B1 _R3P_ 1_6_4"),
            window("Via Lux", 4, "Y_6__ _15_2 3YRP_ __43R"),
            window("Kaleidoscopic dream", 4, "YB__1 G_5_4 3_R_G 2__BY"),
            window("Batllo", 5, "__6__ _5B4_ 3GYP2 14R53"),
            window("Ripples of light", 5, "___R5 __P4B _B3Y6 Y2G1R"),
            window("Shadow thief", 5, "6P__5 5_P__ R6_P_ YR543"),
            window("Fulgor del Cielo", 5, "_BR__ _45_B B2_R5 6R31_"),
            window("Aurorae Magnificus", 5, "5GBP2 P___Y Y_6_P 1__G4"),
            window("Comitas", 5, "Y_2_6 _4_5Y ___Y5 12Y3_"),
            window("Industria", 5, "1R3_6 54R2_ __5R1 ___3R"),
            window("Firmitas", 5, "P6__3 5P3__ _2P1_ _15P4"),
            window("Firelight", 5, "3415_ _62_Y ___YR 5_YR6"),
            window("Virtus", 5, "4_25G __6G2 _3G4_ 5G1__"),
            window("Gravitas", 5, "1_3B_ _2B__ 6B_4_ B52_1"),
            window("Lux Astram", 5, "_1GP4 6P25G 1G53P _____"),
            window("Sun's Glory", 6, "1PY_4 PY__6 Y__53 _5421"),
            window("Symphony of Light", 6, "2_5_1 Y6P2R _B4G_ _3_5_"),
            window("Water of Life", 6, "6B__1 _5B__ 4R2B_ G6Y3P"),
            window("Lux Mundi", 6, "__1__ 1G3B2 B546G _B5G_"),
            window("Armonia", 6, "_53_Y Y2GB4 B__R6 6_1_2"),
            window("Shadom", 6, "_5G__ 5B1_6 2_BPG P_361"),
            window("Naturaleza", 6, "1__Y_ _GY53 _YG_6 Y312G"),
            window("Alegria", 6, "1GPR_ 35_P6 R_5__ P42_G"),
            window("Baroque", 6, "__R__ YR4BP 1_P_2 3Y6R5"),
            window("Tenebra", 6, "_5Y31 G4__B 2GR_6 _PBY_")
        )

        fun window(name: String, toolTokens: Int, map: String): Window {
            val rows = map.split(" ")
            val colors = Color.values().associateBy { it.name[0] }
            val map2d = Games.components.grid(5, 4) { x, y ->
                val ch = rows[y][x]
                when (ch) {
                    in '1'..'6' -> WindowSlot(restrictedValue = ch.digitToInt())
                    in colors.keys -> WindowSlot(restrictedColor = colors.getValue(ch))
                    else -> WindowSlot()
                }
            }
            return Window(name, toolTokens, map2d)
        }

    }

    fun interface Objective {
        fun score(player: Player): Int
    }
    enum class Color { BLUE, RED, GREEN, YELLOW, PURPLE }
    class WindowSlot(val restrictedColor: Color? = null, val restrictedValue: Int? = null, var die: Die? = null)
    class Window(val name: String, val toolTokens: Int, val slots: Grid<WindowSlot>)
    class Die(val color: Color, var value: Int)
    class Tool(val name: String, val color: Color, val description: String)
    data class Config(val rounds: Int)
    class Model(val config: Config, playerCount: Int) {
        var turnOrder: List<Player> = emptyList()
        val players = (0 until playerCount).map { Player(it) }
        val diceBag = CardZone(
            (0 until (playerCount * 20 + 10) / Color.values().size).map { Die(Color.values()[it % Color.values().size], 0) }.toMutableList()
        )
        val tools = CardZone<Tool>()
        val publicObjectives = CardZone<Objective>()

        var currentPlayer: Player = players[0]
        var roundsFinished: Int = 0
    }
    class Player(val playerIndex: Int) {
        val windowOptions = CardZone<Window>()
        var window: Window? = null
        var favorTokens: Int = 0
    }

    val factory = GamesApi.gameCreator(Model::class)

    val game = factory.game("Sagrada") {
        setup(Config::class) {
            players(2..4)
            defaultConfig { Config(10) }
            init { Model(config, playerCount) }
            onStart {
                val playerCount = game.players.size

                // Should be 2 window pattern cards where you may choose front or back
                // The randomization can be replaced to select only the cards, but prefer to save both front and back in replayable still
                val windows = CardZone(Data.facades.toMutableList())
                val chosenWindows = windows.random(replayable, playerCount * 4, "windows") { it.name }
                windows.deal(chosenWindows.map { it.card }.toList(), game.players.map { it.windowOptions })

                // 3 tools
                // 3 public objectives
                // 1 private objective
            }
        }
        gameFlowRules {
        }
        gameFlow {
            choosePlayerWindow(this)

            for (i in 0 until 10) {
                game.roundsFinished = i
                val playerOrder = game.players.shifted(game.roundsFinished % game.players.size)
                val reverseOrder = playerOrder.reversed()
                val turnOrder = playerOrder + reverseOrder

                game.turnOrder = turnOrder
                roundStart(this)

                for (player in turnOrder) {
                    game.currentPlayer = player
                    playerTurn(this, player)
                }
                roundEnd(this)
            }

            scoringCalculation(this)
        }
        testCase(players = 2) {

        }
    }

    private val chooseWindow = factory.action("pick-window", Window::class)
    private suspend fun choosePlayerWindow(gameFlow: GameFlowScope<Model>) {
        gameFlow.step("choose player window") {
            yieldAction(chooseWindow) {
                precondition { game.players[playerIndex].window == null }
                options { game.players[playerIndex].windowOptions.cards }
                perform { game.players[playerIndex].window = action.parameter }
            }
        }.loopUntil { gameFlow.game.players.all { it.window != null } }
    }

    private suspend fun roundStart(gameFlow: GameFlowScope<Model>) {
        gameFlow.step("round start") {

        }
    }

    private suspend fun playerTurn(gameFlow: GameFlowScope<Model>, player: Player) {
        // choose up to two: Draft die, use tool, or just pass
    }

    private suspend fun roundEnd(gameFlow: GameFlowScope<Model>) {
        TODO("Not yet implemented")
    }

    private suspend fun scoringCalculation(gameFlow: GameFlowScope<Model>) {
        TODO("Not yet implemented")
    }

}
