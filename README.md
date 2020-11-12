# WDumper [![](https://github.com/bennofs/wdumper/workflows/Java%20CI/badge.svg?branch=master)](https://github.com/bennofs/wdumper/actions)
_A tool to create customized Wikidata RDF dumps_

This project contains the source code for the application running at https://tools.wmflabs.org/wdumps.

## Building

To build the project, run `gradle build` in the root directory.  

## Environment variables for configuration

| Name                 | Description                               | Default                      |
|----------------------|-------------------------------------------|------------------------------|
| DB_HOST              | hostname for MariaDB connection           | localhost                    |
| DB_USER              | username to connect to the DB             | root                         |
| DB_PASSWORD          | password to connect to the DB             |                              |
| DB_NAME              | name of the database to use               | wdumper                      |
| ZENODO_SANDBOX_TOKEN | API token for uploads to zenodo sandbox   |                              |
| ZENODO_TOKEN         | API token for uploads to main zenodo      |                              |
| DUMPS_PATH           | path where the generated dumps are stored | frontend/dumpfiles/generated |
| PUBLIC_URL           | Public URL pointing to the web server     | http://localhost:5050/       |
