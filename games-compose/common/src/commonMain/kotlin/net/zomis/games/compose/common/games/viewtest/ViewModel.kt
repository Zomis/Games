package net.zomis.games.compose.common.games.viewtest

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.Value
import net.zomis.games.components.grids.GridView
import net.zomis.games.impl.minesweeper.Flags

@Immutable
data class ViewField(
    val x: Int, val y: Int,
    val clicked: Boolean,
    val knownValue: Int?,
    val knownMineValue: Int?,
    val playedBy: Int?,
)
@Immutable
data class ViewWeapon(val weaponName: String, val remaining: Int)
@Immutable
data class ViewPlayer(val score: Int, val weapons: List<ViewWeapon>)

@Immutable
data class ViewModel2(
    val viewer: Int,
    val minesRemaining: Int,
    val grid: GridView<ViewField?>,
    val players: List<ViewPlayer>,
)
