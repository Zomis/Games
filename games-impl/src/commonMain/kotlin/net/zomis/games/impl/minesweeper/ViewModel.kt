package net.zomis.games.impl.minesweeper

import net.zomis.games.components.grids.GridView

data class ViewField(
    val x: Int, val y: Int,
    val clicked: Boolean,
    val knownValue: Int?,
    val knownMineValue: Int?,
    val playedBy: Int?,
) {
    constructor(field: Flags.Field) : this(field.x, field.y,
        clicked = field.clicked,
        knownValue = field.value.takeIf { field.clicked },
        knownMineValue = field.mineValue.takeIf { field.clicked },
        playedBy = field.takenBy?.playerIndex
    )
}
data class ViewWeapon(val weaponName: String, val remaining: Int)
data class ViewPlayer(val score: Int, val weapons: List<ViewWeapon>) {
    constructor(player: Flags.Player) : this(player.score, emptyList())
}
data class ViewModel(
    val viewer: Int,
    val minesRemaining: Int,
    val grid: GridView<ViewField?>,
    val players: List<ViewPlayer>,
) {
    constructor(model: Flags.Model, viewer: Int) : this(
        viewer = viewer,
        minesRemaining = model.remainingMines(),
        grid = model.grid.viewModel { ViewField(it) },
        players = model.players.map { ViewPlayer(it) }
    )
}
