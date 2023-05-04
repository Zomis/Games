package net.zomis.games.compose.common

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

interface LoginComponent

class DefaultLoginComponent(
    componentContext: ComponentContext
) : ComponentContext by componentContext

@Composable
fun LoginContent(component: DefaultLoginComponent) {
    Text("Hello World!")
}
