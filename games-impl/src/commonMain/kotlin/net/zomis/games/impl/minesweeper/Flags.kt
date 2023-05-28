package net.zomis.games.impl.minesweeper

import net.zomis.games.api.GamesApi
import net.zomis.games.common.next
import net.zomis.games.components.Point
import net.zomis.games.impl.minesweeper.specials.NormalMultiplayer

object Flags {
    enum class AI(val visibleName: String, aiName: String? = null) {
//        Loser("Loser"),
        CompleteIdiot("Complete Idiot"),
//        Medium("Medium"),
//        Challenger("Challenger"),
//        Hard("Hard"),
//        HardPlus("Hard Plus"),
//        Extreme("Extreme"),
//        Nightmare("Nightmare"),
        Impossible("Impossible (for testing only)", aiName = "#AI_Impossible"),
        ;

        val publicName = aiName ?: "#AI_$visibleName"
    }

    class Model(playerCount: Int, size: Point) {
        val players = (0 until playerCount).map { Player(it) }
        val grid = GamesApi.components.grid(size.x, size.y) { x, y ->
            Field(x, y)
        }
        var currentPlayer: Int = 0

        fun recount() = grid.all().forEach { it.value.recount() }
        fun fieldAt(position: Point): Field = grid.point(position).value
        fun nextPlayer() {
            currentPlayer = currentPlayer.next(players.size)
        }

        fun remainingMines(): Int = grid.all().filter { !it.value.clicked }.sumOf { it.value.mineValue }

    }
    class Field(val x: Int, val y: Int) {
        var clicked: Boolean = false
        var mineValue: Int = 0
        var value: Int = 0
        var takenBy: Player? = null
        var blocked: Boolean = false
        private val _neighbors: MutableList<Field> = mutableListOf()
        private val _inverseNeighbors: MutableList<Field> = mutableListOf()
        val neighbors: List<Field> = _neighbors
        val inverseNeighbors: List<Field> = _inverseNeighbors

        fun recount() {
            this.value = _neighbors.sumOf { it.mineValue }
        }
        fun reveal(playedBy: Player?) {
            if (!this.clicked && playedBy != null) {
                playedBy.score += mineValue
            }
            this.clicked = true
            this.takenBy = playedBy
        }

        internal fun addMutualNeighbor(other: Field) {
            this._neighbors.add(other)
            other._inverseNeighbors.add(this)
        }

        fun toStateString() = Point(x, y).toStateString()
        fun isMine() = mineValue != 0
    }
    class Player(val playerIndex: Int) {
        val weapons = mutableListOf<Weapon>()
        var score: Int = 0
    }

    val factory = GamesApi.gameCreator(Model::class)
    val viewModel = factory.viewModel(::ViewModel)
    val useWeapon = factory.action("use", WeaponUse::class).serializer { it.weapon.name + "@" + it.position.toStateString() }
    val game = factory.game("MFE") {
        val width = config("width") { 16 }
        val height = config("height") { 16 }
        val neighbors = config("neighbors") { NeighborStyle.NORMAL }
        val expanderRule = config("expanderRule") { true }
        val mineCount = config("mines") { 51 }
        setup {
            init {
                Model(playerCount, Point(config(width), config(height)))
            }
            playersFixed(2)
        }
        gameFlow {
            Neighbors.configure(game, config(neighbors))
            game.players.forEach {
                it.weapons.add(Weapons.Default())
                it.weapons.add(Weapons.bomb(usages = 1))
            }
            val mines = replayable.randomFromList("mines", game.grid.all().map { it.value }.toList(), config(mineCount)) { it.toStateString() }
            mines.forEach {
                it.mineValue = 1
            }
            game.recount()

            loop {
                step("gameplay") {
                    yieldAction(useWeapon) {
                        requires { game.players[playerIndex].weapons.contains(action.parameter.weapon) }
                        requires { action.parameter.weapon.usableForPlayer(game, playerIndex) }
                        requires {
                            action.parameter.weapon.usableAt(game, playerIndex, game.fieldAt(action.parameter.position))
                        }
                        perform { action.parameter.weapon.use(game, playerIndex, game.fieldAt(action.parameter.position)) }
                        choose {
                            optionsWithIds({ game.players[playerIndex].weapons.map { it.name to it } }) { weapon ->
                                options({ game.grid.points() }) { pos ->
                                    parameter(WeaponUse(weapon, pos))
                                }
                            }
                        }
                    }
                }

                NormalMultiplayer.Goal.eliminateLosers(game, eliminations)
                NormalMultiplayer.Goal.lastPlayersStanding(eliminations, count = 1)
                NormalMultiplayer.Goal.endShowMines(eliminations, game)
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                viewModel(viewModel)
            }
        }
        val bombScorer = this.scorers.actionConditional(useWeapon) { action.parameter.weapon is SizedBombWeapon }
        val cheatScorer = this.scorers.actionConditional(useWeapon) {
            action.parameter.weapon is Weapons.Default && action.game.grid.point(action.parameter.position).value.mineValue > 0
        }
        scorers.ai(AI.CompleteIdiot.publicName, bombScorer.weight(-1))
        scorers.ai(AI.Impossible.publicName, cheatScorer.weight(1))
    }

}