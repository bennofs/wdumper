#!/usr/bin/env bash

cd /data/project/wdumps/dumpfiles/generated || exit
find -mtime +14 -exec rm -vf '{}' '+' || echo "nothing to delete"
