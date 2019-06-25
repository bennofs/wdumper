import { el, list, mount, List } from "redom";

interface ValueFilter {
    property: string,
    type: "novalue" | "somevalue" | "entityid" | "anyvalue" | "any";
    value?: string;
    truthy: boolean;
}

interface EntityFilter {
    type: "item" | "property" | "lexeme";
    properties: ValueFilter[];
}
const ENTITY_TYPES = ["item", "property", "lexeme"]

interface StatementFilter {
    properties?: String[];
    simple: boolean;
    full: boolean;
    references: boolean;
    qualifiers: boolean;
}

interface DumpSpec {
    entities: EntityFilter[];
    statements: StatementFilter[];
    languages: string[];

    labels: boolean;
    descriptions: boolean;
    aliases: boolean;
    truthy: boolean;
    meta: boolean;
    sitelinks: boolean;
}

const {buildRadioGroup} = new class {
    sequence = 0;
    buildRadioGroup = <T extends keyof any>(initial: string, choices: { [K in T]?: string }, handler: ((x: T) => void)): HTMLElement => {
        const radioName = "radio-" + this.sequence;
        this.sequence++;

        const node = el("li.radio-group", Object.keys(choices).map(value => {
            const label = choices[value];
            return el("li.radio-group--option", [
                el("input", {"type": "radio", "name": radioName, "value": value, "id": radioName + "-" + value, "checked": value == initial}),
                el("label", {"for": radioName + "-" + value}, label)
            ]);
        }));

        node.addEventListener("change", (event: Event) => {
            const target = event.target as HTMLInputElement;
            handler(target.value as T);
        })

        return node;
    }
}

class SinglePropertyMatcher {
    readonly el: HTMLElement;
    readonly model: ValueFilter;

    readonly valueEl: HTMLInputElement;
    readonly propertyEl: HTMLInputElement;

    constructor(model: ValueFilter) {
        this.model = model;
        const group = buildRadioGroup(this.model.type, {
            "anyvalue": "exists",
            "entityid": "entity",
        }, (ty) => this.setType(ty));

        this.propertyEl = el("input", {"type": "text", placeholder: "P31"}) as HTMLInputElement;
        this.propertyEl.addEventListener("blur", () => {
            this.model.property = this.propertyEl.value;
        });

        this.valueEl = el("input", {"type":"text", placeholder: "Q5"}) as HTMLInputElement;
        this.valueEl.addEventListener("blur", () => {
            this.model.value = this.valueEl.value;
        })

        this.el = el("li.form-line.prop-constraint", [
            this.propertyEl,
            group,
            this.valueEl
        ]);

        this.sync();
    }

    setType(type: ValueFilter["type"]) {
        this.model.type = type;
        if (type == "entityid") {
            this.model.value = this.valueEl.value;
        } else {
            delete this.model.value;
        }
        this.sync();
    }

    sync() {
        this.valueEl.classList.toggle("hide", this.model.type != "entityid");
        this.propertyEl.value = this.model.property;
        if (this.model.type == "entityid") {
            this.valueEl.value = this.model.value;
        }
    }
}

class PropertyEntityMatcher {
    readonly el: HTMLElement;
    readonly type: HTMLElement;
    readonly model: EntityFilter;
    readonly propertiesEl: HTMLElement;
    readonly propertyViews: SinglePropertyMatcher[];

    constructor(model: EntityFilter, id: number, remove: () => void) {
        this.model = model;

        const typeGroupEl = buildRadioGroup(this.model.type, {
            "item": "item",
            "property": "property",
            "lexeme": "lexeme"
        }, (type) => { this.model.type = type; });
        this.propertyViews = [];

        const addButton = el("button", "+");
        addButton.addEventListener("click", () => this.add())

        this.el = el(".form-group", [
            el(".form-group--label", "Property"),
            el(".form-group--main",
               el(".form-line", el("p.form-label", "entity type"), typeGroupEl),
               el(".form-line", el("p.form-label", "properties"), addButton),
               this.propertiesEl = el("ul")
              )
        ])
    }

    add() {
        const initial: ValueFilter = {
            property: "P31",
            type: "anyvalue",
            truthy: false
        }
        this.model.properties.push(initial);

        const view = new SinglePropertyMatcher(initial);
        this.propertyViews.push(view)

        this.propertiesEl.appendChild(view.el);
    }
}


class EntityFiltersView {
    readonly container: HTMLElement;
    readonly matchers: { [key:number]: PropertyEntityMatcher };

    nextId: number;

    constructor(container: HTMLElement, filters: EntityFilter[]) {
        this.container = container
        this.nextId = 0;
        this.matchers = {}

        for (const filter of filters) {
            this.add(filter)
        }

        document.getElementById("add-property-matcher").addEventListener("click", () => {
            this.add({
                "type": "item",
                "properties": []
            })
        })
    }

    remove(id: number) {
        // unmount
        this.matchers[id].el.remove();
        delete this.matchers[id];
    }

    add(filter: EntityFilter) {
        const id = this.nextId;
        const view = new PropertyEntityMatcher(filter, id, () => this.remove(id));

        this.matchers[this.nextId] = view;
        this.nextId += 1;

        // mount the new element
        this.container.appendChild(view.el);
    }
}

class StatementFilterView {
    readonly model: StatementFilter
}

class StatementFiltersView {
    readonly container: HTMLElement;
    readonly matchers: { [key:number]: StatementFilterView }

    nextId: number;

    constructor(container: HTMLElement, model: StatementFilter) {
        this.container = container
        this.nextId = 0;
        this.matchers = {}

        for (const filter of filters) {
            this.add(filter)
        }

        document.getElementById("add-property-matcher").addEventListener("click", () => {
            this.add({
                "type": "item",
                "properties": []
            })
        })
    }
}

class AdditionalSettingsView {
}

class DumpSpecView {
    readonly entityFiltersView: EntityFiltersView;
    readonly statementFiltersView: StatementFiltersView;
    readonly additionalSettingsView: AdditionalSettingsView;
    readonly parent: HTMLElement;
    readonly model: DumpSpec

    constructor(parent: HTMLElement, init: DumpSpec) {
        this.parent = parent;
        this.model = init

        const entityFiltersEl = document.getElementById("entity-filters");
        const statementFiltersEl = document.getElementById("statement-filters");
        const additionalSettingsEl = document.getElementById("additional-settings");

        this.entityFiltersView = new EntityFiltersView(document.getElementById("entity-filters"), this.model.entities);
        this.statementFiltersView = new StatementFiltersView(document.getElementById("statement-filters"), this.model.statements);
        this.additionalSettingsView = new AdditionalSettingsView();
    }
}

const initSpec: DumpSpec = {
    entities: [{
        type: "item",
        properties: []
    }],
    statements: [],
    languages: [],

    labels: true,
    descriptions: true,
    aliases: true,
    truthy: false,
    meta: true,
    sitelinks: true
};

const mainEl = document.getElementById("main");
window['view'] = new DumpSpecView(mainEl, initSpec);
