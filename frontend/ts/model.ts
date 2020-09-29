export const { createWithId } = new class {
    sequence: number = 0;
    createWithId = <T>(t: T): T & { id: number } => {
        const id = this.sequence;
        this.sequence += 1;
        return {id, ...t};
    };
};

export interface ValueFilter {
    id: number;
    property: string;
    rank: RankFilter;
    type: "novalue" | "somevalue" | "entityid" | "anyvalue" | "any";
    value?: string;
}

export type RankFilter = "best-rank" | "non-deprecated" | "all";

export interface BasicEntityFilter {
    id: number;
    type: "item" | "property" | "lexeme";
    properties: { [id:number]: ValueFilter };
}

export interface StatementFilter {
    id: number;
    rank: RankFilter;
    properties?: string[];
    simple: boolean;
    full: boolean;
    references: boolean;
    qualifiers: boolean;
}

export interface DumpSpec {
    version: String;
    entities: { [id:number]: BasicEntityFilter };
    statements: { [id:number]: StatementFilter };
    samplingPercent?: number;
    languages?: string[];
    seed?: number;

    labels: boolean;
    descriptions: boolean;
    aliases: boolean;
    meta: boolean;
    sitelinks: boolean;
}

export interface DumpMetadata {
    title: string;
    description: string;
}
