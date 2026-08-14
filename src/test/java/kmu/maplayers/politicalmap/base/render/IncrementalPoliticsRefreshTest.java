package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbon;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonSettingsFixtures;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritoryBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.StyledCellBuilder;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the fold {@link IncrementalPoliticsRefresh} performs when a colony resize marks
 * systems stale: which systems are re-derived, which cells that obliges to re-shape, and
 * which factions' territories that obliges to rebuild. The claim under test is the one the
 * incremental path exists to make - that it touches the systems whose holding actually
 * moved and their neighbours, and nothing else - since anything wider costs the frame it
 * was written to save, and anything narrower leaves the map drawing a stale holder.
 *
 * <p>A unit test, so the primitives the fold delegates to are mocked at their static seams:
 * the dominance resolve that answers who holds a system, the two builders that turn a
 * re-shaped cell and a faction's members into draw records, and the anchor and label
 * rebuilds that ride along. Each is pinned by its own suite; what belongs here is only the
 * decision about which of them to call and with what.
 *
 * <p>The same "only what moved" claim is pinned on the placements the fold carries along: a
 * frame that re-fits them hands the caller's own pair down for the fit to replace, and a frame
 * that leaves them alone calls no fit at all - so what the caller holds is only ever relabelled
 * when the placements it labels were themselves rebuilt.
 */
final class IncrementalPoliticsRefreshTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // The flipping system and the neighbour whose shared edge flips with it. Both seed a
    // cell, so both are re-shapeable; the third seeds none.
    private static final String FLIPPED_SYSTEM = "flipped";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String CELL_LESS_SYSTEM = "cellless";

    // The revision the caller's cells stand at. This path re-shapes cells but never recuts
    // them, so it fits against the geometry it was handed and reports that same revision back.
    private static final int GEOMETRY_REVISION = 7;

    // What the caller's standing placements were fitted under, standing in the pair so the
    // re-fit can carry over the clusters this fold did not move. Opaque here - the tuning
    // inside is the fit's own business and no case reads it.
    // What the stubbed planner reports for the marked system: one run, which is all the case
    // reads - that a band was baked at all, rather than what it says.
    private static final RibbonPlan BAND_OF_ONE_RUN =
        new RibbonPlan(List.of(new RibbonSegment(Color.WHITE, 1)));

    private static final AnchorFitFingerprint STANDING_FIT =
        new AnchorFitFingerprint(null, GEOMETRY_REVISION - 1);

    @Nested
    class ApplyStalePoliticsUpdates {

        // Closed in reverse on the way out, so a seam opened over another is never left
        // standing when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

        // The placements the caller holds across frames, standing at what a previous pass
        // fitted them under. Empty of placements because the re-fit is neutralised here and
        // would leave none: what a case reads off it is the label, which is the half this fold
        // can leave wrong.
        private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();

        // The cells the caller holds, paired with the revision they stand at. Built once per
        // case so what a re-fit is handed can be read back as the caller's own pair rather than
        // as a value that merely compares equal to it.
        private final RevisedCellGeometry cellGeometry =
            new RevisedCellGeometry(buildTwoAdjacentCells(), GEOMETRY_REVISION);

        private MockedStatic<Global> globalMock;
        private MockedStatic<SectorPolitics> politicsMock;
        private MockedStatic<StyledCellBuilder> styledCellsMock;
        private MockedStatic<FactionTerritoryBuilder> territoriesMock;
        private MockedStatic<ClusterAnchorsBuilder> anchorsMock;

        private SectorAPI sectorMock;

        @BeforeEach
        void openSeamsAndClearTheStaleSet() {

            globalMock = openSeam(Global.class);
            // The class logs through a static field initialised on first touch, which may
            // happen inside this block; without this the logger would come back null and
            // the debug lines below would fault before the assertion was reached.
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(Logger.getLogger(IncrementalPoliticsRefreshTest.class));

            // Built before the stubbing rather than inside it: each system is itself a mock,
            // and building one while another stubbing is open reads to Mockito as an
            // unfinished stub.
            var systems = List.of(buildSystem(FLIPPED_SYSTEM), buildSystem(NEIGHBOUR_SYSTEM));

            sectorMock = mock(SectorAPI.class);

            when(sectorMock.getStarSystems())
                .thenReturn(systems);

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            politicsMock = openSeam(SectorPolitics.class);

            styledCellsMock = openSeam(StyledCellBuilder.class);
            styledCellsMock
                .when(() -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    any(),
                    any()))
                .thenReturn(PoliticalMapTerritoryFixtures.createPlaceholderStyledCell());

            territoriesMock = openSeam(FactionTerritoryBuilder.class);
            territoriesMock
                .when(() -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    any(),
                    anyList()))
                .thenReturn(PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of()));

            // Both ride along after a flip and are pinned by their own suites; opening them
            // leaves each a no-op, so a case here asserts the fold and not their output. The
            // re-fit writes the pair it is handed, which is exactly what a neutralised seam does
            // not do - so a case reads whether the fold called it, not what it left behind.
            anchorsMock = openSeam(ClusterAnchorsBuilder.class);

            openSeam(LabelsBuilder.class);

            // Read as an argument to the label rebuild, so it evaluates even with that
            // rebuild neutralised - and it reads save-backed memory no test JVM has.
            openSeam(NameFormatPreference.class)
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.NONE);

            // The pair arrives labelled by a pass that ran before this frame, which is what
            // every case here folds into: a fold that overwrote the label without re-fitting
            // would read as agreeing with itself if the pair started blank.
            standingAnchors.replaceAnchors(List.of(), STANDING_FIT);

            // A re-bake reads the player's band sizes, which reach LunaLib - so the knobs answer
            // from a seam here, at the sizes the mod ships, since no case in this suite is about
            // what a band is sized at.
            RibbonSettingsFixtures.stubBandsOnAtShippedSizes(
                openSeam(KmuPoliticalMapSettings.class));

            // The stale set is static and shared, so a residue from another suite would
            // read here as a system this one never marked.
            MapLayerRefresh.drainStaleGroupingSystemIds();
        }

        @AfterEach
        void closeSeams() {
            for (var index = openStaticSeams.size() - 1; index >= 0; index--) {
                openStaticSeams.get(index).close();
            }
            openStaticSeams.clear();
        }

        @Test
        void applyStalePoliticsUpdatesReadsNoSectorWhenNothingIsStale() {
            // The per-frame path: this runs every frame, and on almost all of them the
            // stale set is empty, so it must cost nothing before it returns.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            applyTo(territories);

            globalMock.verify(
                Global::getSector,
                never());
            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesSkipsAStaleSystemThatSeedsNoCell() {
            // A resize changes holding over cells already drawn; it never admits a
            // system to the map, so one with no cell has nothing to re-shape and must not
            // reach the re-derive at all.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(CELL_LESS_SYSTEM);
            applyTo(territories);

            politicsMock.verifyNoInteractions();

            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesReshapesNothingWhenTheHolderDidNotChange() {
            // The common resize: a colony grows, its faction still wins, and every fill and
            // border is identical - so no cell re-shapes and no territory rebuilds. The marked
            // system's band is re-baked all the same, which the case below states; here the cell
            // carries none, so the re-bake finds nothing to write.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getHolderBySystemId())
                .containsExactly(buildEntryOwnedBy(FLIPPED_SYSTEM, HEGEMONY));

            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRebakesTheBandOfAMarkedSystemThatDidNotFlip() {
            // What marks a system is a colony appearing, growing, or changing hands - which is
            // exactly what changes how many colonies a band counts. So a marked system owes a
            // re-baked band even on the frame where nothing about its fill moved, and waiting for
            // a flip would leave the band reporting a colony that is no longer there.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            territories.putStyledCell(
                FLIPPED_SYSTEM,
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                buildBandSizedCell(),
                CellRibbon.NONE);

            // The band starts above the cell's own site, so the marked system needs one; the
            // shared geometry fixture records none, every other case being about shapes.
            when(cellGeometry.cells().getSiteBySystemId())
                .thenReturn(Map.of(FLIPPED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(territories.getView().resolveRibbonPlanner(any(), any(), any()))
                .thenReturn(system -> BAND_OF_ONE_RUN);

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getRibbonByCellId())
                .containsOnlyKeys(FLIPPED_SYSTEM);

            // Nothing else moved: a re-bake is not a re-shape.
            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRecordsTheNewHolderWhenASystemChangesHands() {

            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getHolderBySystemId().get(FLIPPED_SYSTEM).factionId())
                .isEqualTo(TRITACHYON);
        }

        @Test
        void applyStalePoliticsUpdatesDropsTheHolderOfASystemThatLostItsLastColony() {
            // Decivilised or bombed out: the system stays drawn but holds no holder, so the
            // entry goes rather than being left pointing at the faction that lost it.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, null);

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getHolderBySystemId())
                .doesNotContainKey(FLIPPED_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesReshapesTheFlippedSystemAndItsNeighbour() {
            // The neighbour's own holder did not move, but the edge it shares with the
            // flipped system just turned from a same-faction seam into a national border,
            // so it has to be re-shaped too or the border draws down one side only.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(FLIPPED_SYSTEM),
                    any()));

            styledCellsMock.verify(
                () -> StyledCellBuilder.buildStyledCellForSystem(
                    any(),
                    eq(NEIGHBOUR_SYSTEM),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesRebuildsTheLosingAndGainingFactionsTerritories() {
            // Both sides of the transfer change shape - one loses the cell, the other gains
            // it - and no third faction's rings trace a cell that moved, so exactly these
            // two rebuild.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            territoriesMock.verify(
                () -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    eq(HEGEMONY),
                    anyList()));
            territoriesMock.verify(
                () -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(),
                    any(),
                    eq(TRITACHYON),
                    anyList()));
        }

        @Test
        void applyStalePoliticsUpdatesRefitsAgainstTheCallersGeometry() {
            // The caller's own cells-and-revision pair goes out as it came in, because this path
            // re-shapes cells within a partition it never recut - so the fit ran against the very
            // geometry the caller named, and a re-fit reported against any other one would offer
            // its placements to a later rebuild standing somewhere else.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verify(
                () -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    any(),
                    same(cellGeometry),
                    any(),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesRefitsIntoTheCallersOwnStandingPair() {
            // The re-fit here is partial for the same reason a full rebuild's is: a flip
            // re-partitions the clusters it touches and leaves the rest alone, so what the
            // standing placements were fitted under has to reach the fit or every cluster is
            // searched again. That record and the placements are one value, and it is the
            // caller's own - handing a copy down would carry over correctly and still leave
            // the caller holding placements labelled by the pass before this one.
            var territories = buildOwnedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verify(
                () -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    same(standingAnchors),
                    any(),
                    any(),
                    any()));
        }

        @Test
        void applyStalePoliticsUpdatesLeavesTheStandingPairAloneWhenNothingIsStale() {
            // Nothing was re-fitted, so the standing placements and the rules recorded for them
            // still describe each other and neither half may move.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            applyTo(territories);

            anchorsMock.verifyNoInteractions();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(STANDING_FIT);
        }

        @Test
        void applyStalePoliticsUpdatesLeavesTheStandingPairAloneWhenTheHolderDidNotChange() {
            // The same claim on the other early return: a resize that leaves the winner alone
            // leaves the placements alone, so there is nothing about them to restate.
            var territories = buildOwnedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertResolvesTo(FLIPPED_SYSTEM, readOwnerOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            anchorsMock.verifyNoInteractions();
            assertThat(standingAnchors.getFitFingerprint())
                .isEqualTo(STANDING_FIT);
        }

        // Opens a static seam and registers it for closing, so a case names what it needs
        // rather than repeating the open-and-remember pair for each.
        private <T> MockedStatic<T> openSeam(Class<T> seamType) {
            MockedStatic<T> staticMock = mockStatic(seamType);
            openStaticSeams.add(staticMock);
            return staticMock;
        }

        // What the re-derive answers for one system this pass. Every case stubs the systems
        // it marks; an unstubbed one comes back null, which reads as a system that lost its
        // holder rather than as a missing stub.
        private void assertResolvesTo(String systemId, DominantHolder holder) {
            // The grouping matcher is typed because a sibling entry point takes a resolved
            // dominance pass in the same slot, and a bare any() would name neither.
            politicsMock
                .when(() -> SectorPolitics.resolveDominantHolder(
                    any(),
                    matchSystemArg(systemId),
                    any(HolderGrouping.class)))
                .thenReturn(holder);
        }

        // Runs the refresh over the two-cell geometry every case shares, handing it the pair
        // the caller holds across frames and an empty stand-in for the label list the plugin
        // owns beside it.
        private void applyTo(PoliticalMapTerritories territories) {
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                territories,
                standingAnchors,
                new ArrayList<Label>(),
                cellGeometry);
        }
    }

    // A cell large enough to hold the authored band clear of its own border, so a re-bake that
    // ran comes back with runs rather than with the empty band a collapsed inset would give.
    private static List<double[]> buildBandSizedCell() {
        return List.of(
            new double[] {0.0, 0.0},
            new double[] {4000.0, 0.0},
            new double[] {4000.0, 4000.0},
            new double[] {0.0, 4000.0});
    }

    // A geometry cache holding two adjacent square cells, each drawing as its own star: the
    // shape the fold reads to find a flipped system's neighbours. Mocked rather than built
    // from a sector because only its two lookups are read here, and a real partition would
    // make each case depend on the Voronoi build as well as on the fold.
    private static CellGeometryCache buildTwoAdjacentCells() {

        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(Map.of(
                FLIPPED_SYSTEM,
                buildSquareCellFacing(NEIGHBOUR_SYSTEM, 0),
                NEIGHBOUR_SYSTEM,
                buildSquareCellFacing(FLIPPED_SYSTEM, 100)));

        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(Map.of(
                FLIPPED_SYSTEM,
                FLIPPED_SYSTEM,
                NEIGHBOUR_SYSTEM,
                NEIGHBOUR_SYSTEM));

        return geometryCacheMock;
    }

    // A closed four-edge cell offset along x, one edge of which faces the given neighbour
    // while the rest face open space. Real coordinates because the re-shape insets these
    // edges for real; only the adjacency tag is what the fold itself reads.
    private static List<CellEdge> buildSquareCellFacing(String neighbourSystemId, double offsetX) {

        var frontier = new EdgeTarget.NoSystem("frontier");

        return List.of(
            new CellEdge(
                offsetX,
                0,
                offsetX + 50,
                0,
                new EdgeTarget.AcrossSystem(neighbourSystemId)),
            new CellEdge(offsetX + 50, 0, offsetX + 50, 50, frontier),
            new CellEdge(offsetX + 50, 50, offsetX, 50, frontier),
            new CellEdge(offsetX, 50, offsetX, 0, frontier));
    }

    private static PoliticalMapTerritories buildOwnedBy(Map<String, String> factionIdBySystemId) {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var entry : factionIdBySystemId.entrySet()) {
            ownerBySystemId.put(entry.getKey(), readOwnerOf(entry.getValue()));
        }
        return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(ownerBySystemId);
    }

    // The fold compares whole holders, so the shades matter only in that two holders of the
    // same faction must compare equal; one shared placeholder pair gives that.
    private static DominantHolder readOwnerOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }

    private static Map.Entry<String, DominantHolder> buildEntryOwnedBy(
            String systemId,
            String factionId) {
        return Map.entry(systemId, readOwnerOf(factionId));
    }

    private static StarSystemAPI buildSystem(String systemId) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        return systemMock;
    }

    // Matches the star system carrying the given id, so a stub names the system it answers
    // for rather than the mock instance the fixture happened to build.
    private static StarSystemAPI matchSystemArg(String systemId) {
        return argThat(system -> system != null && systemId.equals(system.getId()));
    }
}
