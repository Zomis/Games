package net.zomis.games.compose.common

import com.arkivanov.decompose.router.stack.StackNavigator
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push

interface Navigator {
    fun navigateTo(configuration: Configuration)
    fun pop()
}

class PlatformNavigator(
    private val platformTools: PlatformTools,
    private val navigator: StackNavigator<Configuration>,
) : Navigator {
    override fun navigateTo(configuration: Configuration) {
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
class NoopNavigator : Navigator {
    override fun navigateTo(configuration: Configuration) {}
    override fun pop() {}
}
