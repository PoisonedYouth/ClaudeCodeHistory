package com.claudecode.history.service

import com.claudecode.history.data.ConversationRepository
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.File
import java.nio.file.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

private val logger = KotlinLogging.logger {}

class IndexingService(
    private val repository: ConversationRepository,
    private val parser: ClaudeConversationParser
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isWatching = AtomicBoolean(false)
    private var watchJob: Job? = null

    /**
     * Index all existing conversation files
     */
    suspend fun indexAllConversations(): IndexingResult = withContext(Dispatchers.IO) {
        logger.info { "Starting full indexing of all conversations" }

        val conversationFiles = parser.findConversationFiles()
        var totalIndexed = 0
        var totalFailed = 0

        conversationFiles.forEach { (file, projectPath) ->
            try {
                val conversations = parser.parseConversationFile(file, projectPath)
                conversations.forEach { conversation ->
                    try {
                        repository.insert(conversation)
                        totalIndexed++
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to insert conversation: ${conversation.sessionId}" }
                        totalFailed++
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse file: $file" }
                totalFailed++
            }
        }

        logger.info { "Indexing complete. Indexed: $totalIndexed, Failed: $totalFailed" }
        IndexingResult(totalIndexed, totalFailed)
    }

    /**
     * Start watching for new conversation files
     */
    fun startWatching() {
        if (isWatching.compareAndSet(false, true)) {
            watchJob = scope.launch {
                watchConversationDirectory()
            }
            logger.info { "Started watching for new conversations" }
        } else {
            logger.warn { "Already watching for conversations" }
        }
    }

    /**
     * Stop watching for new conversation files
     */
    fun stopWatching() {
        if (isWatching.compareAndSet(true, false)) {
            watchJob?.cancel()
            logger.info { "Stopped watching for new conversations" }
        }
    }

    private suspend fun watchConversationDirectory() {
        val claudeDir = File(System.getProperty("user.home"), ".claude/projects").toPath()

        if (!claudeDir.exists()) {
            logger.error { "Claude directory not found: $claudeDir" }
            isWatching.set(false)
            return
        }

        try {
            // Use 'use' to ensure WatchService is always closed
            FileSystems.getDefault().newWatchService().use { watcher ->
                // Register all existing project directories
                Files.walk(claudeDir, 1).use { paths ->
                    paths.filter { Files.isDirectory(it) && it != claudeDir }
                        .forEach { projectDir ->
                            try {
                                projectDir.register(
                                    watcher,
                                    StandardWatchEventKinds.ENTRY_CREATE,
                                    StandardWatchEventKinds.ENTRY_MODIFY
                                )
                                logger.debug { "Watching directory: $projectDir" }
                            } catch (e: Exception) {
                                logger.warn(e) { "Failed to watch directory: $projectDir" }
                            }
                        }
                }

                // Also watch the projects directory itself for new project folders
                claudeDir.register(
                    watcher,
                    StandardWatchEventKinds.ENTRY_CREATE
                )

                logger.info { "File watcher initialized, monitoring ${claudeDir}" }

                while (isWatching.get() && currentCoroutineContext().isActive) {
                    // Check for cancellation
                    currentCoroutineContext().ensureActive()

                    val key = try {
                        watcher.poll(1, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        logger.info { "File watcher interrupted" }
                        break
                    } catch (e: CancellationException) {
                        logger.info { "File watcher cancelled" }
                        throw e
                    }

                    if (key == null) continue

                    for (event in key.pollEvents()) {
                        val kind = event.kind()

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            logger.warn { "File system event overflow" }
                            continue
                        }

                        @Suppress("UNCHECKED_CAST")
                        val ev = event as WatchEvent<Path>
                        val filename = ev.context()
                        val dir = key.watchable() as Path
                        val filePath = dir.resolve(filename)

                        when {
                            // New project directory created
                            Files.isDirectory(filePath) && dir == claudeDir -> {
                                try {
                                    filePath.register(
                                        watcher,
                                        StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_MODIFY
                                    )
                                    logger.info { "Now watching new project directory: $filePath" }
                                } catch (e: Exception) {
                                    logger.warn(e) { "Failed to watch new directory: $filePath" }
                                }
                            }

                            // Conversation file created or modified
                            filePath.isRegularFile() && filename.toString().endsWith(".jsonl") -> {
                                logger.info { "Detected conversation file change: $filePath" }
                                indexConversationFile(filePath)
                            }
                        }
                    }

                    if (!key.reset()) {
                        logger.warn { "Watch key no longer valid" }
                        break
                    }
                }

                logger.info { "File watcher loop exited normally" }
            } // WatchService automatically closed here
        } catch (e: CancellationException) {
            logger.info { "File watcher cancelled" }
            throw e // Re-throw to properly cancel the coroutine
        } catch (e: Exception) {
            logger.error(e) { "Error in file watcher" }
        } finally {
            isWatching.set(false)
            logger.info { "File watcher cleanup complete" }
        }
    }

    private suspend fun indexConversationFile(file: Path) = withContext(Dispatchers.IO) {
        try {
            // Decode project path from parent directory name
            val projectDirName = file.parent.fileName.toString()
            val projectPath = parser.findConversationFiles()
                .find { it.first == file }
                ?.second
                ?: projectDirName

            val conversations = parser.parseConversationFile(file, projectPath)
            conversations.forEach { conversation ->
                try {
                    repository.insert(conversation)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to insert conversation: ${conversation.sessionId}" }
                }
            }
            logger.info { "Indexed ${conversations.size} conversations from $file" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to index file: $file" }
        }
    }

    fun shutdown() {
        stopWatching()
        scope.cancel()
    }
}

data class IndexingResult(
    val indexed: Int,
    val failed: Int
)
