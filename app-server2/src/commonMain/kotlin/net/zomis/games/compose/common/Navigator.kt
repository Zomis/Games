package net.zomis.games.compose.common

import com.arkivanov.decompose.router.stack.StackNavigator
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push

interface Navigator<T> {
    fun navigateTo(configuration: T)
    fun pop()
}

class PlatformNavigator<T: Any>(
    private val platformTools: PlatformTools,
    private val navigator: StackNavigator<T>,
) : Navigator<T> {
    override fun navigateTo(configuration: T) {
        platformTools.runOnUiThread {
            navigator.push(configuration)
        }
    }

    override fun pop() {
        platformTools.runOnUiThread {
            navigator.pop()
        }
    }
}
class NoopNavigator<T> : Navigator<T> {
    override fun navigateTo(configuration: T) {}
    override fun pop() {}
}
