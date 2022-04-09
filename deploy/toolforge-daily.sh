#!/usr/bin/env bash
cd $(dirname "${BASH_SOURCE[0]}")/..
toolforge-jobs delete daily || true
toolforge-jobs run daily --no-filelog --schedule '47 18 * * *' --image tf-bullseye-std --command ./deploy/job-daily.sh --cpu 100m
