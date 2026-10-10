package kmu.maplayers.ownermap.owners.holders.base.picker;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPaintedViewFake;
import kmu.maplayers.ownermap.picker.HazardRatingFake;
import kmu.maplayers.ownermap.picker.OwnerMetrics;
import kmu.maplayers.ownermap.picker.OwnerMetricsFake;
import kmu.maplayers.ownermap.picker.OwnerPresenceIndex;
import kmu.maplayers.ownermap.picker.OwnerSortModesFake;
import kmu.maplayers.ownermap.picker.OwnerStatsReadFake;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.ROW_COLOUR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the picker read every holder view shares: which walked blocs survive the view's own gate, how a
 * surviving one is turned into a row, and what rides beside those rows - the stats-walk order, the
 * crest and name off the view's own reading over the walk's grouping, a row surviving a crest that
 * will not resolve, the caller's vocabulary composed with the shared standing mode, the whole walk's
 * presence being carried through, and the metrics riding through untouched whatever their type.
 */
final class BlocPickerAssemblyTests {

    private static final OwnerMetricsFake ANY_STATS_FAKE = new OwnerMetricsFake(3, 2, 5000, 7);
    private static final OwnerMetricsFake OTHER_STATS_FAKE = new OwnerMetricsFake(1, 1, 400, 2);

    // The shades two relations resolve to, distinct so an end drawn from the wrong member shows as a
    // colour rather than only as a number.
    private static final Color GREEN = new Color(60, 180, 60);
    private static final Color RED = new Color(200, 50, 50);

