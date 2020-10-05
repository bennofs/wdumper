#!/usr/bin/env bash
set -euo pipefail

if [ ! $# -eq 1 ]; then
  echo "usage: clear-failed-run.sh RUNID"
  exit 22
fi

runid=$1
mysql --defaults-file=~/tools.cnf s54044__wdumper --batch --execute "UPDATE dump SET run_id = NULL WHERE run_id = $runid LIMIT 4"

