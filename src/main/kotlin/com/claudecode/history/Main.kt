package com.claudecode.history

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.claudecode.history.data.DatabaseFactory
import com.claudecode.history.ui.MainScreen
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Claude Code History application" }

    // Initialize database
    DatabaseFactory.init()
    logger.info { "Database initialized successfully" }

    application {
        val windowState = rememberWindowState(size = DpSize(1400.dp, 900.dp))

        Window(
            onCloseRequest = ::exitApplication,
            title = "Claude Code History",
            state = windowState
        ) {
            MainScreen()
        }
    }
}
