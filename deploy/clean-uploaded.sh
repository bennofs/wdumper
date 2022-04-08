#!/usr/bin/env bash


min_id=$(ls dumpfiles/generated/|grep -Eo '[0-9]+' | sort -n | head -n1)
query="SELECT dump.id FROM dump INNER JOIN zenodo ON dump.id = zenodo.dump_id WHERE zenodo.completed_at IS NOT NULL AND dump.id > $min_id"
mysql --defaults-file=~/tools.cnf s54044__wdumper -N --batch --execute "$query" | sed -re 's@.*@dumpfiles/generated/wdump-\0.nt.gz@' | xargs rm -v
