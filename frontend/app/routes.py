import json
import requests
from flask import render_template, render_template_string, request, jsonify, make_response, send_from_directory, url_for
from markupsafe import Markup
from urllib.parse import quote
from app import app, db
from app.models import Dump, ZenodoTarget, Zenodo, Run
from datetime import datetime, timedelta
import sqlalchemy.sql as sql

import config

@app.route("/")
def index():
    return render_template('index.html')

def render_404(msg):
    r = make_response(render_template("404.html", **locals()))
    r.status_code = 404
    return r

@app.route("/dumps")
def dumps():
    page = request.args.get('page', 1, type=int)
    dumps = Dump.query.order_by(Dump.created_at.desc()).paginate(page, 20, True)

    prev_url = url_for('dumps', page=dumps.prev_num) if dumps.has_prev else None
    next_url = url_for('dumps', page=dumps.next_num) if dumps.has_next else None

    return render_template('dumps.html', dumps=dumps, next_url=next_url, prev_url=prev_url)


@app.route("/dump/<int:n>")
def dump(n: int):
    dump = Dump.query.get(n)
    if dump is None:
        return render_404("Dump not found!")
    return render_template('dump.html', json=json, **locals())

@app.route("/create", methods=["POST"])
def create():
    data = request.get_json()

    spec = data["spec"]
    metadata = data["metadata"]

    dump = Dump(
        title=metadata["title"],
        description=metadata["description"],
        spec=json.dumps(spec)
    )
    db.session.add(dump)
    db.session.commit()

    return jsonify({
        "url": url_for('dump', n=dump.id)
    })

@app.route("/download/<int:id>", methods=["GET"])
def download(id: int):
    dump = Dump.query.get(id)
    if dump is None:
        return render_404("Dump not found!")
    if dump.run is None or dump.run.finished_at is None:
        return render_404("Dump not ready yet!")

    fname = "wdump-" + str(dump.id) + ".nt.gz"
    return send_from_directory(config.DUMPS_PATH, fname, as_attachment=True, attachment_filename=fname)

@app.route("/zenodo", methods=["POST"])
def zenodo():
    data = request.get_json()
    print(json.dumps(data))
    id = data["id"]
    dump = Dump.query.get(id)

    target = ZenodoTarget.RELEASE if data["target"] == "release" else ZenodoTarget.SANDBOX
    
    r = requests.post(target.endpoint("deposit/depositions"), headers=target.headers(), json={})
    r.raise_for_status()
    record = r.json()

    doi = record["metadata"]["prereserve_doi"]["doi"]

    r = requests.put(target.endpoint("deposit/depositions/" + str(record["id"])), headers=target.headers(), json={
        'metadata': {
            'title': "Wikidata Dump " + dump.title,
            'upload_type': 'dataset',
            'access_right': 'open',
            'license': 'cc-zero',
            'description': '''
            <p>
            RDF dump of wikidata produced with <a href="https://tools.wmflabs.org/wdumps/">wdumps</a>.
            </p>

            <p>
            {dump.description}<br>
            <a href="https://tools.wmflabs.org/wdumps/dump/{dump.id}">View on wdumper</a>
            </p>

            <p>
            <b>entity count</b>: {dump.entity_count}, <b>statement count</b>: {dump.statement_count}, <b>triple count</b>: {dump.triple_count}
            </p>
            '''.strip().format(dump=dump),
            'creators': [{'name': "Benno Fünfstück"}],
            'prereserve_doi': True,
            'doi': doi,
        }
    })
    r.raise_for_status()

    zenodo = Zenodo(deposit_id=record["id"], dump_id=id, doi=doi, target=target)
    db.session.add(zenodo)
    db.session.commit()

    return ""

def time_to_age(value):
    if value is None:
        return 0
    return int(datetime.utcnow().timestamp() - value.timestamp())

@app.route("/status", methods=["GET"])
def status():
    # collect some status metrics
    queue_length = Dump.query.filter_by(run=None).count()
    queue_min_age, queue_max_age = db.session.query(
        sql.func.min(Dump.created_at),
        sql.func.max(Dump.created_at)
    ).filter(Dump.in_queue).one()
    unfinished_min_age, unfinished_max_age = db.session.query(
        sql.func.min(Dump.created_at),
        sql.func.max(Dump.created_at)
    ).outerjoin(Run).filter(
        sql.or_(Dump.run == None, Run.finished_at == None)
    ).one()
    runs_in_progress = Run.query.filter_by(finished_at=None).count()
    next_run_min = None
    next_run_max = None
    valid_next_run = False
    if not runs_in_progress and queue_min_age is not None:
        next_run_min = queue_min_age + timedelta(minutes=config.RECENT_MIN_MINUTES)
        next_run_max = queue_max_age + timedelta(minutes=config.RECENT_MAX_MINUTES)
        valid_next_run = next_run_min >= datetime.utcnow() and next_run_max >= datetime.utcnow()

    # return as JSON if requested
    if not request.accept_mimetypes["text/html"]:
        return json.dumps({
            "queue_length": queue_length,
            "queue_max_age": time_to_age(queue_max_age),
            "unfinished_min_age": time_to_age(unfinished_max_age),
            "unfinished_max_age": time_to_age(unfinished_max_age),
            "runs_in_progress": runs_in_progress,
            "next_run_min": time_to_age(next_run_min),
            "next_run_max": time_to_age(next_run_max)
        })

    return render_template("status.html", **locals())

@app.template_filter("timedelta")
def date2delta(delta, invert=False):
    if not delta:
        delta = timedelta()

    if isinstance(delta, datetime):
        delta = datetime.utcnow() - delta if not invert else delta - datetime.utcnow()
    
    if delta.days:
        return "{}d{}h".format(delta.days, delta.seconds // (60**2))

    minutes = delta.seconds // 60
    seconds = delta.seconds - minutes * 60

    hours = minutes // 60
    minutes = minutes - hours * 60

    if hours:
        return "{}h:{:02}m".format(hours, minutes)

    if minutes:
        return "{}m:{:02}s".format(minutes, seconds)

    return "{:02} secs".format(seconds)

@app.template_filter("ghversion")
def ghversion(version, user, project):
    if version is None:
        return "unknown"

    version = version.strip()
    if version.startswith("release-"):
        name = version
        link = "https://github.com/" + quote(user) + "/" + quote(project) + "/releases/" + version[8:]
    else:
        name = "git-" + version[:10]
        link = "https://github.com/" + quote(user) + "/" + quote(project) + "/commit/" + version

    return Markup(render_template_string('<a href="{{link}}"><code>{{name}}</code></a>', **locals()))
