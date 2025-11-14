#!/bin/bash

# Claude Code Hook Script for Conversation Capture
# This script is called by Claude Code hooks to trigger indexing

# Configuration
APP_DIR="$HOME/.claude-history"
TRIGGER_FILE="$APP_DIR/trigger-index"

# Ensure directory exists
mkdir -p "$APP_DIR"

# Create a trigger file that the desktop app can monitor
# The file contains the timestamp and project directory
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ)|${CLAUDE_PROJECT_DIR:-unknown}" >> "$TRIGGER_FILE"

# Exit with success
exit 0
