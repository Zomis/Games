package net.tejpbit.ais

import net.zomis.minesweeper.api.Invite

// @AI(rating = 500)
class AI_Fighter : MinesweeperAI("#AI_Fighter") {
    private val random: java.util.Random = java.util.Random()
    private val ai1: MinesweeperAI = TejpbitAI_Hard()
    private val ai2: MinesweeperAI = AI_Challenger()
    fun agreeDraw(pp: MinesweeperPlayingPlayer?): Boolean {
        return false
    }

    fun play(pp: MinesweeperPlayingPlayer?): MinesweeperMove {
        return if (random.nextBoolean()) {
            ai1.play(pp)
        } else {
            ai2.play(pp)
        }
    }

    fun respondToInvite(invite: Invite?): Boolean {
        return true
    }
}