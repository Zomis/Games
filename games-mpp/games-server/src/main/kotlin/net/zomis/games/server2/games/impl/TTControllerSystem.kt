package net.zomis.games.server2.games.impl

import net.zomis.games.WinResult
import net.zomis.games.impl.ttt.ultimate.TTPlayer

fun TTPlayer.playerIndex(): Int {
    if (this == TTPlayer.X) return 0
    if (this == TTPlayer.O) return 1
    throw IllegalArgumentException("Current player must be X or O but was $this")
}

fun TTPlayer.toWinResult(playerIndex: Int): WinResult {
    return when {
        this == TTPlayer.NONE -> WinResult.DRAW
        this == TTPlayer.BLOCKED -> WinResult.DRAW
        this == TTPlayer.XO -> WinResult.DRAW
        this.playerIndex() == playerIndex -> WinResult.WIN
        else -> WinResult.LOSS
    }
}
