package com.claudecode.history

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.claudecode.history.config.ConfigLoader
import com.claudecode.history.data.DatabaseFactory
import com.claudecode.history.ui.MainScreen
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Claude Code History application" }

    // Load configuration
    val config = try {
        ConfigLoader.load()
    } catch (e: Exception) {
        logger.error(e) { "Failed to load configuration, using defaults" }
        com.claudecode.history.config.AppConfig() // Use defaults
    }

    logger.info { "Configuration loaded successfully" }

    // Initialize database with configuration
    DatabaseFactory.init(config.database, config.application.resolveDataDirectory())
    logger.info { "Database initialized successfully" }

    application {
        val windowState = rememberWindowState(size = DpSize(1400.dp, 900.dp))

        Window(
            onCloseRequest = ::exitApplication,
            title = "Claude Code History",
            state = windowState
        ) {
            MainScreen(config)
        }
    }
}
