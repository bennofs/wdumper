#!/usr/bin/env bash
source "$HOME/secrets"

export JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -Xmx1g"
"$HOME/app/bin/wdumper-api" 2>&1 | ts >> logs/web
