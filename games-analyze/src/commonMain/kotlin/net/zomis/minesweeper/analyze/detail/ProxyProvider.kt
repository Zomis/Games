package net.zomis.minesweeper.analyze.detail

fun interface ProxyProvider<T> {
    fun getProxyFor(field: T): FieldProxy<T>?
}