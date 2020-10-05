# Scripts and support files for toolforge

This directory hosts files required for the deployment of the tool on the toolforge.

## Scripts

- `clean-old.sh`: remove old dumpfiles. should be run by cron 
- `clear-failed-run.sh RUNID`: restart all dumps that were assigned to run `RUNID`
- `query-stuck.sh`: find runs which didn't finish (got started too far in the past)
- `deploy-web.sh`: ensure web frontend is deployed and up-to-date (performs updates if necessary)
- `deploy-worker.sh`: ensure backend worker is running and up-to-date (performs updates if necessary)
- `worker.sh`: script that is started on a toolforge grid compute node

## Files

- `web.yaml`: kubernetes spec file for the web frontend
