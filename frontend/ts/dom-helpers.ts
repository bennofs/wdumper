import { el } from "redom";

const {buildRadioGroup} = new class {
    sequence = 0;
    buildRadioGroup = <T extends keyof any>(initial: string, choices: { [K in T]?: string }, handler: ((x: T) => void)): HTMLElement => {
        const radioName = "radio-" + this.sequence;
        this.sequence++;

        const node = el("li.radio-group", Object.keys(choices).map(value => {
            const label = choices[value];
            return el("li.radio-group--option", [
                el("input", {"type": "radio", "name": radioName, "value": value, "id": radioName + "-" + value, "checked": value == initial}),
                el("label", {"for": radioName + "-" + value}, label)
            ]);
        }));

        node.addEventListener("change", (event: Event) => {
            const target = event.target as HTMLInputElement;
            handler(target.value as T);
        })

        return node;
    }
}

export {buildRadioGroup};
