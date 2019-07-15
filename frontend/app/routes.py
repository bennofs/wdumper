import json
from flask import render_template, request, jsonify, make_response, send_from_directory
from app import app, db
from app.models import Dump

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
        spec=json.dumps(spec)
    )
    db.session.add(dump)
    db.session.commit()

    return jsonify({
        "url": "/dump/{}".format(dump.id)
    })

@app.route("/download/<int:id>", methods=["GET"])
def download(id: int):
    dump = Dump.query.get(id)
    if dump is None:
        return render_404("Dump not found!")
    if dump.run is None or dump.run.finished_at is None:
        return render_404("Dump not ready yet!")

    fname = "wdump-" + str(dump.id) + ".nt.gz"
    return send_from_directory(config.DUMPS_PATH, str(dump.id) + ".nt.gz", as_attachment=True, attachment_filename=fname)
