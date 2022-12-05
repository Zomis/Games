package net.zomis.games.components

class IdGenerator(private var firstId: Int = 1) {
    operator fun invoke(): Int = firstId++
}
