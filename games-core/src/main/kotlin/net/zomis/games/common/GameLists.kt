package net.zomis.games.common

fun <T: Any> T.toSingleList(): List<T> = listOf(this)
fun <T> List<T>.shifted(steps: Int): List<T> {
    require(steps >= 0)
    require(steps < this.size)
    return this.subList(steps, this.size) + this.subList(0, steps)
}
