package net.zomis.games.compose.common

actual fun getPlatformName(): String {
    return "Desktop"
}

internal actual class PlatformSocket actual constructor(url: String) {
    actual fun openSocket(listener: PlatformSocketListener) {
    }

    actual fun closeSocket(code: Int, reason: String) {
    }

    actual fun sendMessage(msg: String) {
    }
}

class DesktopPlatformSocketListener : PlatformSocketListener {
    override fun onOpen() {
        TODO("Not yet implemented")
    }

    override fun onFailure(t: Throwable) {
        TODO("Not yet implemented")
    }

    override fun onMessage(msg: String) {
        TODO("Not yet implemented")
    }

    override fun onClosing(code: Int, reason: String) {
        TODO("Not yet implemented")
    }

    override fun onClosed(code: Int, reason: String) {
        TODO("Not yet implemented")
    }

}