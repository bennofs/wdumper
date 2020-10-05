#!/usr/bin/env bash
mysql --defaults-file=~/tools.cnf s54044__wdumper --table --batch --execute 'SELECT dump.id, dump.title, dump.run_id, dump.created_at, run.started_at FROM dump INNER JOIN run ON run.id = run_id WHERE run.finished_at IS NULL AND NOW() > DATE_ADD(run.started_at, INTERVAL 1 day)'
