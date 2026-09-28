package example

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Joyframe KMP — Lake Lab",
        state = rememberWindowState(width = 1180.dp, height = 800.dp)) { Playground() }
}
