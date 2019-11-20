package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;

public enum RankFilter {
    @JsonProperty ("best-rank") BEST_RANK,
    @JsonProperty ("non-deprecated") NON_DEPRECATED,
    @JsonProperty ("all") ALL;

    public boolean matches(StatementRank rank, boolean best) {
        switch(this) {
            case BEST_RANK:
                return best;
            case NON_DEPRECATED:
                return rank != StatementRank.DEPRECATED;
            case ALL:
                return true;
        }
        throw new RuntimeException("invalid RankFilter value (impossible)");
    }

    public RankFilter union(RankFilter rank) {
        switch(this) {
            case BEST_RANK:
                return rank;
            case NON_DEPRECATED:
                if (rank != BEST_RANK) return rank;
                return this;
            case ALL:
                return this;
        }
        throw new RuntimeException("invalid RankFilter value (impossible)");
    }
}
