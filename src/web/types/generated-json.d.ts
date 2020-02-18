/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator version 2.19.577 on 2020-02-17 17:02:31.

interface DumpRequest {
    meta: DumpRequestMeta;
    spec: DumpSpecJson;
}

interface ZenodoRequest {
    id: number;
    target: ZenodoTarget;
}

interface DumpSpecJson {
    statements: StatementFilterJson[];
    languages: string[];
    entities: EntityFilterJson[];
    sitelinks: boolean;
    samplingPercent: number;
    labels: boolean;
    meta: boolean;
    seed: number;
    descriptions: boolean;
    aliases: boolean;
}

interface DumpRequestMeta {
    title: string;
    description: string;
}

interface StatementFilterJson {
    simple: boolean;
    references: boolean;
    full: boolean;
    rank: RankFilter;
    qualifiers: boolean;
    properties: string[];
}

interface EntityFilterJson {
    type: EntityTypeFilter;
    properties: PropertyRestrictionJson[];
}

interface PropertyRestrictionJson {
    rank: RankFilter;
    property: string;
    type: Type;
    value: string;
}

type ZenodoTarget = "SANDBOX" | "RELEASE";

type RankFilter = "best-rank" | "non-deprecated" | "all";

type EntityTypeFilter = "property" | "item" | "lexeme" | "any";

type Type = "novalue" | "somevalue" | "entityid" | "anyvalue" | "any";
