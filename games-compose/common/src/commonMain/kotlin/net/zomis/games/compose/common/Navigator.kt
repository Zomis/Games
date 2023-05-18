package net.zomis.games.compose.common

import com.arkivanov.decompose.router.stack.StackNavigator
import com.arkivanov.decompose.router.stack.push

interface Navigator {
    fun navigateTo(configuration: Configuration)
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
}
class NoopNavigator : Navigator {
    override fun navigateTo(configuration: Configuration) {}
}
