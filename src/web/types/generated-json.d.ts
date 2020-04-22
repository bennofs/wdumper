/* tslint:disable */
/* eslint-disable */
// Generated using typescript-generator version 2.22-SNAPSHOT on 2020-04-22 16:40:03.

interface DumpRequest {
    meta: DumpRequestMeta;
    spec: DumpSpecJson;
}

interface ZenodoRequest {
    id: number;
    target: ZenodoTarget;
}

interface DumpSpecJson {
    meta: boolean;
    statements: StatementFilterJson[];
    languages: string[];
    entities: EntityFilterJson[];
    samplingPercent: number;
    sitelinks: boolean;
    seed: number;
    labels: boolean;
    descriptions: boolean;
    version: DumpSpecVersion;
    aliases: boolean;
}

interface DumpRequestMeta {
    title: string;
    description: string;
}

interface StatementFilterJson {
    simple: boolean;
    rank: RankFilter;
    qualifiers: boolean;
    references: boolean;
    properties: string[];
    full: boolean;
}

interface EntityFilterJson {
    properties: PropertyRestrictionJson[];
    type: EntityTypeFilter;
}

interface PropertyRestrictionJson {
    rank: RankFilter;
    property: string;
    type: Type;
    value: string;
}

type ZenodoTarget = "SANDBOX" | "RELEASE";

type DumpSpecVersion = "1";

type RankFilter = "best-rank" | "non-deprecated" | "all";

type EntityTypeFilter = "property" | "item" | "lexeme" | "any";

type Type = "novalue" | "somevalue" | "entityid" | "anyvalue" | "any";
