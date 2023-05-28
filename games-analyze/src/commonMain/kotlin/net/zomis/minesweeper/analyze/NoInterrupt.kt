package net.zomis.minesweeper.analyze

class NoInterrupt : InterruptCheck {
    override val isInterrupted: Boolean
        get() = false
}