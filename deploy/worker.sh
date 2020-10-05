#!/usr/bin/env bash
source /data/project/wdumps/secrets 

while true; do
	java -XX:+ExitOnOutOfMemoryError -jar /data/project/wdumps/wdumper-all.jar -d $DUMPS_PATH /public/dumps/public/wikidatawiki/entities/latest-all.json.gz > /dev/null || true
	tail -n2000 wdumper.err > /data/project/wdumps/crashes/$(date +"%s").stderr
	sleep 10
done
