#!/usr/bin/env bash
set -euo pipefail

AGENT="${1:-codex}"

if ! command -v npx >/dev/null 2>&1; then
  echo "Node.js/npm is required because agent skills are installed with npx." >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Python 3 is required by UI/UX Pro Max." >&2
  exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

echo "Installing UI/UX Pro Max for $AGENT ..."
npx --yes skills add https://github.com/nextlevelbuilder/ui-ux-pro-max-skill \
  --skill ui-ux-pro-max --agent "$AGENT" --global --yes

echo "Installing Modern Jetpack Compose for $AGENT ..."
npx --yes skills add https://github.com/anhvt52/jetpack-compose-skills \
  --skill modern-jetpack-compose --agent "$AGENT" --global --yes

echo
printf 'Installed skills for %s:\n  - ui-ux-pro-max\n  - modern-jetpack-compose\n' "$AGENT"
echo "Use docs/PLANET_UI_REDESIGN_AGENT.md as the execution brief."
