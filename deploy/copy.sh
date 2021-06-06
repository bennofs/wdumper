#!/usr/bin/env bash
set -euo pipefail

cd $(dirname "${BASH_SOURCE[0]}")/..

ssh_user="${TOOLFORGE_USER:-""}${TOOLFORGE_USER:+"@"}login.tools.wmflabs.org"

rsync --delete --recursive --checksum --info=progress --verbose build/install/wdumper/ "$ssh_user:~tools.wdumps/app"
ssh "$ssh_user" "sudo -u tools.wdumps bash -c 'take ~tools.wdumps/app && chmod --recursive g+w ~tools.wdumps/app'"
rsync --recursive --checksum --info=progress --verbose deploy/ "$ssh_user:~tools.wdumps/scripts"
