package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.politics.BlocStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sort mode's three surfaces: the frozen persistence keys a save round-trips through, the
 * trailing value each mode draws on a row, and the comparator that lays a bloc list out. The
 * comparator tests are the meat - they pin that each mode promotes its own metric to the primary key,
 * that the shared canonical chain breaks ties the same way behind every mode, and that a fully-level
 * pair falls back to a stable by-id order. All exercised on hand-built blocs, since the mode carries
 * no Starsector types.
 */
final class BlocSortModeTest {

    @Nested
    class FromKeyOrDefault {

        @Test
        void fromKeyOrDefaultResolvesAKnownKeyToItsMode() {
            assertThat(BlocSortMode.fromKeyOrDefault("presence")).isEqualTo(BlocSortMode.PRESENCE);
        }

        @Test
        void fromKeyOrDefaultFallsBackToDominationWhenTheKeyIsNull() {
            // A fresh save has stored no key, which must resolve to the default rather than fail.
            assertThat(BlocSortMode.fromKeyOrDefault(null)).isEqualTo(BlocSortMode.DEFAULT);
            assertThat(BlocSortMode.DEFAULT).isEqualTo(BlocSortMode.DOMINATION);
        }

        @Test
        void fromKeyOrDefaultFallsBackToDefaultWhenTheKeyIsUnrecognised() {
            // A key left by an older or modded build names no mode here, so the picker defaults rather
            // than failing on it.
            assertThat(BlocSortMode.fromKeyOrDefault("no_such_mode")).isEqualTo(BlocSortMode.DEFAULT);
        }
    }

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachMode() {
            // Pinned as literals: renaming a key silently resets every save that stored that mode back
            // to the default, so a change must break this test before it ships.
            assertThat(BlocSortMode.NAME.persistenceKey()).isEqualTo("name");
            assertThat(BlocSortMode.DOMINATION.persistenceKey()).isEqualTo("domination");
            assertThat(BlocSortMode.PRESENCE.persistenceKey()).isEqualTo("presence");
            assertThat(BlocSortMode.SCORE.persistenceKey()).isEqualTo("score");
            assertThat(BlocSortMode.MARKET_SIZE.persistenceKey()).isEqualTo("market_size");
        }
    }

    @Nested
    class ResolveTrailingValue {

        @Test
        void resolveTrailingValueIsTheModesMetricForANumericMode() {
            var stats = new BlocStats(5, 8, 40, 12);

            assertThat(BlocSortMode.DOMINATION.resolveTrailingValue(stats)).isEqualTo("5");
            assertThat(BlocSortMode.PRESENCE.resolveTrailingValue(stats)).isEqualTo("8");
            assertThat(BlocSortMode.SCORE.resolveTrailingValue(stats)).isEqualTo("40");
            assertThat(BlocSortMode.MARKET_SIZE.resolveTrailingValue(stats)).isEqualTo("12");
        }

        @Test
        void resolveTrailingValueIsBlankUnderTheNameMode() {
            // The name mode ranks on the label, so there is no number to show and the row draws blank.
            assertThat(BlocSortMode.NAME.resolveTrailingValue(new BlocStats(5, 8, 40, 12))).isEmpty();
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksANumericModeByItsOwnMetricHighToLow() {
            var low = bloc("low", "Low", new BlocStats(1, 0, 0, 0));
            var high = bloc("high", "High", new BlocStats(9, 0, 0, 0));

            assertThat(idsSortedBy(BlocSortMode.DOMINATION, low, high)).containsExactly("high", "low");
        }

        @Test
        void comparatorBreaksAMetricTieDownTheCanonicalChain() {
            // Level on domination, so the tie falls to presence next in the canonical chain: the higher
            // presence leads even though the mode being sorted is domination.
            var lowerPresence = bloc("a", "A", new BlocStats(5, 2, 0, 0));
            var higherPresence = bloc("b", "B", new BlocStats(5, 7, 0, 0));

            assertThat(idsSortedBy(BlocSortMode.DOMINATION, lowerPresence, higherPresence))
                    .containsExactly("b", "a");
        }

        @Test
        void comparatorPromotesEachModesMetricAheadOfTheCanonicalChain() {
            // Under score, the lower-dominating but higher-scoring bloc leads: score is promoted to the
            // primary key ahead of domination, which would otherwise win.
            var dominant = bloc("dominant", "Dominant", new BlocStats(9, 0, 10, 0));
            var scorer = bloc("scorer", "Scorer", new BlocStats(1, 0, 50, 0));

            assertThat(idsSortedBy(BlocSortMode.SCORE, dominant, scorer))
                    .containsExactly("scorer", "dominant");
        }

        @Test
        void comparatorRanksTheNameModeAlphabetically() {
            var zeta = bloc("z", "Zeta", BlocStats.EMPTY);
            var alpha = bloc("a", "Alpha", BlocStats.EMPTY);

            assertThat(idsSortedBy(BlocSortMode.NAME, zeta, alpha)).containsExactly("a", "z");
        }

        @Test
        void comparatorBreaksANameTieDownTheNumericChain() {
            // Two blocs share a name, so the name mode falls through to the numeric chain: the higher
            // dominating one leads.
            var weaker = bloc("weaker", "Same", new BlocStats(1, 0, 0, 0));
            var stronger = bloc("stronger", "Same", new BlocStats(8, 0, 0, 0));

            assertThat(idsSortedBy(BlocSortMode.NAME, weaker, stronger))
                    .containsExactly("stronger", "weaker");
        }

        @Test
        void comparatorFallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {
            // Same name and identical stats, so every visible key is level; the by-id key gives a total
            // order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = bloc("bbb", "Same", new BlocStats(3, 3, 3, 3));
            var first = bloc("aaa", "Same", new BlocStats(3, 3, 3, 3));

            assertThat(idsSortedBy(BlocSortMode.SCORE, second, first)).containsExactly("aaa", "bbb");
        }
    }

    // Sorts the blocs by the mode's comparator and returns their ids in the resulting order, so an
    // assertion reads the arrangement without the blocs' other fields getting in the way.
    private static List<String> idsSortedBy(BlocSortMode mode, SelectableBloc... blocs) {
        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(mode.comparator());
        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.blocId());
        }
        return ids;
    }

    private static SelectableBloc bloc(String id, String name, BlocStats stats) {
        return new SelectableBloc(id, name, null, stats);
    }
}
