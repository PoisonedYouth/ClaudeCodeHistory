# Claude Code Hooks for Conversation History

These hook scripts enable automatic indexing of Claude Code conversations.

## Installation

1. Make the script executable:
   ```bash
   chmod +x hooks/capture-conversation.sh
   ```

2. Add the hook configuration to your Claude Code settings file (`~/.claude/settings.json`):

   ```json
   {
     "hooks": {
       "Stop": [
         {
           "matcher": ".*",
           "hooks": [
             {
               "type": "command",
               "command": "/path/to/ClaudeCodeHistory/hooks/capture-conversation.sh"
             }
           ]
         }
       ],
       "PreCompact": [
         {
           "matcher": ".*",
           "hooks": [
             {
               "type": "command",
               "command": "/path/to/ClaudeCodeHistory/hooks/capture-conversation.sh"
             }
           ]
         }
       ],
       "SessionEnd": [
         {
           "matcher": ".*",
           "hooks": [
             {
               "type": "command",
               "command": "/path/to/ClaudeCodeHistory/hooks/capture-conversation.sh"
             }
           ]
         }
       ]
     }
   }
   ```

3. Replace `/path/to/ClaudeCodeHistory` with the actual path to this project.

## How It Works

- The hooks create a trigger file at `~/.claude-history/trigger-index`
- The desktop application monitors this file for changes
- When new entries are detected, the app automatically indexes new conversations
- This ensures your search index stays up-to-date in real-time

## Hook Events

- **Stop**: Triggered after each Claude response completes
- **PreCompact**: Triggered before conversation history is compressed (critical for capturing full history)
- **SessionEnd**: Triggered when a Claude Code session ends

## Manual Triggering

You can manually trigger indexing by running:
```bash
./hooks/capture-conversation.sh
```

Or by running the "Index All Conversations" action in the desktop app.
