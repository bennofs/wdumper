import { el } from "redom";

import * as m from "../model";
import { completeWikidata } from "../complete";
import { TagList } from "./TagList";

export class StatementFilter {
    readonly el: HTMLElement;
    readonly propertyList: TagList;

    readonly propertiesEl: HTMLElement;
    readonly defaultEl: HTMLElement;
    readonly removeEl: HTMLElement;
    readonly cornerEl: HTMLElement;

    readonly simpleStatementEl: HTMLInputElement;
    readonly fullStatementEl: HTMLInputElement;
    readonly referencesEl: HTMLInputElement;
    readonly qualifiersEl: HTMLInputElement;

    model: m.StatementFilter;
    id: number;

    constructor(remove: (id: number) => void) {
        this.el = el(".card.card-two", [
            this.cornerEl = el(".card-corner", [
                this.removeEl = el("a.img-button", {"title": "remove"}, [
                    el("img", {"src": ICONS_URL + "/close.svg"})
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
            this.propertyList.update(this.model.properties);
            this.propertiesEl.style.removeProperty("display");
            this.defaultEl.style.display = "none";
            this.cornerEl.style.removeProperty("display");
        } else {
            this.propertiesEl.style.display = "none";
            this.defaultEl.style.removeProperty("display");
            this.cornerEl.style.display = "none";
        }

        this.simpleStatementEl.checked = this.model.simple;
        this.fullStatementEl.checked = this.model.full;
        this.referencesEl.checked = this.model.references;
        this.qualifiersEl.checked = this.model.qualifiers;
    }
}
