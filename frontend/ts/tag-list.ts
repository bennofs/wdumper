import { el, list, List } from "redom";

declare const ICONS_URL: string;

class Tag {
    readonly el: HTMLSpanElement;
    readonly textEl: HTMLSpanElement;

    constructor(remove: (data: string) => void) {
        const icon = el("span.tag-remove");
        this.el = el("span.tag", this.textEl = el("span") as HTMLSpanElement, icon);

        this.el.addEventListener("click", (e) => {
            remove(this.el.textContent)
        });
    }

    update(data) {
        this.textEl.textContent = data;
    }
}

export default class {
    readonly el: HTMLElement;
    readonly propertiesEl: List;
    readonly inputEl: HTMLInputElement;

    tags: string[] = [];
    completerCallback: (e: HTMLInputElement, select: () => void) => void;

    constructor(completerCallback: (e: HTMLInputElement, select: () => void) => void) {
        this.completerCallback = completerCallback;

        const img = el("img", {src: ICONS_URL + "/add.svg"});
        const form = el("form.tag-list-new", el(".img-input", [
            img,
            this.inputEl = el("input", {"type": "text"}) as HTMLInputElement
        ]));

        this.el = el(".tag-list-input", [
            this.propertiesEl = list(".tags", Tag.bind(undefined, (data) => this.remove(data))),
            form
        ]);

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            this.add();
        });

        img.addEventListener("click", (e) => {
            e.preventDefault();
            this.add();
        })
    }

    onmount() {
        this.completerCallback(this.inputEl, () => this.add());
    }

    remove(data) {
        for (let i = 0; i < this.tags.length; ++i) {
            if (this.tags[i] === data) {
                this.tags.splice(i, 1);
                break;
            }
        }
        this.update(this.tags);
    }

    update(tags: string[]) {
        this.tags = tags;

        this.propertiesEl.update(this.tags);
    }

    add() {
        const tag = this.inputEl.value;
        if (tag === "") return;
        
        this.tags.push(tag);
        this.inputEl.value = "";
        this.update(this.tags);
    }
}
