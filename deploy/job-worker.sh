#!/usr/bin/env bash
source "$HOME/secrets"

export JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -Xmx4g"
while true; do
	{
		"$HOME/app/bin/wdumper-backend" -d "$DUMPS_PATH" /public/dumps/public/wikidatawiki/entities/latest-all.json.gz >/dev/null
	} 2>&1 | ts >> "$HOME/logs/backend" || true
	tail -n2000 "$HOME/logs/backend" > /data/project/wdumps/crashes/$(date +"%s").stderr
	sleep 10
done
