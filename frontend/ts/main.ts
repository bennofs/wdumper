import { mount } from "redom";

import * as m from "./model";
import * as v from "./views";

class DumpCreator {
    readonly entityFilterSectionView: v.EntityFilterSection;
    readonly statementFilterSectionView: v.StatementFilterSection;
    readonly additionalSettingsView: v.AdditionalSettings;
    readonly metadataSectionView: v.MetadataSection;
    readonly parent: HTMLElement;
    readonly model: m.DumpSpec;
    readonly metadata: m.DumpMetadata;

    constructor(parent: HTMLElement, init: m.DumpSpec) {
        this.parent = parent;
        this.model = init;
        this.metadata = { title: "" };

        this.entityFilterSectionView = new v.EntityFilterSection(document.getElementById("entity-filters"));
        this.statementFilterSectionView = new v.StatementFilterSection(document.getElementById("statement-filters"));
        this.additionalSettingsView = new v.AdditionalSettings();
        this.metadataSectionView = new v.MetadataSection()

        mount(document.getElementById("additional-settings"), this.additionalSettingsView);
        mount(document.getElementById("dump-metadata"), this.metadataSectionView);

        document.getElementById("submit").addEventListener("click", () => {
            this.submit();
        });

        this.entityFilterSectionView.update(this.model.entities);
        this.statementFilterSectionView.update(this.model.statements);
        this.additionalSettingsView.update(this.model);
        this.metadataSectionView.update(this.metadata);

        this.statementFilterSectionView.add(true);
    }

    submit() {
        const data = {
            ...this.model,
            entities: Object.values(this.model.entities).map(entity => {
                return {
                    ...entity,
                    properties: Object.values(entity.properties)
                };
            }),
            statements: Object.values(this.model.statements),
        };

        fetch("/create", {
            method: "POST",
            body: JSON.stringify({
                spec: data,
                metadata: this.metadata,
            }),
            headers: { "Content-Type": "application/json" }
        }).then(r => r.json()).then(r => {
            window.location = r.url;
        })
    }
}

function mountCreate() {
    const initSpec: m.DumpSpec = {
        entities: {},
        statements: {},

        labels: true,
        descriptions: true,
        aliases: true,
        truthy: false,
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
            fetch("/zenodo", {
                method: "POST",
                body: JSON.stringify(body),
                headers: {"Content-Type": "application/json" }
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

