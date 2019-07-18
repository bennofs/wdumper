import { el } from "redom";

import * as m from "../model";

export class MetadataSection {
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
