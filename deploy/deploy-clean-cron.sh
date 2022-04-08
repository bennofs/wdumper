#!/usr/bin/env bash
cd $(dirname "${BASH_SOURCE[0]}")/..
toolforge-jobs run cleanup --schedule '47 18 * * *' --image tf-bullseye-std --command ./scripts/clean-old.sh
