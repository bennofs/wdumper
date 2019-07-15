import enum
from flask import url_for
from datetime import datetime
from sqlalchemy.sql import func

from app import db

class ErrorLevel(enum.Enum):
    CRITICAL = 0,
    ERROR = 1,
    WARNING = 2

class Dump(db.Model):
    id = db.Column(db.Integer, primary_key=True, nullable=False)
    title = db.Column(db.UnicodeText, nullable=False)
    spec = db.Column(db.Text, nullable=False)
    created_at = db.Column(db.TIMESTAMP, index=True, nullable=False, server_default=func.now())
    run_id = db.Column(db.Integer, db.ForeignKey("run.id"), nullable=True)
    completed_at = db.Column(db.TIMESTAMP, nullable=True, index=True)
    zenodo_uri = db.Column(db.Text, nullable=True)

    run = db.relationship("Run")

    @property
    def done(self):
        return self.run is not None and self.run.finished_at is not None

    @property
    def download_link(self):
        return url_for('download', id=self.id)

class Run(db.Model):
    id = db.Column(db.Integer, primary_key = True, nullable=False)
    started_at = db.Column(db.TIMESTAMP, nullable=True)
    finished_at = db.Column(db.TIMESTAMP, nullable=True)
    count = db.Column(db.Integer, nullable=False, server_default=db.text("0"))

class DumpError(db.Model):
    id = db.Column(db.Integer, primary_key = True, nullable=False)
    logged_at = db.Column(db.TIMESTAMP, nullable=False, server_default=func.now())
    dump_id = db.Column(db.Integer, db.ForeignKey("dump.id"), nullable=True)
    run_id = db.Column(db.Integer, db.ForeignKey("run.id"), nullable=False)
    level = db.Column(db.Enum(ErrorLevel), nullable=False)
    message = db.Column(db.Text, nullable=False)
