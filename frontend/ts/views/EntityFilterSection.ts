import { list, List } from "redom";

import * as m from "../model";
import { BasicEntityFilter } from "./BasicEntityFilter";

export class EntityFilterSection {
    readonly el: List;
    readonly emptyEl: HTMLElement;
    readonly container: HTMLElement;

    model: { [id: number]: m.BasicEntityFilter };

    constructor(container: HTMLElement) {
        this.container = container;
        this.emptyEl = document.getElementById("entity-match-all");
        this.el = list(this.container, BasicEntityFilter.bind(undefined, (id: number) => this.remove(id)), "id");

        document.getElementById("add-basic-filter").addEventListener("click", () => this.add("item"));
    }

    add(type: m.BasicEntityFilter["type"]) {
        const prop: m.ValueFilter = m.createWithId({
            property: "",
            type: "anyvalue",
            rank: "non-deprecated",
        });

        const properties: m.BasicEntityFilter["properties"] = {};
        properties[prop.id] = prop;

        const initial: m.BasicEntityFilter = m.createWithId({
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

    update(model: {[id:number]: m.BasicEntityFilter}) {
        this.model = model;

        const matchers = Object.values(this.model);
        this.el.update(matchers);

        this.emptyEl.classList.toggle("hide", matchers.length != 0);
    }
}
