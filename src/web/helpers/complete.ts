import autocomplete from "autocompleter";

interface WikidataEntity {
    id: string,
    label: string,
    description: string
}

type WikidataEntityType = "item"|"property"|"lexeme"|"form"|"sense";

export function completeWikidata(type: WikidataEntityType, input: HTMLInputElement, onselect: () => void = () => {}) {
    autocomplete<WikidataEntity>({
        input,

        onSelect(item) {
            input.value = item.id;

            const changeEv = new Event("change");
            input.dispatchEvent(changeEv);

            onselect();
        },

        fetch(text, update) {
            const url = new URL("https://www.wikidata.org/w/api.php");
            url.search = new URLSearchParams({
                action: "wbsearchentities",
                search: text,
                language: "en",
                format: "json",
                type,
                origin: "*"
            }).toString();
            fetch(url.toString()).then(r => r.json()).then(result => {
                update(result.search as WikidataEntity[])
            });
        },

        render(item) {
            const el = document.createElement("div");
            const labelEl = document.createElement("div");
            const descriptionEl = document.createElement("div");
            labelEl.textContent = item.label + " (" + item.id + ")";
            labelEl.classList.add("label");
            descriptionEl.textContent = item.description;
            descriptionEl.classList.add("description");
            el.appendChild(labelEl);
            el.appendChild(descriptionEl);
            return el as HTMLDivElement;
        },

        customize(_input, _inputRect, container, _maxHeight) {
            container.style.width = "30rem";
        }
    });
}
