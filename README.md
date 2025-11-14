# Claude Code History

A desktop application for searching and browsing your Claude Code conversation history. Built with Kotlin and Compose Desktop, this tool provides a powerful interface to explore, search, and analyze your Claude Code interactions.

[![Build and Test](https://github.com/PoisonedYouth/ClaudeCodeHistory/actions/workflows/build.yml/badge.svg)](https://github.com/PoisonedYouth/ClaudeCodeHistory/actions/workflows/build.yml)

## Features

### Core Functionality
- **Full-Text Search**: Search through all your Claude Code conversations with advanced filtering
- **Auto-Indexing**: Automatically indexes existing conversations and watches for new ones in real-time
- **Rich Metadata**: Captures tool usage, file paths, programming languages, and model information
- **Conversation Timeline**: Browse conversations chronologically with detailed timestamps

### Search & Filtering
- Search by content, project path, date range, or programming language
- Filter by message role (user, assistant, system)
- Filter by specific file paths mentioned in conversations
- Advanced search with multiple filter combinations

### User Interface
- **Search Screen**: Intuitive search interface with real-time results
- **Conversation Detail View**: View complete conversation threads with syntax highlighting
- **Statistics Dashboard**: Analytics on your Claude Code usage patterns
- **Settings Panel**: Configure indexing and application preferences

### Data Management
- SQLite database for efficient storage and retrieval
- Automatic conversation parsing from `.jsonl` files
- File system watcher for real-time updates
- Conversation statistics and usage analytics

## Requirements

- **Java 17** or higher
- **Operating System**: macOS, Windows, or Linux
- **Claude Code**: Active installation with conversation history

## Installation

### Pre-built Binaries

Download the latest release for your platform:
- **macOS**: `ClaudeCodeHistory-0.0.1.dmg`
- **Windows**: `ClaudeCodeHistory-0.0.1.msi`
- **Linux**: `ClaudeCodeHistory-0.0.1.deb`

### Building from Source

```bash
# Clone the repository
git clone https://github.com/PoisonedYouth/ClaudeCodeHistory.git
cd ClaudeCodeHistory

# Build the application
./gradlew build

# Run the application
./gradlew run

# Package for distribution
./gradlew packageDmg  # macOS
./gradlew packageMsi  # Windows
./gradlew packageDeb  # Linux
```

## Usage

### First Launch

1. Launch the application
2. The app will automatically detect your Claude Code installation at `~/.claude/projects`
3. Initial indexing will begin automatically
4. Once complete, you can start searching your conversations

### Searching Conversations

- **Basic Search**: Enter keywords in the search box
- **Filter by Project**: Select a specific project from the dropdown
- **Date Range**: Use the date pickers to narrow results by time
- **Programming Language**: Filter by detected programming languages
- **File Paths**: Search for conversations mentioning specific files

### Viewing Conversation Details

- Click on any search result to view the full conversation thread
- Navigate between messages in the conversation
- View metadata including tool usage and file references
- Copy conversation content for external use

## Tech Stack

### Frontend
- **Compose Desktop**: Modern UI framework for desktop applications
- **Material 3**: Material Design components for Compose
- **KodeView**: Syntax highlighting for code blocks

### Backend
- **Kotlin**: Primary programming language
- **Exposed ORM**: Type-safe SQL framework
- **SQLite**: Embedded database
- **Kotlinx Serialization**: JSON parsing
- **Kotlinx Coroutines**: Asynchronous programming

### Additional Libraries
- **Ktor Client**: HTTP client (for future semantic search features)
- **Kotlinx DateTime**: Date and time handling
- **Logback**: Logging framework

## Project Structure

```
src/main/kotlin/com/claudecode/history/
├── data/                   # Database layer
│   ├── ConversationRepository.kt
│   ├── DatabaseFactory.kt
│   └── Tables.kt
├── domain/                 # Domain models
│   └── Models.kt
├── service/                # Business logic
│   ├── ClaudeConversationParser.kt
│   ├── IndexingService.kt
│   └── SearchService.kt
├── ui/                     # User interface
│   ├── MainScreen.kt
│   ├── SearchScreen.kt
│   ├── ConversationDetailView.kt
│   ├── StatisticsScreen.kt
│   ├── SettingsScreen.kt
│   ├── SearchResultCard.kt
│   ├── FiltersPanel.kt
│   └── TopNavigationBar.kt
└── Main.kt                 # Application entry point
```

## Development

### Running Tests

```bash
./gradlew test
```

### Code Style

This project follows standard Kotlin coding conventions. Ensure your code:
- Uses conventional commits
- Is covered by appropriate tests
- Passes all CI checks before merging

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes using conventional commits
4. Write tests for new functionality
5. Push to the branch (`git push origin feature/amazing-feature`)
6. Open a Pull Request

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- Built for the Claude Code community
- Powered by [Anthropic's Claude](https://claude.ai)
- Uses [JetBrains Compose Desktop](https://www.jetbrains.com/lp/compose-desktop/)

## Support

For issues, questions, or suggestions:
- Open an issue on [GitHub](https://github.com/PoisonedYouth/ClaudeCodeHistory/issues)
- Check existing issues for solutions

---

**Note**: This is a community project and is not officially affiliated with Anthropic.
