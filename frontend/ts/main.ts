import { el, list, List, mount } from "redom";
import autocomplete from "autocompleter";

import * as m from "./model";
import { buildRadioGroup } from "./dom-helpers";
import { completeWikidata } from "./complete";
import TagList from "./tag-list";

declare const ICONS_URL: string;
declare const LANGCODES: { [code:string]: {code: string, label: string}};

class PropertyMatcher {
    readonly el: HTMLElement;
    model: m.ValueFilter;
    id: number = null;

    readonly valueEl: HTMLInputElement;
    readonly propertyEl: HTMLInputElement;
    readonly removeEl: HTMLElement;

    readonly canRemove: (id: number) => boolean;

    constructor(canRemove: (id: number) => boolean, remove: (id: number) => void) {
        this.canRemove = canRemove;

        this.removeEl = el("a.img-button", [
            el("img", {"src": ICONS_URL + "/trash.svg", "alt": "remove"})
        ]);
        this.removeEl.addEventListener("click", (e) => {
            e.preventDefault();
            remove(this.id);
        })

        const group = buildRadioGroup("anyvalue", {
            "anyvalue": "exists",
            "entityid": "has value",
        }, (ty) => this.setType(ty));

        this.propertyEl = el("input", {"type": "text", placeholder: "P31"}) as HTMLInputElement;
        this.propertyEl.addEventListener("change", () => {
            this.model.property = this.propertyEl.value;
        });
        completeWikidata("property", this.propertyEl);

        this.valueEl = el("input", {"type":"text", placeholder: "Q5"}) as HTMLInputElement;
        this.valueEl.addEventListener("change", () => {
            this.model.value = this.valueEl.value;
        })
        completeWikidata("item", this.valueEl);

        this.el = el("tr", [
            el("td", this.propertyEl),
            el("td", group),
            el("td", this.valueEl),
            el("td", this.removeEl)
        ]);
    }

    setType(type: m.ValueFilter["type"]) {
        this.model.type = type;
        if (type == "entityid") {
            this.model.value = this.valueEl.value;
        } else {
            delete this.model.value;
        }
        this.sync();
    }

    update(model: m.ValueFilter) {
        this.model = model;
        this.id = model.id;
        this.propertyEl.value = this.model.property;
        this.valueEl.value = this.model.value || "";
        this.sync();
    }

    sync() {
        this.valueEl.classList.toggle("hide", this.model.type != "entityid");
        this.propertyEl.value = this.model.property;
        if (this.model.type == "entityid") {
            this.valueEl.value = this.model.value;
        }
        this.removeEl.classList.toggle("hide", !this.canRemove(this.id));
    }
}

class BasicEntityFilterView {
    readonly el: HTMLElement;
    readonly typeEl: HTMLSpanElement;
    readonly propertiesEl: List;

    model: m.EntityFilter;
    id: number;

    constructor(remove: (id: number) => void) {
        const addLink = el(
            "a.link-button",
            el("img", {'src': ICONS_URL + "/add.svg"}),
            "Add condition"
        );

        addLink.addEventListener("click", (e) => {
            e.preventDefault();
            this.add()
        });

        const deleteButton = el("a.img-button", [
            el("img", {'src': ICONS_URL + "/close.svg"}),
        ]);
        deleteButton.addEventListener("click", (e) => {
            e.preventDefault();
            remove(this.id)
        });

        this.el = el(".card", [
            el(".card-label", this.typeEl = el("span"), deleteButton),
            el(".card-main",
               el("table", 
                  el("tr",
                     el("th", "property"),
                     el("th", "constraint"),
                     el("th", "value"),
                     el("th", {"width": "40em"}, "")
                  ),
                  this.propertiesEl = list("tbody", PropertyMatcher.bind(
                      undefined,
                      (id) => Object.values(this.model.properties).length > 1,
                      (id) => this.remove(id)
                  ), "id")
                 ),
               el(".controls-add", addLink)
           )
        ])
    }

