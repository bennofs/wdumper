import autocomplete from "autocompleter";
import { el } from "redom";

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
            return el(
                "",
                el(".label", item.label + " (" + item.id + ")"),
                el(".description", item.description)
            ) as HTMLDivElement
        },

        customize(_input, _inputRect, container, _maxHeight) {
            container.style.width = "30rem";
        }
    });
}
