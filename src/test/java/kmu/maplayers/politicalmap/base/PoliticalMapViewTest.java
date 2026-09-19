package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.BlocStatsReadFake;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.politicalmap.base.dominance.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the picker read every view shares: which walked blocs survive the view's own gate, how a
 * surviving one is turned into a row, and what rides beside those rows. The two concrete views pin
 * their own gates; this pins what the shared default owns whichever view calls it - the stats-walk
 * order, the crest read off the bloc's colour faction, a row surviving a crest that will not
 * resolve, the caller's vocabulary composed with the shared standing mode, the whole walk's
 * presence being carried through, and the metrics
 * riding through untouched whatever their type, which is what lets a layer ranked by other numbers
 * reuse the assembly.
 */
final class PoliticalMapViewTest {

    private static final DominanceStats ANY_STATS = new DominanceStats(3, 2, 5000, 7);

    private static final DominanceStats OTHER_STATS = new DominanceStats(1, 1, 400, 2);

    // The shades two relations resolve to, distinct so an end drawn from the wrong member shows as a
    // colour rather than only as a number.
    private static final Color GREEN = new Color(60, 180, 60);
    private static final Color RED = new Color(200, 50, 50);

    // An alliance of two, which is the smallest grouping in which a bloc's membership is anything
    // other than the bloc's own ID - so a standing read against it can only come from the grouping
    // the read was handed.
    private static final HolderGrouping PACT_GROUPING = new HolderGrouping(
        Map.of("hegemony", "pact", "tritachyon", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

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
            // A bloc with no authored crest (or no faction behind its colour ID) still paints
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
        void buildBlocPickerReadOffersTheHandedVocabularyAheadOfTheStandingMode() {
            // The rows and the modes are bundled here rather than by each view, so a view states its
            // vocabulary once and cannot end up handing its rows on beside another layer's. The
            // standing mode joins it here for the opposite reason: it reads one fact off the sector
            // that is the same under every view, so neither vocabulary declares a copy of it.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.buildBlocPickerRead(
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.<String, DominanceStats>of()),
                    DominanceSortModes.MODES)
                .picker().sortModes().modes())
                .extracting(ListSortMode::persistenceKey)
                .containsExactly("name", "domination", "presence", "score", "market_size",
                    "player_standing");
        }

        @Test
        void buildBlocPickerReadKeepsTheHandedVocabularysOwnFallbackMode() {
            // Appending a mode must not move what a fresh save opens on: the standing is a criterion
            // the player picks, while the fallback stays the number the layer is painted by.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.buildBlocPickerRead(
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.<String, DominanceStats>of()),
                    DominanceSortModes.MODES)
                .picker().sortModes().defaultMode())
                .isEqualTo(DominanceSortModes.DEFAULT);
        }

        @Test
        void buildBlocPickerReadComposesAVocabularyAStoredStandingKeyResolvesAgainst() {
            // A stored key is resolved against the vocabulary the picker carries, so a mode appended
            // outside that set would leave a save that stored the standing ranking silently reopening
            // on the layer's default.
            var viewFake = new PoliticalMapViewFake(Map.of());

            var offeredModes = viewFake.buildBlocPickerRead(
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.<String, DominanceStats>of()),
                    DominanceSortModes.MODES)
                .picker().sortModes();

            assertThat(ListSort.resolveStored("player_standing", null, offeredModes).mode())
                .isInstanceOf(BlocStandingSortMode.class);
        }

        @Test
        void buildBlocPickerReadBindsTheStandingModeToTheHandedSectorAndGrouping() {
            // The appended mode is only right if it reads the very sector and grouping this read was
            // folded under - bound to anything else it would rank a bloc by a membership the map
            // never painted. An alliance whose two members disagree is what shows the binding: the
            // ends come back in their own members' shades, which a mode reading the bloc ID against
            // some other fold could not produce.
            // Both factions are built before either is handed over: stubbing a fresh mock inside an
            // open when(...) leaves Mockito holding an unfinished stubbing.
            var hostileMember = stubFactionAtStanding(RepLevel.HOSTILE, -40, RED);
            var friendlyMember = stubFactionAtStanding(RepLevel.FRIENDLY, 60, GREEN);
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("hegemony")).thenReturn(hostileMember);
            when(sectorMock.getFaction("tritachyon")).thenReturn(friendlyMember);

            var viewFake = new PoliticalMapViewFake(Map.of("pact", "Persean Pact"));

            var read = viewFake.buildBlocPickerRead(
                sectorMock,
                PACT_GROUPING,
                BlocStatsReadFake.createRowsOnlyFake(Map.of("pact", ANY_STATS)),
                DominanceSortModes.MODES);

            var standingRuns = resolveStandingMode(read.picker().sortModes())
                .resolveTrailingRuns(read.picker().items().get(0), ROW_COLOUR);

            // The separator's text goes through the live settings, which the test JVM has none of, so
            // only the tone it takes and where it sits are read.
            assertThat(standingRuns)
                .extracting(TextSpan::colour)
                .containsExactly(RED, ROW_COLOUR, GREEN);

            assertThat(standingRuns)
                .extracting(TextSpan::text)
                .startsWith("-40")
                .endsWith("+60");
        }

        @Test
        void buildBlocPickerReadOffersNoStandingModeWithoutASectorToReadOneFrom() {
            // A read over no sector lists nothing and has no relations behind it, so the vocabulary
            // stands as its layer declared it rather than offering a ranking with nothing to rank by.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.buildBlocPickerRead(
                    null,
                    HolderGrouping.identity(),
                    BlocStatsReadFake.createRowsOnlyFake(Map.<String, DominanceStats>of()),
                    DominanceSortModes.MODES)
                .picker().sortModes())
                .isEqualTo(DominanceSortModes.MODES);
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
                        "kept", Set.of(buildCellKey("corvus")),
                        "dropped", Set.of(buildCellKey("askonia"))))),
                ANY_MODES);

            assertThat(read.picker().items())
                .extracting(RankedBloc::itemId)
                .containsExactly("kept");

            assertThat(read.presenceIndex().readPresentSystemKeys("dropped"))
                .containsExactly(buildCellKey("askonia"));
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
            var rating = new HazardRating(4, 1);

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

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), UNDER_THE_FOG).picker().items())
                .isEmpty();
        }

        @Test
        void resolveBlocPickerReadDefaultsToNoPresenceSoALookupAnswersRatherThanFaults() {
            // The other half of the default read. A view offering no rows can still be asked where a
            // bloc is - the ask reaches the seam before any row does - so the default has to answer
            // an empty set rather than leave a null for the lookup to fall over on.
            var viewFake = new PoliticalMapViewFake(Map.of());

            assertThat(viewFake.resolveBlocPickerRead(mock(SectorAPI.class), UNDER_THE_FOG)
                    .presenceIndex()
                    .readPresentSystemKeys("hegemony"))
                .isEmpty();
        }
    }

    // A faction the sector answers with, standing where the case wants it. Stubbed through the live
    // relationship object, which is the tier the standing read takes first, so the colour comes back
    // as this shade rather than out of the engine palette the test JVM cannot reach.
    private static FactionAPI stubFactionAtStanding(RepLevel level, int reputation, Color colour) {

        var relationshipMock = mock(RelationshipAPI.class);

        when(relationshipMock.getLevel()).thenReturn(level);
        when(relationshipMock.getRepInt()).thenReturn(reputation);
        when(relationshipMock.getRelColor()).thenReturn(colour);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getRelToPlayer()).thenReturn(relationshipMock);

        return factionMock;
    }

    // The appended mode picked out of the offered vocabulary by its key. By key rather than by
    // position, so a case about what the mode draws does not also fail when the selector's row order
    // changes - which the ordering case above is what pins.
    private static <S extends BlocMetrics> ListSortMode<RankedBloc<S>> resolveStandingMode(
            ListSortModes<RankedBloc<S>> offeredModes) {

        for (var mode : offeredModes.modes()) {
            if ("player_standing".equals(mode.persistenceKey())) {
                return mode;
            }
        }
        throw new AssertionError("the offered vocabulary holds no standing mode");
    }
}
