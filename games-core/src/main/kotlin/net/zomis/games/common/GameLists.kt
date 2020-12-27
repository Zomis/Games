package net.zomis.games.common

fun <T: Any> T.toSingleList(): List<T> = listOf(this)
