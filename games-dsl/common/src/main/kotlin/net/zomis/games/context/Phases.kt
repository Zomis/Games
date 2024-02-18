package net.zomis.games.context

class ActivePhases<T>(sequence: Sequence<T>) {
    var phaseIndex = 0
        private set
    private val iterator: Iterator<T> = sequence.iterator()

    var current: T = iterator.next()

    fun next() {
        phaseIndex++
        current = iterator.next()
    }
}