    update(model: m.EntityFilter) {
        this.model = model;
        this.id = model.id;

        this.typeEl.textContent = this.model.type;
        this.propertiesEl.update(Object.values(this.model.properties));
    }

    add() {
        const initial: m.ValueFilter = m.createWithId({
            property: "",
            type: "anyvalue",
            truthy: false
        });
        this.model.properties[initial.id] = initial;
        this.propertiesEl.update(Object.values(this.model.properties));
    }

    remove(id: number) {
        delete this.model.properties[id];
        this.update(this.model);
    }
}


class EntityFiltersView {
    readonly el: List;
    readonly emptyEl: HTMLElement;
    readonly container: HTMLElement;

    model: { [id: number]: m.EntityFilter }

    constructor(container: HTMLElement) {
        this.container = container
        this.emptyEl = document.getElementById("entity-match-all");
        this.el = list(this.container, BasicEntityFilterView.bind(undefined, (id: number) => this.remove(id)), "id")

        document.getElementById("add-item-filter").addEventListener("click", () => this.add("item"));
        document.getElementById("add-property-filter").addEventListener("click", () => this.add("property"));
    }

    add(type: m.EntityFilter["type"]) {
        const prop = m.createWithId({
            property: "",
            type: "exists",
            truthy: false
        });

        const properties = {}
        properties[prop.id] = prop;

        const initial: m.EntityFilter = m.createWithId({
            type,
            properties
        });

        this.model[initial.id] = initial;
        this.update(this.model);
    }

    remove(id: number) {
        delete this.model[id];
        this.update(this.model);
    }

    update(model: {[id:number]: m.EntityFilter}) {
        this.model = model;

        const matchers = Object.values(this.model);
        this.el.update(matchers);

        this.emptyEl.classList.toggle("hide", matchers.length != 0);
    }
}

class StatementFilterView {
    readonly el: HTMLElement;
    readonly propertyList: TagList;

    readonly labelEl: HTMLElement;
    readonly propertiesEl: HTMLElement;
    readonly defaultEl: HTMLElement;
    readonly removeEl: HTMLElement;

    readonly simpleStatementEl: HTMLInputElement;
    readonly fullStatementEl: HTMLInputElement;
    readonly referencesEl: HTMLInputElement;
    readonly qualifiersEl: HTMLInputElement;

    model: m.StatementFilter;
    id: number;

