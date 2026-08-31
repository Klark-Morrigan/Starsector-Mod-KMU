package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.BlocStatsReadFake;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the picker read every view shares: which walked blocs survive the view's own gate, how a
 * surviving one is turned into a row, and what rides beside those rows. The two concrete views pin
 * their own gates; this pins what the shared default owns whichever view calls it - the stats-walk
 * order, the crest read off the bloc's colour faction, a row surviving a crest that will not
 * resolve, the vocabulary and the whole walk's presence being carried through, and the metrics
 * riding through untouched whatever their type, which is what lets a layer ranked by other numbers
 * reuse the assembly.
 */
final class PoliticalMapViewTest {

    private static final DominanceStats ANY_STATS = new DominanceStats(3, 2, 5000, 7);

    private static final DominanceStats OTHER_STATS = new DominanceStats(1, 1, 400, 2);

    // The vocabulary a case is not about. The assembly bundles whatever it is handed, so a case
    // reading only the rows or the presence pairs them with a set that ranks nothing.
    private static final ListSortModes<RankedBloc<DominanceStats>> ANY_MODES =
        new ListSortModes<>(List.of(), null);

    @Nested
    class BuildBlocPickerRead {

        @Test
        void buildBlocPickerReadDropsEveryBlocTheViewsGateRejects() {
            // The gate is the one thing a view varies, so a false test drops the bloc outright rather
            // than listing it un-spotlightable.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = PoliticalMapViewFake.createGatedFake(
                Map.of("kept", "Kept", "dropped", "Dropped"),
                blocId -> blocId.equals("kept"));
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            statsByBlocId.put("kept", ANY_STATS);
            statsByBlocId.put("dropped", OTHER_STATS);

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(statsByBlocId),
                    ANY_MODES)
                .picker().items())
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("kept", "Kept", null),
                    ANY_STATS));
        }

        @Test
        void buildBlocPickerReadOffersEveryWalkedBlocUnderTheDefaultGate() {
            // The base case a view inherits when its walk surfaces only blocs it paints, so such a
            // view declares no gate at all rather than restating an always-true one.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("kept", "Kept", "other", "Other"));
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            statsByBlocId.put("kept", ANY_STATS);
            statsByBlocId.put("other", OTHER_STATS);

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(statsByBlocId),
                    ANY_MODES)
                .picker().items())
                .extracting(RankedBloc::itemId)
                .containsExactly("kept", "other");
        }

        @Test
        void buildBlocPickerReadKeepsTheStatsWalkOrder() {
            // The picker sorts the list itself, but the un-sorted order is the economy walk's and is
            // carried through unchanged, so a caller reading it before a sort sees one stable order.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("second", "Second", "first", "First"));
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            statsByBlocId.put("second", OTHER_STATS);
            statsByBlocId.put("first", ANY_STATS);

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(statsByBlocId),
                    ANY_MODES)
                .picker().items())
                .extracting(RankedBloc::itemId)
                .containsExactly("second", "first");
        }

        @Test
        void buildBlocPickerReadCrestsABlocFromItsColourFaction() {
            // An alliance paints in its lead member's palette, so the row draws that member's crest.
            // Identity grouping returns the bloc itself, which is why one lookup serves both views.
            var sectorMock = mock(SectorAPI.class);
            var leadFactionMock = mock(FactionAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("rebel_pact", "Rebel Pact"));

            when(sectorMock.getFaction("rebels"))
                .thenReturn(leadFactionMock);
            when(leadFactionMock.getCrest())
                .thenReturn("graphics/rebels_crest.png");

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    new HolderGrouping(
                        Map.of("rebels", "rebel_pact"),
                        Map.of("rebel_pact", "rebels"),
                        Map.of("rebel_pact", "Rebel Pact")),
                    BlocStatsReadFake.createRowsOnlyFake(Map.of("rebel_pact", ANY_STATS)),
                    ANY_MODES)
                .picker().items())
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("rebel_pact", "Rebel Pact", "graphics/rebels_crest.png"),
                    ANY_STATS));
        }

        @Test
        void buildBlocPickerReadKeepsABlocWhoseCrestDoesNotResolve() {
            // A bloc with no authored crest (or no faction behind its colour id) still paints
            // territory, so it stays on offer and the row simply draws its name alone.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("ghost", "Ghost"));

            when(sectorMock.getFaction("ghost"))
                .thenReturn(null);

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.of("ghost", ANY_STATS)),
                    ANY_MODES)
                .picker().items())
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("ghost", "Ghost", null),
                    ANY_STATS));
        }

        @Test
        void buildBlocPickerReadBundlesTheVocabularyItWasHanded() {
            // The rows and the modes are bundled here rather than by each view, so a view states its
            // vocabulary once and cannot end up handing its rows on beside another layer's.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.buildBlocPickerRead(
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.<String, DominanceStats>of()),
                    DominanceSortMode.MODES)
                .picker().sortModes())
                .isEqualTo(DominanceSortMode.MODES);
        }

        @Test
        void buildBlocPickerReadCarriesTheWalksPresencePastTheGate() {
            // The presence is the whole walk's, so a bloc the gate dropped keeps its systems. Pinned
            // here rather than on a view because it is the assembly that decides: trimming the index
            // to the offered rows would cost a pass to remove entries no lookup can reach.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = PoliticalMapViewFake.createGatedFake(
                Map.of("kept", "Kept", "dropped", "Dropped"),
                blocId -> blocId.equals("kept"));

            var read = viewFake.buildBlocPickerRead(
                sectorMock,
                HolderGrouping.identity(),
                new BlocStatsReadFake<>(
                    Map.of("kept", ANY_STATS, "dropped", OTHER_STATS),
                    new BlocPresenceIndex(Map.of(
                        "kept", Set.of("corvus"),
                        "dropped", Set.of("askonia")))),
                ANY_MODES);

            assertThat(read.picker().items())
                .extracting(RankedBloc::itemId)
                .containsExactly("kept");

            assertThat(read.presenceIndex().readPresentSystemIds("dropped"))
                .containsExactly("askonia");
        }

        @Test
        void buildBlocPickerReadCarriesAPayloadFromOutsideTheDominanceMetrics() {
            // The assembly is what every layer's picker shares, so it must build an option over
            // metrics it has never heard of - a layer painted by some other mechanic ranks by its own
            // numbers. Run against a payload no view declares, sharing with the dominance metrics
            // only the bound every option's payload satisfies: this stops compiling the moment the
            // assembly narrows back to one layer's numbers, which the dominance-typed cases above
            // would not notice.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("pirates", "Pirates"));
            var rating = new HazardRating(4);

            assertThat(viewFake.buildBlocPickerRead(
                    sectorMock,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.of("pirates", rating)),
                    new ListSortModes<RankedBloc<HazardRating>>(List.of(), null))
                .picker().items())
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("pirates", "Pirates", null),
                    rating));
        }
    }

    @Nested
    class ResolveBlocPickerRead {

        @Test
        void resolveBlocPickerReadDefaultsToTheEmptyPickerSoASpotlightIsOptedInto() {
            // The base case is "no spotlight", so a view with nothing to list inherits a whole
            // answer rather than overriding with two arguments it would ignore. Pinned on the
            // fake rather than on the one view that currently relies on it, since what is under
            // test is the seam's default and not that view's choice to keep it.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), BASE_FOG).picker().items())
                .isEmpty();
        }

        @Test
        void resolveBlocPickerReadDefaultsToNoPresenceSoALookupAnswersRatherThanFaults() {
            // The other half of the default read. A view offering no rows can still be asked where a
            // bloc is - the ask reaches the seam before any row does - so the default has to answer
            // an empty set rather than leave a null for the lookup to fall over on.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), BASE_FOG)
                    .presenceIndex()
                    .readPresentSystemIds("hegemony"))
                .isEmpty();
        }
    }
}
