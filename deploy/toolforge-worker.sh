#!/usr/bin/env bash
cd $(dirname "${BASH_SOURCE[0]}")/..
toolforge-jobs run backend --no-filelog --continuous --command ./deploy/job-worker.sh --mem "4G" --image tf-jdk17
