package example

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() { ComposeViewport(requireNotNull(document.getElementById("compose-root"))) { Playground() } }
