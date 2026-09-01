package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one declaration both painted vocabularies now share: the frozen key a save round-trips
 * through, the size it reads whichever metrics record carries it, the direction that follows, and
 * that a tie on the size falls through to the chain the declaring vocabulary bound in rather than to
 * one spelled here. Exercised over both records, since a mode declared once has to answer identically
 * under each.
 *
 * <p>The shape the ranking is then laid out in - the promoted key, the flip, the name and by-id tail -
 * is the shared assembly's and is pinned in its own suite. All on hand-built blocs, since neither
 * record carries a Starsector type.
 */
final class BlocMarketSizeSortModeTest {

    // The tone the picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
    // any engine shade, since what the value cases read off it is only that the offered tone came back
    // on the run rather than one the mode chose.
    private static final Color ROW_COLOUR = Color.ORANGE;

    // The two bindings, each over the chain its own vocabulary declares: domination leads the held
    // views' chain, claims leads the claims view's. Naming both here is what lets a tie on the size
    // show which chain the bound mode actually fell through to.
    private static final BlocMarketSizeSortMode<DominanceStats> DOMINANCE_BINDING =
        new BlocMarketSizeSortMode<>(
            List.of(
                DominanceStats::domination,
                DominanceStats::presence,
                DominanceStats::score,
                SizedBlocMetrics::marketSize));

    private static final BlocMarketSizeSortMode<ClaimStats> CLAIMS_BINDING =
        new BlocMarketSizeSortMode<>(
            List.of(
                ClaimStats::claims,
                SizedBlocMetrics::marketSize));

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyUnderEveryVocabulary() {

            // Pinned as a literal, and as one literal: both vocabularies stored this spelling before
            // the declaration was shared, so a change here resets every save that stored the size
            // ranking under either view.
            assertThat(DOMINANCE_BINDING.persistenceKey())
                .isEqualTo("market_size");
            assertThat(CLAIMS_BINDING.persistenceKey())
                .isEqualTo("market_size");
        }
    }

    @Nested
    class ResolveTrailingRuns {

        @Test
        void resolveTrailingRunsIsTheWholeSectorSizeAsOneRowColouredRun() {

            // The same reading off either record: one run, in the tone the picker offered, since a
            // colony size carries no colour of its own.
            assertThat(DOMINANCE_BINDING.resolveTrailingRuns(
                    buildDominanceBloc("hegemony", "Hegemony", new DominanceStats(5, 8, 40, 12)),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("12", ROW_COLOUR));

            assertThat(CLAIMS_BINDING.resolveTrailingRuns(
                    buildClaimBloc("hegemony", "Hegemony", new ClaimStats(4, 26)),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("26", ROW_COLOUR));
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void defaultDirectionIsDescendingUnderEveryVocabulary() {

            // A number leads with the bigger bloc, so the size runs high-to-low wherever it is offered.
            assertThat(DOMINANCE_BINDING.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(CLAIMS_BINDING.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksTheBiggerBlocFirst() {

            assertThat(listIdsSortedBy(
                    DOMINANCE_BINDING,
                    buildDominanceBloc("small", "Small", new DominanceStats(0, 0, 0, 3)),
                    buildDominanceBloc("large", "Large", new DominanceStats(0, 0, 0, 40))))
                .containsExactly("large", "small");

            assertThat(listIdsSortedBy(
                    CLAIMS_BINDING,
                    buildClaimBloc("small", "Small", new ClaimStats(0, 3)),
                    buildClaimBloc("large", "Large", new ClaimStats(0, 40))))
                .containsExactly("large", "small");
        }

        @Test
        void comparatorBreaksASizeTieDownTheBindingVocabularysOwnChain() {

            // Each pair is level on the size, so which bloc leads names the number the bound chain
            // reaches next - and the two bindings reach different ones, which is what the chain being
            // per-vocabulary buys.
            assertThat(listIdsSortedBy(
                    DOMINANCE_BINDING,
                    buildDominanceBloc("lower", "A", new DominanceStats(2, 0, 0, 7)),
                    buildDominanceBloc("higher", "B", new DominanceStats(6, 0, 0, 7))))
                .containsExactly("higher", "lower");

            assertThat(listIdsSortedBy(
                    CLAIMS_BINDING,
                    buildClaimBloc("lower", "A", new ClaimStats(1, 7)),
                    buildClaimBloc("higher", "B", new ClaimStats(5, 7))))
                .containsExactly("higher", "lower");
        }
    }

    // Sorts the blocs by the mode's comparator in its own default direction and returns their ids in
    // the resulting order, so an assertion reads the arrangement without the blocs' other fields
    // getting in the way. The flipped direction belongs to the shared assembly and is pinned where
    // that lives.
    @SafeVarargs
    private static <S extends SizedBlocMetrics> List<String> listIdsSortedBy(
            BlocMarketSizeSortMode<S> mode,
            RankedBloc<S>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(mode.comparator(mode.defaultDirection()));

        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.itemId());
        }
        return ids;
    }

    private static RankedBloc<ClaimStats> buildClaimBloc(String id, String name, ClaimStats stats) {
        return new RankedBloc<>(new SelectableBloc(id, name, null), stats);
    }

    private static RankedBloc<DominanceStats> buildDominanceBloc(
            String id,
            String name,
            DominanceStats stats) {
        return new RankedBloc<>(new SelectableBloc(id, name, null), stats);
    }
}
