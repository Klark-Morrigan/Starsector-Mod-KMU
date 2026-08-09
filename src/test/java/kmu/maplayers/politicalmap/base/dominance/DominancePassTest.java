package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link DominancePass}'s construction guard: a pass carries its rule and
 * grouping through a whole economy walk and dereferences both per system, so it rejects a null of
 * either at construction to fail fast rather than deep in the walk under a less legible error.
 *
 * <p>The per-system reads the pass drives - {@link DominancePass#readBlocFootprints},
 * {@link DominancePass#readBlocContributions}, and {@link DominancePass#tieBreakFor} - are covered
 * end to end by the {@link SectorPolitics}, {@link FilteredPolitics}, and stats-aggregation
 * integration suites, which exercise the pass over a stubbed economy.
 */
class DominancePassTest {

    @Nested
    class Constructor {

        @Test
        void rejectsNullRules() {
            assertThatThrownBy(() ->
                    new DominancePass(null, false, HolderGrouping.identity()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullGrouping() {
            assertThatThrownBy(() ->
                    new DominancePass(buildStabilityWeightedRules(), false, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
