import { el } from "redom";
import autocomplete from "autocompleter";

import * as m from "../model";
import { TagList } from "./TagList";

export class AdditionalSettings {
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
