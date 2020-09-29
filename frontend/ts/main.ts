import { mount } from "redom";

import * as m from "./model";
import * as v from "./views";

class DumpCreator {
    readonly entityFilterSectionView: v.EntityFilterSection;
    readonly statementFilterSectionView: v.StatementFilterSection;
    readonly additionalSettingsView: v.AdditionalSettings;
    readonly metadataSectionView: v.MetadataSection;
    readonly parent: HTMLElement;
    readonly elSamplingDesc: HTMLElement;
    readonly model: m.DumpSpec;
    readonly metadata: m.DumpMetadata;

    constructor(parent: HTMLElement, init: m.DumpSpec) {
        this.parent = parent;
        this.model = init;
        this.metadata = { title: "", description: "" };

        this.entityFilterSectionView = new v.EntityFilterSection(document.getElementById("entity-filters"));
        this.statementFilterSectionView = new v.StatementFilterSection(document.getElementById("statement-filters"));
        this.additionalSettingsView = new v.AdditionalSettings();
        this.metadataSectionView = new v.MetadataSection();

        mount(document.getElementById("additional-settings"), this.additionalSettingsView);
        mount(document.getElementById("dump-metadata"), this.metadataSectionView);

        const elSampling = document.getElementById("sampling-prop") as HTMLInputElement;
        this.elSamplingDesc = document.getElementById("sampling-desc");
        elSampling.addEventListener("input", () => {
            this.model.samplingPercent = Number.parseInt(elSampling.value);
            this.updateSamplingDesc();
        });
        this.updateSamplingDesc();
        elSampling.value = this.model.samplingPercent.toString();

        document.getElementById("submit").addEventListener("click", () => {
            this.submit();
        });

        this.entityFilterSectionView.update(this.model.entities);
        this.statementFilterSectionView.update(this.model.statements);
        this.additionalSettingsView.update(this.model);
        this.metadataSectionView.update(this.metadata);

        this.statementFilterSectionView.add(true);
    }

    updateSamplingDesc() {
        const amount = (this.model.samplingPercent == 100) ? "all" : (this.model.samplingPercent + "% of");
        this.elSamplingDesc.textContent = `keep ${amount} matched entites`;
    }

    submit() {
        const data: m.DumpSpec = {
            ...this.model,
            entities: Object.values(this.model.entities).map(entity => {
                return {
                    ...entity,
                    properties: Object.values(entity.properties)
                };
            }),
            statements: Object.values(this.model.statements),
        };

        fetch(API_URL + "dumps", {
            method: "POST",
            body: JSON.stringify({
                spec: data,
                metadata: this.metadata,
            }),
            headers: { "Content-Type": "application/json" }
        }).then(r => r.json()).then(r => {
            window.location = r["view-url"];
        })
    }
}

function mountCreate() {
    const initSpec: m.DumpSpec = {
        version: "1",
        entities: {},
        statements: {},
        samplingPercent: 100,

        labels: true,
        descriptions: true,
        aliases: true,
        meta: true,
        sitelinks: true
    };

    const dumpCreator = new DumpCreator(mainEl, initSpec);
    window['dumpCreator'] = dumpCreator
}

function mountInfo() {
    document.querySelectorAll("button.upload").forEach((button: HTMLElement) => {
        button.addEventListener("click", () => {
            const body = {
                id: button.dataset["dumpId"],
                target: button.dataset["target"]
            };
            fetch(ROOT + "zenodo", {
                method: "POST",
                body: JSON.stringify(body),
                headers: {"Content-Type": "application/json" }
            }).then(_ => {
                document.location.reload();
            })
        });
    });
}

const views = {
    "create": mountCreate,
    "info": mountInfo
}

const mainEl = document.getElementById("main");
if (mainEl && mainEl.dataset["view"]) {
    const controller = views[mainEl.dataset["view"]];
    controller();
}
