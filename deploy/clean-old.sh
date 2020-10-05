#!/usr/bin/env bash

cd /data/project/wdumps/dumpfiles/generated
find -mtime +14 | xargs rm -vf || echo "nothing to delete"
