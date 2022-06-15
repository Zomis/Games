package net.zomis.games.server2

data class StartupEvent(val time: Long)
data class ShutdownEvent(val reason: String)
