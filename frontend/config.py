import os

basedir = os.path.abspath(os.path.dirname(__file__))

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_NAME = os.getenv("DB_NAME", "wdumper")
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD")

DB_URI = "mysql+mysqlconnector://{}@{}/{}".format(DB_USER + (":" + DB_PASSWORD if DB_PASSWORD is not None else ""), DB_HOST, DB_NAME)
DUMPS_PATH = os.getenv("DUMPS_PATH", os.path.join(basedir, "dumpfiles/generated"))
ZENODO_SANDBOX_TOKEN = os.getenv("ZENODO_SANDBOX_TOKEN", "")
ZENODO_TOKEN = os.getenv("ZENODO_TOKEN", "")
