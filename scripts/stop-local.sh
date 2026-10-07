#!/usr/bin/env bash
cd "$(dirname "$0")/.."
if [ -f logs/pids ]; then
  while read -r pid; do kill "$pid" 2>/dev/null && echo "stopped $pid"; done < logs/pids
  : > logs/pids
fi
