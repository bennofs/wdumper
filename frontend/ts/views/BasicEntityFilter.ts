import { el, list, List } from "redom";

import * as m from "../model";
import { buildRadioGroup, RadioGroup } from "../dom-helpers";
import { completeWikidata } from "../complete";

class PropertyRestriction {
    readonly el: HTMLElement;
    model: m.ValueFilter;
    id: number = null;

    readonly valueEl: HTMLInputElement;
    readonly propertyEl: HTMLInputElement;
    readonly removeEl: HTMLElement;
    readonly typeRadio: RadioGroup<m.ValueFilter["type"]>;
    readonly rankRadio: RadioGroup<m.RankFilter>;

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

        this.typeRadio = buildRadioGroup({
            "anyvalue": "exists",
            "entityid": "has value",
        }, (ty) => this.setType(ty));

        this.rankRadio = buildRadioGroup({
            "best-rank": "best rank",
            "non-deprecated": "not deprecated",
            "all": "any"
        }, (rank) => this.model.rank = rank);

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

        this.el = el("div.form-line", [
            el(".span", "property"),
            this.propertyEl,
            this.typeRadio.el,
            this.valueEl,
            el(".span", "with rank"),
            this.rankRadio.el,
            this.removeEl,
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
        this.rankRadio.setValue(this.model.rank);
        this.sync();
    }

    sync() {
        this.valueEl.classList.toggle("invisible", this.model.type != "entityid");
        this.propertyEl.value = this.model.property;
        if (this.model.type == "entityid") {
            this.valueEl.value = this.model.value;
        }
        this.removeEl.classList.toggle("invisible", !this.canRemove(this.id));
        this.typeRadio.setValue(this.model.type);
    }
}

export class BasicEntityFilter {
    readonly el: HTMLElement;
    readonly typeRadio: RadioGroup<m.EntityFilter["type"]>;
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

        const deleteButton = el("a.img-button", {"title": "remove"}, [
            el("img", {'src': ICONS_URL + "/close.svg"}),
        ]);
        deleteButton.addEventListener("click", (e) => {
            e.preventDefault();
            remove(this.id)
        });

        this.typeRadio = buildRadioGroup({
            "item": "items",
            "property": "properties"
        }, (value) => {
            this.model.type = value;
        });

        this.el = el(".card", [
            el(".card-corner", deleteButton),
            el(".card-main",
               el(".form-line", [
                   el(".form-text", "select"),
                   this.typeRadio.el,
                   el(".form-text", "which match all of these conditions:"),
               ]),
               this.propertiesEl = list("div.card-subsection", PropertyRestriction.bind(
                   undefined,
                   (_id: number) => Object.values(this.model.properties).length > 1,
                   (id: number) => this.remove(id)
               ), "id"),
               el(".controls-add", addLink)
           )
        ])
    }

    update(model: m.EntityFilter) {
        this.model = model;
        this.id = model.id;

        this.typeRadio.setValue(this.model.type);
        this.propertiesEl.update(Object.values(this.model.properties));
    }

    add() {
        const initial: m.ValueFilter = m.createWithId({
            property: "",
            rank: "non-deprecated",
            type: "anyvalue",
        });
        this.model.properties[initial.id] = initial;
        this.propertiesEl.update(Object.values(this.model.properties));
    }

    remove(id: number) {
        delete this.model.properties[id];
        this.update(this.model);
    }
}
