import { el } from "redom";

import * as m from "../model";
import { buildRadioGroup, RadioGroup } from "../dom-helpers";
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

    readonly rankRadio: RadioGroup<m.RankFilter>;
    readonly statementsRadio: RadioGroup<"complete"|"noreferences"|"none">;

    model: m.StatementFilter;
    id: number;

    constructor(remove: (id: number) => void) {
        this.rankRadio = buildRadioGroup({
            "best-rank": "best rank",
            "non-deprecated": "not deprecated",
            "all": "any"
        }, (rank) => this.model.rank = rank);

        this.statementsRadio = buildRadioGroup({
            "complete": "complete",
            "noreferences": "without references",
            "none": "none"
        }, (choice) => {
            switch(choice) {
                case "complete":
                    this.model.full = true;
                    this.model.qualifiers = true;
                    this.model.references = true;
                    break;
                case "noreferences":
                    this.model.full = true;
                    this.model.qualifiers = true;
                    this.model.references = false;
                    break;
                case "none":
                    this.model.full = false;
                    this.model.qualifiers = false;
                    this.model.references = false;
                    break;
            }
        });

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
               this.defaultEl = el(".left", [
                   el("h3", "Default rule"),
                   el("span.form-label", "This rule is applied if no other rules match.")
               ]),
               el(".right", [
                   el("h3", "How to export"),
                   el(".form-line",
                      el("p.form-label", "simple statements"),
                      this.simpleStatementEl = el("input", {"type": "checkbox"}) as HTMLInputElement),
                   el(".form-line",
                      el("p.form-label", "full statement mode"),
                      this.statementsRadio.el),
                   el(".form-line",
                      el("p.form-label", "export only with rank"),
                      this.rankRadio.el)
               ])
            )
        ])

        this.simpleStatementEl.addEventListener("change", () => { this.model.simple = this.simpleStatementEl.checked });

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
        this.rankRadio.setValue(this.model.rank);

        if (this.model.full) {
            if (this.model.references) {
                this.statementsRadio.setValue("complete");
            } else {
                this.statementsRadio.setValue("noreferences");
            }
        } else {
            this.statementsRadio.setValue("none");
        }
    }
}