    constructor(remove: (id: number) => void) {
        this.el = el(".card.card-two", [
            el(".card-label", [
                this.labelEl = el("span" ,"Custom"),
                this.removeEl = el("a.img-button", [
                    el("img", {"src": ICONS_URL + "/close.svg", "alt": "remove"})
                ])
            ]),
            el(".card-main",
               this.propertiesEl = el(".left", [
                   el("h3", "Properties"),
                   this.propertyList = new TagList(completeWikidata.bind(undefined, "property"))
               ]),
               this.defaultEl = el(".left.hide", [
                   el("h3", "Default rule"),
                   el("span.form-label", "This rule is applied to all matched entities")
               ]),
               el(".right", [
                   el("h3", "Parts to export"),
                   el(".form-line",
                      el("p.form-label", "simple statement"),
                      this.simpleStatementEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
                   el(".form-line",
                      el("p.form-label", "full statement"),
                      this.fullStatementEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
                   el(".form-line",
                      el("p.form-label", "references"),
                      this.referencesEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
                   el(".form-line",
                      el("p.form-label", "qualifiers"),
                      this.qualifiersEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
               ])
            )
        ])

        this.simpleStatementEl.addEventListener("change", () => { this.model.simple = this.simpleStatementEl.checked });
        this.fullStatementEl.addEventListener("change", () => { this.model.full = this.fullStatementEl.checked });
        this.referencesEl.addEventListener("change", () => { this.model.references = this.referencesEl.checked });
        this.qualifiersEl.addEventListener("change", () => { this.model.qualifiers = this.qualifiersEl.checked });

        this.removeEl.addEventListener("click", () => {
            if (this.model.properties) {
                remove(this.id);
            }
        })
    }

    update(model: m.StatementFilter) {
        this.model = model;
        this.id = model.id;

        if (this.model.properties) {
            this.labelEl.textContent = "Custom";
            this.propertyList.update(this.model.properties);
            this.propertiesEl.style.removeProperty("display");
            this.defaultEl.style.display = "none";
            this.removeEl.style.removeProperty("display");
        } else {
            this.labelEl.textContent = "Default";
            this.propertiesEl.style.display = "none";
            this.defaultEl.style.removeProperty("display");
            this.removeEl.style.display = "none";
        }

        this.simpleStatementEl.checked = this.model.simple;
        this.fullStatementEl.checked = this.model.full;
        this.referencesEl.checked = this.model.references;
        this.qualifiersEl.checked = this.model.qualifiers;
    }
}

class StatementFiltersView {
    readonly el: List;
    readonly container: HTMLElement;

    model: { [id: number]: m.StatementFilter }

    constructor(container: HTMLElement) {
        this.container = container
        this.el = list(this.container, StatementFilterView.bind(undefined, (id: number) => this.remove(id)), "id")

        document.getElementById("add-statement-filter").addEventListener("click", (e) => {
            this.add()
            e.preventDefault();
        });
    }

    update(model: {[id: number]: m.StatementFilter}) {
        this.model = model;
        this.el.update(Object.values(this.model));
    }

    add(isDefault: boolean = false) {
        let initial = m.createWithId({
            simple: true,
            full: false,
            references: false,
            qualifiers: false,
            properties: []
        })
        if (isDefault) {
            delete initial.properties;
        }
        this.model[initial.id] = initial;
        this.update(this.model);
    }

    remove(id: number) {
        delete this.model[id];
        this.update(this.model);
    }
}

class AdditionalSettingsView {
    readonly el: HTMLElement;

    readonly languageFilterEl: HTMLInputElement;
    readonly languageListLabel: HTMLElement;
    readonly languageList: TagList;

    model: m.DumpSpec;
    savedLanguageList: string[] = [];


    readonly labelsEl: HTMLInputElement;
    readonly descriptionsEl: HTMLInputElement;
    readonly aliasesEl: HTMLInputElement;
    readonly sitelinksEl: HTMLInputElement;

    constructor() {
        this.el = el("div", [
            el(".form-line",
               el("p.form-label", "labels"),
               this.labelsEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
            el(".form-line",
               el("p.form-label", "descriptions"),
               this.descriptionsEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
            el(".form-line",
               el("p.form-label", "aliases"),
               this.aliasesEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
            el(".form-line",
               el("p.form-label", "sitelinks"),
               this.sitelinksEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
            el(".form-line",
               el("p.form-label", "filter languages"),
               el("span", [
                   el("p", [
                       this.languageFilterEl = el("input", {"type": "checkbox"}) as HTMLInputElement,
                       this.languageListLabel = el("span.form-label", "only include the following languages:"),
                   ]),
                   this.languageList = new TagList((input, onselect) => {
                       this.setupCompleter(input, onselect)
                   })
               ]))
        ]);

        this.labelsEl.addEventListener("change", () => {
            this.model.labels = this.labelsEl.checked;
        });

        this.descriptionsEl.addEventListener("change", () => {
            this.model.descriptions = this.descriptionsEl.checked;
        });

        this.aliasesEl.addEventListener("change", () => {
            this.model.aliases = this.aliasesEl.checked;
        });

        this.sitelinksEl.addEventListener("change", () => {
            this.model.sitelinks = this.sitelinksEl.checked; 
        });

        this.languageFilterEl.addEventListener("change", () => {
            if (this.languageFilterEl.checked) {
                this.model.languages = this.savedLanguageList;
            } else {
                if (this.model.languages) {
                    this.savedLanguageList = this.model.languages;
                }
                delete this.model.languages;
            }

            this.update(this.model);
        })
    }

    update(model: m.DumpSpec) {
        this.model = model;

        this.labelsEl.checked = this.model.labels;
        this.descriptionsEl.checked = this.model.descriptions;
        this.aliasesEl.checked = this.model.aliases;
        this.sitelinksEl.checked = this.model.sitelinks;

        if (this.model.languages) {
            this.languageFilterEl.checked = true;
            this.languageList.el.style.removeProperty("display");
            this.languageListLabel.style.removeProperty("display");
            this.languageList.update(this.model.languages);
        } else {
            this.languageFilterEl.checked = false;
            this.languageList.el.style.display = "none";
            this.languageListLabel.style.display = "none";
        }
    }



    setupCompleter(input: HTMLInputElement, onselect: () => void) {
        const self = this;

        autocomplete<{label: string, code: string}>({
            input,

            onSelect(item) {
                input.value = item.code;

                const changeEv = new Event("change");
                input.dispatchEvent(changeEv);

                onselect();
            },

            fetch(text, update) {
                let matchesPrefix = [];
                let matchesCode = [];
                let matchesLabel = [];

                for (let item of Object.values(LANGCODES)) {
                    // do not present as option if already added
                    if (self.model.languages.includes(item.code)) {
                        continue;
                    }

                    if (item.code.toLowerCase().startsWith(text.toLowerCase())) {
                        matchesPrefix.push(item);
                        continue;
                    }

                    if (item.code.toLowerCase().includes(text.toLowerCase())) {
                        matchesCode.push(item);
                        continue;
                    }

                    if (item.label.toLowerCase().includes(text.toLowerCase())) {
                        matchesLabel.push(item);
                    }
                }

                update(matchesPrefix.concat(matchesCode.concat(matchesLabel)));
            },

            customize(_input, _inputRect, container, _maxHeight) {
                container.style.width = "30rem";
            }
        })
    }
}

class MetadataView {
    readonly el: HTMLElement;
    readonly titleEl: HTMLInputElement;

    model: m.DumpMetadata;

    constructor() {
        this.el = el("", [
            el(".form-line", [
                el(".form-label", "Dump title"),
                this.titleEl = el("input", {"type": "text"}) as HTMLInputElement
            ])
        ])

        this.titleEl.addEventListener("change", () => {
            this.model.title = this.titleEl.value.trim();
            this.update(this.model);
        })
    }

    update(model: m.DumpMetadata) {
        this.model = model;

        this.titleEl.value = model.title;
    }
}

class DumpCreatorView {
    readonly entityFiltersView: EntityFiltersView;
    readonly statementFiltersView: StatementFiltersView;
    readonly additionalSettingsView: AdditionalSettingsView;
    readonly metadataView: MetadataView;
    readonly parent: HTMLElement;
    readonly model: m.DumpSpec;
    readonly metadata: m.DumpMetadata;

    constructor(parent: HTMLElement, init: m.DumpSpec) {
        this.parent = parent;
        this.model = init;
        this.metadata = { title: "" };

        this.entityFiltersView = new EntityFiltersView(document.getElementById("entity-filters"));
        this.statementFiltersView = new StatementFiltersView(document.getElementById("statement-filters"));
        this.additionalSettingsView = new AdditionalSettingsView();
        this.metadataView = new MetadataView()

        mount(document.getElementById("additional-settings"), this.additionalSettingsView);
        mount(document.getElementById("dump-metadata"), this.metadataView);

        document.getElementById("submit").addEventListener("click", () => {
            this.submit();
        });

        this.entityFiltersView.update(this.model.entities);
        this.statementFiltersView.update(this.model.statements);
        this.additionalSettingsView.update(this.model);
        this.metadataView.update(this.metadata);
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

const mainEl = document.getElementById("main");
const view = new DumpCreatorView(mainEl, initSpec);

view.statementFiltersView.add(true);
window['view'] = view
