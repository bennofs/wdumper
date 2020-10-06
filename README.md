# WDumper [![](https://github.com/bennofs/wdumper/workflows/Java%20CI/badge.svg?branch=master)](https://github.com/bennofs/wdumper/actions)
_A tool to create customized Wikidata RDF dumps_

This project contains the source code for the application running at https://tools.wmflabs.org/wdumps.

## Building

To build the backend, run `gradle build` in the root directory.  
To build the frontend, run:

```sh
$ cd frontend
$ python3 -m venv env
$ source env/bin/activate
$ pip install -r requirements.txt
$ npm install && npm run all
```

## Running

The system requires a MariaDB/MySQL database.
The default config will try to connect to `root@localhost` (without password)
with the database name `wdumper`. You can change these values with the 
environment variables `DB_HOST`, `DB_USER`, `DB_PASSWORD` and `DB_NAME`.

To initialize the database and start the frontend server, run:
```sh
$ cd frontend
$ source env/bin/activate
$ flask db migrate
$ flask run
```
You should then be able to access the web application at http://localhost:5000/.

Start the backend, which processes the dump requests from the frontend, with:
```
$ java -jar build/wdumper-all.jar /path/to/wikidata-20191111-all.json.gz 
```

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
