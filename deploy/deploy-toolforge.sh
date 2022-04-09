#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ssh_user="${TOOLFORGE_USER:-""}${TOOLFORGE_USER:+"@"}login-buster.toolforge.org"

./gradlew installDist
./deploy/deploy-toolforge-copy.sh

ssh "$ssh_user" "sudo -u tools.wdumps ~tools.wdumps/deploy/toolforge-web.sh"
ssh "$ssh_user" "sudo -u tools.wdumps ~tools.wdumps/deploy/toolforge-worker.sh"
ssh "$ssh_user" "sudo -u tools.wdumps ~tools.wdumps/deploy/toolforge-daily.sh"
