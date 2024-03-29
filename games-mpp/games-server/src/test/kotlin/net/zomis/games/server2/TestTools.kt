package net.zomis.games.server2

import net.zomis.games.server2.doctools.DocWriter
import java.net.ServerSocket

fun testDocWriter(file: String = "UNDEFINED"): DocWriter {
    return DocWriter(file)
}

fun testServerConfig(): ServerConfig {
    val config = ServerConfig()
    config.port = randomPort()
    config.idGenerator = { "1" }
    config.wait = false
    return config
}

fun randomPort(): Int {
    val tempServer = ServerSocket(0)
    val port = tempServer.localPort
    tempServer.close()
    return port
}