    // A group of two, which is the smallest grouping in which a bloc's membership is anything
    // other than the bloc's own ID - so a standing read against it can only come from the grouping
    // the read was handed.
    private static final HolderGrouping PACT_GROUPING = new HolderGrouping(
        Map.of("hegemony", "pact", "tritachyon", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    // The vocabulary a case is not about. The assembly bundles whatever it is handed, so a case
    // reading only the rows or the presence pairs them with a set that ranks nothing.
    private static final ListSortModes<RankedOwner<OwnerMetricsFake>> ANY_MODES =
        new ListSortModes<>(List.of(), null);

    @Nested
    class BuildBlocPickerRead {

        @Test
        void dropsEveryBlocTheViewsGateRejects() {
            // The gate is the one thing a view varies, so a false test drops the bloc outright rather
            // than listing it un-spotlightable.
            var sectorMock = mock(SectorAPI.class);
            var statsByBlocId = new LinkedHashMap<String, OwnerMetricsFake>();

            var viewFake = HolderPaintedViewFake.createGatedFake(
                Map.of("kept", "Kept", "dropped", "Dropped"),
                blocId -> blocId.equals("kept"));

            statsByBlocId.put("kept", ANY_STATS_FAKE);
            statsByBlocId.put("dropped", OTHER_STATS_FAKE);

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        sectorMock,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(statsByBlocId),
                        ANY_MODES)
                    .picker()
                    .items())
                .containsExactly(new RankedOwner<>(
                    new SelectableOwner("kept", "Kept", null),
                    ANY_STATS_FAKE));
        }

        @Test
        void offersEveryWalkedBlocUnderTheDefaultGate() {
            // The base case a view inherits when its walk surfaces only blocs it paints, so such a
            // view declares no gate at all rather than restating an always-true one.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new HolderPaintedViewFake(Map.of("kept", "Kept", "other", "Other"));
            var statsByBlocId = new LinkedHashMap<String, OwnerMetricsFake>();

            statsByBlocId.put("kept", ANY_STATS_FAKE);
            statsByBlocId.put("other", OTHER_STATS_FAKE);

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        sectorMock,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(statsByBlocId),
                        ANY_MODES)
                    .picker()
                    .items())
                .extracting(RankedOwner::itemId)
                .containsExactly("kept", "other");
        }

        @Test
        void keepsTheStatsWalkOrder() {
            // The picker sorts the list itself, but the un-sorted order is the economy walk's and is
            // carried through unchanged, so a caller reading it before a sort sees one stable order.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new HolderPaintedViewFake(Map.of("second", "Second", "first", "First"));
            var statsByBlocId = new LinkedHashMap<String, OwnerMetricsFake>();

            statsByBlocId.put("second", OTHER_STATS_FAKE);
            statsByBlocId.put("first", ANY_STATS_FAKE);

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        sectorMock,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(statsByBlocId),
                        ANY_MODES)
                    .picker()
                    .items())
                .extracting(RankedOwner::itemId)
                .containsExactly("second", "first");
        }

        @Test
        void takesEachRowsNameAndCrestFromTheViewsOwnReading() {
            // The row names and badges a bloc exactly as the map does, so both come off the view's
            // own reading, never a reading of the assembly's.
            var viewFake = HolderPaintedViewFake.createReadingFake(OwnerReadingFake
                .createNaming(Map.of("rebel_pact", "Rebel Pact"))
                .withLooks(Map.of(), Map.of("rebel_pact", "graphics/rebels_crest.png")));

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        mock(SectorAPI.class),
                        PACT_GROUPING,
                        OwnerStatsReadFake.createRowsOnlyFake(Map.of("rebel_pact", ANY_STATS_FAKE)),
                        ANY_MODES)
                    .picker()
                    .items())
                .containsExactly(new RankedOwner<>(
                    new SelectableOwner("rebel_pact", "Rebel Pact", "graphics/rebels_crest.png"),
                    ANY_STATS_FAKE));
        }

        @Test
        void readsTheViewsReadingOverTheHandedSectorAndGrouping() {
            // The reading names the blocs the walk folded, so it is built over the walk's own
            // grouping rather than one the view samples again.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new HolderPaintedViewFake(Map.of());

            BlocPickerAssembly.buildBlocPickerRead(
                viewFake,
                sectorMock,
                PACT_GROUPING,
                OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                ANY_MODES);

            assertThat(viewFake.listReadingSectors())
                .containsExactly(sectorMock);
            assertThat(viewFake.listReadingGroupings())
                .singleElement()
                .isSameAs(PACT_GROUPING);
        }

        @Test
        void keepsABlocWhoseCrestDoesNotResolve() {
            // A bloc with no authored crest (or no faction behind its colour ID) still paints
            // its cells, so it stays on offer and the row simply draws its name alone.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new HolderPaintedViewFake(Map.of("ghost", "Ghost"));

            when(sectorMock.getFaction("ghost"))
                .thenReturn(null);

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        sectorMock,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(Map.of("ghost", ANY_STATS_FAKE)),
                        ANY_MODES)
                    .picker()
                    .items())
                .containsExactly(new RankedOwner<>(
                    new SelectableOwner("ghost", "Ghost", null),
                    ANY_STATS_FAKE));
        }

        @Test
        void offersTheHandedVocabularyAheadOfTheStandingMode() {
            // The rows and the modes are bundled here rather than by each view, so a view states its
            // vocabulary once and cannot end up handing its rows on beside another layer's. The
            // standing mode joins it here for the opposite reason: it reads one fact off the sector
            // that is the same under every view, so neither vocabulary declares a copy of it.
            var viewFake = new HolderPaintedViewFake(Map.of());

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        mock(SectorAPI.class),
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                        OwnerSortModesFake.MODES)
                    .picker()
                    .sortModes()
                    .modes())
                .extracting(ListSortMode::persistenceKey)
                .containsExactly("name", "score", "player_standing");
        }

        @Test
        void keepsTheHandedVocabularysOwnFallbackMode() {
            // Appending a mode must not move what a fresh save opens on: the standing is a criterion
            // the player picks, while the fallback stays the number the layer is painted by.
            var viewFake = new HolderPaintedViewFake(Map.of());

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        mock(SectorAPI.class),
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                        OwnerSortModesFake.MODES)
                    .picker()
                    .sortModes()
                    .defaultMode())
                .isEqualTo(OwnerSortModesFake.DEFAULT);
        }

        @Test
        void composesAVocabularyAStoredStandingKeyResolvesAgainst() {
            // A stored key is resolved against the vocabulary the picker carries, so a mode appended
            // outside that set would leave a save that stored the standing ranking silently reopening
            // on the layer's default.
            var viewFake = new HolderPaintedViewFake(Map.of());
            var offeredModes = BlocPickerAssembly.buildBlocPickerRead(
                    viewFake,
                    mock(SectorAPI.class),
                    HolderGrouping.identity(),
                    OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                    OwnerSortModesFake.MODES)
                .picker()
                .sortModes();

            // Read back by its key, since a key the vocabulary did not hold falls back to the
            // default mode, whose key is another.
            assertThat(ListSort.resolveStored("player_standing", null, offeredModes).mode().persistenceKey())
                .isEqualTo("player_standing");
        }

        @Test
        void bindsTheStandingModeToTheHandedSectorAndGrouping() {
            // The appended mode is only right if it reads the very sector and grouping this read was
            // folded under - bound to anything else it would rank a bloc by a membership the map
            // never painted. A group whose two members disagree is what shows the binding: the
            // ends come back in their own members' shades, which a mode reading the bloc ID against
            // some other fold could not produce.
            // Both factions are built before either is handed over: stubbing a fresh mock inside an
            // open when(...) leaves Mockito holding an unfinished stubbing.
            var hostileMember = stubFactionAtStanding(RepLevel.HOSTILE, -40, RED);
            var friendlyMember = stubFactionAtStanding(RepLevel.FRIENDLY, 60, GREEN);
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(hostileMember);
            when(sectorMock.getFaction("tritachyon"))
                .thenReturn(friendlyMember);

            var viewFake = new HolderPaintedViewFake(Map.of("pact", "Persean Pact"));
            var read = BlocPickerAssembly.buildBlocPickerRead(
                viewFake,
                sectorMock,
                PACT_GROUPING,
                OwnerStatsReadFake.createRowsOnlyFake(Map.of("pact", ANY_STATS_FAKE)),
                OwnerSortModesFake.MODES);

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
        void offersNoStandingModeWithoutASectorToReadOneFrom() {
            // A read over no sector lists nothing and has no relations behind it, so the vocabulary
            // stands as its layer declared it rather than offering a ranking with nothing to rank by.
            var viewFake = new HolderPaintedViewFake(Map.of());

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        null,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                        OwnerSortModesFake.MODES)
                    .picker()
                    .sortModes())
                .isEqualTo(OwnerSortModesFake.MODES);
        }

        @Test
        void carriesTheWalksPresencePastTheGate() {
            // The presence is the whole walk's, so a bloc the gate dropped keeps its systems. Pinned
            // here rather than on a view because it is the assembly that decides: trimming the index
            // to the offered rows would cost a pass to remove entries no lookup can reach.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = HolderPaintedViewFake.createGatedFake(
                Map.of("kept", "Kept", "dropped", "Dropped"),
                blocId -> blocId.equals("kept"));

            var read = BlocPickerAssembly.buildBlocPickerRead(
                viewFake,
                sectorMock,
                HolderGrouping.identity(),
                new OwnerStatsReadFake<>(
                    Map.of("kept", ANY_STATS_FAKE, "dropped", OTHER_STATS_FAKE),
                    new OwnerPresenceIndex(Map.of(
                        "kept", Set.of(buildCellKey("corvus")),
                        "dropped", Set.of(buildCellKey("askonia"))))),
                ANY_MODES);

            assertThat(read.picker().items())
                .extracting(RankedOwner::itemId)
                .containsExactly("kept");

            assertThat(read.presenceIndex().readPresentSystemKeys("dropped"))
                .containsExactly(buildCellKey("askonia"));
        }

        @Test
        void carriesAPayloadNoViewDeclares() {
            // The assembly is what every layer's picker shares, so it must build an option over
            // metrics it has never heard of - a layer painted by some other mechanic ranks by its own
            // numbers. Run against a payload no view declares, sharing with the fake metrics
            // only the bound every option's payload satisfies: this stops compiling the moment the
            // assembly narrows back to one layer's numbers, which the fake-typed cases above
            // would not notice.
            var sectorMock = mock(SectorAPI.class);
            var viewFake = new HolderPaintedViewFake(Map.of("pirates", "Pirates"));
            var ratingFake = new HazardRatingFake(4, 1);

            assertThat(BlocPickerAssembly.buildBlocPickerRead(
                        viewFake,
                        sectorMock,
                        HolderGrouping.identity(),
                        OwnerStatsReadFake.createRowsOnlyFake(Map.of("pirates", ratingFake)),
                        new ListSortModes<RankedOwner<HazardRatingFake>>(List.of(), null))
                    .picker()
                    .items())
                .containsExactly(new RankedOwner<>(
                    new SelectableOwner("pirates", "Pirates", null),
                    ratingFake));
        }
    }

    // A faction the sector answers with, standing where the case wants it. Stubbed through the live
    // relationship object, which is the tier the standing read takes first, so the colour comes back
    // as this shade rather than out of the engine palette the test JVM cannot reach.
    private static FactionAPI stubFactionAtStanding(RepLevel level, int reputation, Color colour) {

        var relationshipMock = mock(RelationshipAPI.class);

        when(relationshipMock.getLevel())
            .thenReturn(level);
        when(relationshipMock.getRepInt())
            .thenReturn(reputation);
        when(relationshipMock.getRelColor())
            .thenReturn(colour);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getRelToPlayer())
            .thenReturn(relationshipMock);

        return factionMock;
    }

    // The appended mode picked out of the offered vocabulary by its key. By key rather than by
    // position, so a case about what the mode draws does not also fail when the selector's row order
    // changes - which the ordering case above is what pins.
    private static <S extends OwnerMetrics> ListSortMode<RankedOwner<S>> resolveStandingMode(
            ListSortModes<RankedOwner<S>> offeredModes) {

        for (var mode : offeredModes.modes()) {

            if ("player_standing".equals(mode.persistenceKey())) {
                return mode;
            }
        }

        throw new AssertionError("the offered vocabulary holds no standing mode");
    }
}
