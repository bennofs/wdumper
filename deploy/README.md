# Scripts and support files for toolforge

This directory hosts files required for the deployment of the tool on the toolforge.

## Scripts

- `clean-old.sh`: remove old dumpfiles. should be run by cron 
- `clear-failed-run.sh RUNID`: restart all dumps that were assigned to run `RUNID`
- `clean-uploaded.sh`: remove dumpfiles which have been upoaded to zenodo successfully
- `deploy-toolforge.sh`: runs all steps to deploy the tool to toolforge (run on local machine)
- `deploy-toolforge-copy.sh`: sync files to toolforge after building (run `./gradlew installDist` before to build the distribution)
- `query-stuck.sh`: find runs which didn't finish (got started too far in the past)
- `toolforge-web.sh`: script to run on toolforge to start the webservice
- `toolforge-worker.sh`: script to run on toolforge to start the backend worker
- `toolforge-daily.sh`: script to run on toolforge to create the daily cron job
- `job-daily.sh`: script that is started on a toolforge grid compute node
- `job-web.sh`: script that is executed by a toolforge webservice 
- `job-worker.sh`: script that is started on a toolforge grid compute node

## Files

- `webservice.template`: Template file for the toolforge webservice
