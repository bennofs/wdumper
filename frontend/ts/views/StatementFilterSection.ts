import { list, List } from "redom";

import * as m from "../model";
import { StatementFilter } from "./StatementFilter";

export class StatementFilterSection {
    readonly el: List;
    readonly container: HTMLElement;

    model: { [id: number]: m.StatementFilter }

    constructor(container: HTMLElement) {
        this.container = container
        this.el = list(this.container, StatementFilter.bind(undefined, (id: number) => this.remove(id)), "id")

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
        let initial: m.StatementFilter = m.createWithId({
            rank: "all",
            simple: true,
            full: false,
            references: false,
            qualifiers: false,
            properties: []
        });
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
