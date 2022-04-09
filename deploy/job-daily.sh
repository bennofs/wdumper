#!/usr/bin/env bash
set -euxo pipefail
exec &> >(ts >> "$HOME/logs/daily")

"$HOME/deploy/clean-old.sh"
