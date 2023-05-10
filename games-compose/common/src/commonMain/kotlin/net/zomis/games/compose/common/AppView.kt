package net.zomis.games.compose.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.arkivanov.decompose.value.Value
import net.zomis.games.compose.common.network.Message

@Composable
fun AppView(playerValue: Value<Message.AuthMessage>, block: @Composable ColumnScope.() -> Unit) {
    val player = playerValue.subscribeAsState().value
/*
TODO: Invitations bar -- requires active invitations list which can be determined from ClientConnection
TODO: Invitations screen -- requires single invitation which can be updated from ClientConnection

*/

    Column {
        TopAppBar {
            Text(text = player.name)
        }
        block.invoke(this)
    }
}