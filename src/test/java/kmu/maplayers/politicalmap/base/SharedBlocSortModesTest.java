package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.politicalmap.base.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.buildBloc;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.listIdsInModeOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one declaration both painted vocabularies ask for rather than write: the frozen key a save
 * round-trips through, the size read off whichever metrics record carries it, the direction that
 * follows, and that a tie on the size falls through to the chain the asking vocabulary supplied rather
 * than to one spelled here. Exercised over both records, since a mode declared once has to answer
 * identically under each.
 *
 * <p>What a declared mode is at all is {@link BlocMetricSortMode}'s and the shape of the ranking is
 * the shared assembly's; both are pinned in their own suites. All on hand-built blocs, since neither
 * record carries a Starsector type.
 */
final class SharedBlocSortModesTest {

    // The two vocabularies' chains, spelled the way each spells its own: domination leads the held
    // views', claims leads the claims view's.
    private static final List<ToIntFunction<DominanceStats>> DOMINANCE_CHAIN =
        List.of(
            DominanceStats::domination,
            DominanceStats::presence,
            DominanceStats::score,
            SizedBlocMetrics::marketSize);

    private static final List<ToIntFunction<ClaimStats>> CLAIMS_CHAIN =
        List.of(
            ClaimStats::claims,
            SizedBlocMetrics::marketSize);

    // Two bindings of the one declaration, each over the chain its own vocabulary declares. Naming both
    // is what lets a tie on the size show which chain the binding actually fell through to.
    private static final ListSortMode<RankedBloc<DominanceStats>> DOMINANCE_BINDING =
        SharedBlocSortModes.declareMarketSizeMode(DOMINANCE_CHAIN);

    private static final ListSortMode<RankedBloc<ClaimStats>> CLAIMS_BINDING =
        SharedBlocSortModes.declareMarketSizeMode(CLAIMS_CHAIN);

    @Nested
    class DeclareMarketSizeMode {

        @Test
        void declareMarketSizeModeCarriesTheFrozenKeyUnderEveryVocabulary() {

            // Pinned as a literal, and as one literal: both vocabularies stored this spelling while
            // each declared the mode separately, so a change here resets every save that stored the
            // size ranking under either view.
            assertThat(DOMINANCE_BINDING.persistenceKey())
                .isEqualTo("market_size");
            assertThat(CLAIMS_BINDING.persistenceKey())
                .isEqualTo("market_size");
        }

        @Test
        void declareMarketSizeModeDrawsTheWholeSectorSizeAsOneRowColouredRun() {

            // The same reading off either record: one run, in the tone the picker offered, since a
            // colony size carries no colour of its own.
            assertThat(DOMINANCE_BINDING.resolveTrailingRuns(
                    buildBloc("hegemony", "Hegemony", new DominanceStats(5, 8, 40, 12)),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("12", ROW_COLOUR));

            assertThat(CLAIMS_BINDING.resolveTrailingRuns(
                    buildBloc("hegemony", "Hegemony", new ClaimStats(4, 26)),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("26", ROW_COLOUR));
        }

        @Test
        void declareMarketSizeModeRunsHighToLowUnderEveryVocabulary() {

            // A number leads with the bigger bloc, so the size runs high-to-low wherever it is offered.
            assertThat(DOMINANCE_BINDING.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(CLAIMS_BINDING.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void declareMarketSizeModeRanksTheBiggerBlocFirst() {

            assertThat(listIdsInModeOrder(
                    DOMINANCE_BINDING,
                    buildBloc("small", "Small", new DominanceStats(0, 0, 0, 3)),
                    buildBloc("large", "Large", new DominanceStats(0, 0, 0, 40))))
                .containsExactly("large", "small");

            assertThat(listIdsInModeOrder(
                    CLAIMS_BINDING,
                    buildBloc("small", "Small", new ClaimStats(0, 3)),
                    buildBloc("large", "Large", new ClaimStats(0, 40))))
                .containsExactly("large", "small");
        }

        @Test
        void declareMarketSizeModeBreaksASizeTieDownTheAskingVocabularysOwnChain() {

            // Each pair is level on the size, so which bloc leads names the number the supplied chain
            // reaches next - and the two bindings reach different ones, which is what asking for the
            // mode rather than being handed a finished one buys.
            assertThat(listIdsInModeOrder(
                    DOMINANCE_BINDING,
                    buildBloc("lower", "A", new DominanceStats(2, 0, 0, 7)),
                    buildBloc("higher", "B", new DominanceStats(6, 0, 0, 7))))
                .containsExactly("higher", "lower");

            assertThat(listIdsInModeOrder(
                    CLAIMS_BINDING,
                    buildBloc("lower", "A", new ClaimStats(1, 7)),
                    buildBloc("higher", "B", new ClaimStats(5, 7))))
                .containsExactly("higher", "lower");
        }
    }

}
