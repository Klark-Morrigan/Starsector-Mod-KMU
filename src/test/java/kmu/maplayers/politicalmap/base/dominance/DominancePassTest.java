package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.systems.SystemColoniesIndex;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit coverage for {@link DominancePass}'s construction guard: a pass carries its rule, grouping
 * and colony walk through a whole sector walk and dereferences each per system, so it rejects a
 * null of any of them at construction to fail fast rather than deep in the walk under a less
 * legible error.
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
                    new DominancePass(
                        null,
                        false,
                        HolderGrouping.identity(),
                        new SystemColoniesIndex(null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullGrouping() {
            assertThatThrownBy(() ->
                    new DominancePass(
                        buildStabilityWeightedRules(),
                        false,
                        null,
                        new SystemColoniesIndex(null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullColonies() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new DominancePass(
                        buildStabilityWeightedRules(),
                        false,
                        HolderGrouping.identity(),
                        null))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
