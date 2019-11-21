import { el } from "redom";

import * as m from "../model";

export class MetadataSection {
    readonly el: HTMLElement;
    readonly titleEl: HTMLInputElement;
    readonly descriptionEl: HTMLTextAreaElement;

    model: m.DumpMetadata;

    constructor() {
        this.el = el("", [
            el(".form-line", [
                el(".form-label", "Dump title"),
                this.titleEl = el("input.long", {"type": "text"}) as HTMLInputElement,
            ]),
            el(".form-line", [
                el(".form-label", "Dump description"),
                this.descriptionEl = el("textarea", {"cols": "80", "rows": "5"}) as HTMLTextAreaElement,
            ])
        ])

        this.titleEl.addEventListener("change", () => {
            this.model.title = this.titleEl.value.trim();
            this.update(this.model);
        })

        this.descriptionEl.addEventListener("change", () => {
            this.model.description = this.descriptionEl.value.trim();
            this.update(this.model);
        })


    }

    update(model: m.DumpMetadata) {
        this.model = model;

        this.titleEl.value = model.title;
        this.descriptionEl.value = model.description;
    }
}
