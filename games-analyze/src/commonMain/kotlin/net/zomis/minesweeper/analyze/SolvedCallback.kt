package net.zomis.minesweeper.analyze

@Deprecated("")
interface SolvedCallback<T> {
    fun solved(solved: Solution<T>?)
}