package kmu.maplayers.base.geometry.v3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the lake spans asked for under a caller's own thinning, over the real
 * sectors.
 *
 * <p>The thinning is the subject: it is the one rule a caller may set apart from the laying's,
 * so what is pinned is that it changes which spans come back and nothing else, and that neither
 * answer is searched for twice.
 */
class BridgedContinentsIntegrationTests {

    @Nested
    class LayLakeSpans {

        @ParameterizedTest
        @MethodSource(SectorPipeline.SECTORS)
        void whereTheThinningAgreesTheSpansAreTheLayingsOwn(String sector) {
            // Not an equal set searched again but the same one: two readers set alike pay for
            // the search once.
            var laying = SectorPipeline.layContinentsIn(sector);

            assertThat(laying.layLakeSpans(SectorPipeline.SPAN_RULES.shouldThinFormations()))
                .isSameAs(laying.layLakeSpans());
        }

        @ParameterizedTest
        @MethodSource(SectorPipeline.SECTORS)
        void theOtherThinningIsSearchedOnceAndKept(String sector) {
            // Asked on every refresh of whatever lays it, so searching it each time would pay
            // the dearest search here per refresh.
            var laying = SectorPipeline.layContinentsIn(sector);
            var otherwise = !SectorPipeline.SPAN_RULES.shouldThinFormations();

            assertThat(laying.layLakeSpans(otherwise))
                .isSameAs(laying.layLakeSpans(otherwise));
        }

        @ParameterizedTest
        @MethodSource(SectorPipeline.SECTORS)
        void unthinnedKeepsEveryThinnedSpanAndMore(String sector) {
            // Thinning only drops, so the unthinned set is the thinned one with the chains and
            // fans put back - and on both fixtures there are some to put back, or the switch
            // would be tested over nothing.
            var laying = SectorPipeline.layContinentsIn(sector);
            var thinned = laying.layLakeSpans(true);

            assertThat(laying.layLakeSpans(false))
                .hasSizeGreaterThan(thinned.size())
                .usingRecursiveFieldByFieldElementComparator()
                .containsAll(thinned);
        }
    }
}
