#!/usr/bin/env bash
source "$HOME/secrets"

export JAVA_OPTS="-XX:+ExitOnOutOfMemoryError"
while true; do
	 "$HOME/app/bin/wdumper-backend" -d "$DUMPS_PATH" /public/dumps/public/wikidatawiki/entities/latest-all.json.gz > /dev/null || true
	tail -n2000 wdumper.err > /data/project/wdumps/crashes/$(date +"%s").stderr
	sleep 10
done
