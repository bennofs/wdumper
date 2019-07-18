import { el } from "redom";

export interface RadioGroup<T> {
    el: HTMLElement;

    setValue(value: T): void;
}

const {buildRadioGroup} = new class {
    sequence = 0;
    buildRadioGroup = <T extends keyof any>(choices: { [K in T]?: string }, handler: ((x: T) => void)): RadioGroup<T> => {
        const radioName = "radio-" + this.sequence;
        this.sequence++;

        let radioOptions: {[K in T]?: HTMLInputElement} = {};
        for (const value of Object.keys(choices)) {
            radioOptions[value] = el("input", {
                "type": "radio",
                "name": radioName,
                "value": value,
                "id": radioName + "-" + value,
            });
        };

        const node = el("li.radio-group", Object.keys(choices).map(value => {
            const label = choices[value];
            const option = radioOptions[value];
            return el("li.radio-group--option", [
                option,
                el("label", {"for": radioName + "-" + value}, label)
            ]);
        }));

        node.addEventListener("change", (event: Event) => {
            const target = event.target as HTMLInputElement;
            handler(target.value as T);
        })

        return {
            el: node,

            setValue(value: T) {
                radioOptions[value].checked = true;
            }
        }
    }
}

export {buildRadioGroup};
