package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the option assembly every view shares: which present blocs survive the view's own gate, and
 * how a surviving one is turned into a picker option. The two concrete views pin their own gates;
 * this pins what the shared default owns whichever view calls it - the stats-walk order, the crest
 * read off the bloc's colour faction, an option surviving a crest that will not resolve, and the
 * metrics riding through untouched whatever their type, which is what lets a layer ranked by other
 * numbers reuse the assembly.
 */
final class PoliticalMapViewTest {

    private static final DominanceStats ANY_STATS = new DominanceStats(3, 2, 5000, 7);

    private static final DominanceStats OTHER_STATS = new DominanceStats(1, 1, 400, 2);

    @Nested
    class BuildSelectableBlocs {

        @Test
        void buildSelectableBlocsDropsEveryBlocTheViewsGateRejects() {
            // The gate is the one thing a view varies, so a false test drops the bloc outright rather
            // than listing it un-spotlightable.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("kept", "Kept", "dropped", "Dropped"));
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            statsByBlocId.put("kept", ANY_STATS);
            statsByBlocId.put("dropped", OTHER_STATS);

            assertThat(viewFake.buildSelectableBlocs(
                    sectorMock,
                    HolderGrouping.identity(),
                    statsByBlocId,
                    blocId -> blocId.equals("kept")))
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("kept", "Kept", null),
                    ANY_STATS));
        }

        @Test
        void buildSelectableBlocsKeepsTheStatsWalkOrder() {
            // The picker sorts the list itself, but the un-sorted order is the economy walk's and is
            // carried through unchanged, so a caller reading it before a sort sees one stable order.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("second", "Second", "first", "First"));
            var statsByBlocId = new LinkedHashMap<String, DominanceStats>();

            statsByBlocId.put("second", OTHER_STATS);
            statsByBlocId.put("first", ANY_STATS);

            assertThat(viewFake.buildSelectableBlocs(
                    sectorMock,
                    HolderGrouping.identity(),
                    statsByBlocId,
                    blocId -> true))
                .extracting(RankedBloc::itemId)
                .containsExactly("second", "first");
        }

        @Test
        void buildSelectableBlocsCrestsABlocFromItsColourFaction() {
            // An alliance paints in its lead member's palette, so the row draws that member's crest.
            // Identity grouping returns the bloc itself, which is why one lookup serves both views.
            var sectorMock = mock(SectorAPI.class);
            var leadFactionMock = mock(FactionAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("rebel_pact", "Rebel Pact"));

            when(sectorMock.getFaction("rebels"))
                .thenReturn(leadFactionMock);
            when(leadFactionMock.getCrest())
                .thenReturn("graphics/rebels_crest.png");

            assertThat(viewFake.buildSelectableBlocs(
                    sectorMock,
                    new HolderGrouping(
                        Map.of("rebels", "rebel_pact"),
                        Map.of("rebel_pact", "rebels"),
                        Map.of("rebel_pact", "Rebel Pact")),
                    Map.of("rebel_pact", ANY_STATS),
                    blocId -> true))
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("rebel_pact", "Rebel Pact", "graphics/rebels_crest.png"),
                    ANY_STATS));
        }

        @Test
        void buildSelectableBlocsKeepsABlocWhoseCrestDoesNotResolve() {
            // A bloc with no authored crest (or no faction behind its colour id) still paints
            // territory, so it stays on offer and the row simply draws its name alone.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("ghost", "Ghost"));

            when(sectorMock.getFaction("ghost"))
                .thenReturn(null);

            assertThat(viewFake.buildSelectableBlocs(
                    sectorMock,
                    HolderGrouping.identity(),
                    Map.of("ghost", ANY_STATS),
                    blocId -> true))
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("ghost", "Ghost", null),
                    ANY_STATS));
        }

        @Test
        void buildSelectableBlocsCarriesAPayloadFromOutsideTheDominanceMetrics() {
            // The assembly is what every layer's picker shares, so it must build an option over
            // metrics it has never heard of - a layer painted by some other mechanic ranks by its own
            // numbers. Run against a payload no view declares, sharing with the dominance metrics
            // only the bound every option's payload satisfies: this stops compiling the moment the
            // assembly narrows back to one layer's numbers, which the dominance-typed cases above
            // would not notice.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new PoliticalMapViewFake(Map.of("pirates", "Pirates"));
            var rating = new HazardRating(4);

            assertThat(viewFake.buildSelectableBlocs(
                    sectorMock,
                    HolderGrouping.identity(),
                    Map.of("pirates", rating),
                    blocId -> true))
                .containsExactly(new RankedBloc<>(
                    new SelectableBloc("pirates", "Pirates", null),
                    rating));
        }
    }

    @Nested
    class ResolveBlocPicker {

        @Test
        void resolveBlocPickerDefaultsToTheEmptyPickerSoASpotlightIsOptedInto() {
            // The base case is "no spotlight", so a view with nothing to list inherits a whole
            // answer rather than overriding with two arguments it would ignore. Pinned on the
            // fake rather than on the one view that currently relies on it, since what is under
            // test is the seam's default and not that view's choice to keep it.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.resolveBlocPicker(mock(SectorAPI.class), BASE_FOG).items())
                .isEmpty();
        }
    }
}
