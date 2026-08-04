package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritoryBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.StyledCellBuilder;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
 * <p>The same "only what moved" claim is pinned on what the fold answers: a frame that
 * re-fitted the placements reports what they were fitted under, and a frame that left them
 * alone reports nothing, so the caller's record of its standing list is only ever replaced
 * when the list itself was.
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

    // What the re-fit answers when it runs. Opaque here - the tuning inside is the fit's own
    // business and no case reads it; what is pinned is that the caller is handed back exactly
    // what the fit reported, rather than something this fold assembled for itself.
    private static final AnchorFitFingerprint REFITTED_UNDER =
        new AnchorFitFingerprint(null, GEOMETRY_REVISION);

    @Nested
    class ApplyStalePoliticsUpdates {

        // Closed in reverse on the way out, so a seam opened over another is never left
        // standing when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();

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
            var systems = List.of(system(FLIPPED_SYSTEM), system(NEIGHBOUR_SYSTEM));

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
            // re-fit still has to answer something, since what it reports is what this fold
            // hands back to the caller.
            anchorsMock = openSeam(ClusterAnchorsBuilder.class);
            anchorsMock
                .when(() -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    anyList(),
                    any(),
                    any(),
                    any(),
                    anyInt()))
                .thenReturn(REFITTED_UNDER);

            openSeam(LabelsBuilder.class);

            // Read as an argument to the label rebuild, so it evaluates even with that
            // rebuild neutralised - and it reads save-backed memory no test JVM has.
            openSeam(NameFormatPreference.class)
                .when(NameFormatPreference::getSelectedNameFormat)
                .thenReturn(FactionNameFormatChoice.NONE);

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
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            applyTo(territories);

            globalMock.verify(
                Global::getSector,
                never());
            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesSkipsAStaleSystemThatSeedsNoCell() {
            // A resize changes holding over ground already drawn; it never admits a
            // system to the map, so one with no cell has nothing to re-shape and must not
            // reach the re-derive at all.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(CELL_LESS_SYSTEM);
            applyTo(territories);

            politicsMock.verifyNoInteractions();

            assertThat(territories.getStyledCellByCellId())
                .isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesRedrawsNothingWhenTheHolderDidNotChange() {
            // The common resize: a colony grows, its faction still wins, and the drawing is
            // identical - so the whole redraw below the re-derive must be skipped.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getHolderBySystemId())
                .containsExactly(entryOwnedBy(FLIPPED_SYSTEM, HEGEMONY));

            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRecordsTheNewHolderWhenASystemChangesHands() {

            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);
            applyTo(territories);

            assertThat(territories.getHolderBySystemId().get(FLIPPED_SYSTEM).factionId())
                .isEqualTo(TRITACHYON);
        }

        @Test
        void applyStalePoliticsUpdatesDropsTheHolderOfASystemThatLostItsLastColony() {
            // Decivilised or bombed out: the system stays drawn but holds no holder, so the
            // entry goes rather than being left pointing at the faction that lost it.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, null);

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
            var territories = ownedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));

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
            var territories = ownedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));

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
        void applyStalePoliticsUpdatesReportsTheRefitAgainstTheCallersGeometry() {
            // The placements were re-fitted, so what they were fitted under has moved and the
            // caller has to be told - it holds that record beside the list. The revision goes
            // out as it came in because this path re-shapes cells within a partition it never
            // recut, so the fit ran against the very geometry the caller named.
            var territories = ownedBy(Map.of(
                FLIPPED_SYSTEM,
                HEGEMONY,
                NEIGHBOUR_SYSTEM,
                HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);

            var refittedUnder = applyTo(territories);

            assertThat(refittedUnder)
                .isEqualTo(REFITTED_UNDER);
                
            anchorsMock.verify(
                () -> ClusterAnchorsBuilder.rebuildClusterAnchors(
                    anyList(),
                    any(),
                    any(),
                    any(),
                    eq(GEOMETRY_REVISION)));
        }

        @Test
        void applyStalePoliticsUpdatesReportsNoRefitWhenNothingIsStale() {
            // Nothing was re-fitted, so the caller's record of what its standing placements
            // were fitted under still describes them and must not be overwritten.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            assertThat(applyTo(territories))
                .isNull();
        }

        @Test
        void applyStalePoliticsUpdatesReportsNoRefitWhenTheHolderDidNotChange() {
            // The same claim on the other early return: a resize that leaves the winner alone
            // leaves the placements alone, so there is nothing new to report about them.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));

            resolvesTo(FLIPPED_SYSTEM, ownerOf(HEGEMONY));

            MapLayerRefresh.markSystemGroupingStale(FLIPPED_SYSTEM);

            assertThat(applyTo(territories))
                .isNull();
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
        private void resolvesTo(String systemId, DominantHolder holder) {
            // The grouping matcher is typed because a sibling entry point takes a resolved
            // dominance pass in the same slot, and a bare any() would name neither.
            politicsMock
                .when(() -> SectorPolitics.resolveDominantHolder(
                    any(),
                    argThatIsSystem(systemId),
                    any(HolderGrouping.class)))
                .thenReturn(holder);
        }

        // Runs the refresh over the two-cell geometry every case shares, with the anchor and
        // label lists the plugin owns standing in as empty ones, and answers what it reported
        // the placements were re-fitted under.
        private AnchorFitFingerprint applyTo(PoliticalMapTerritories territories) {
            return IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                territories,
                new ArrayList<ClusterAnchor>(),
                new ArrayList<Label>(),
                twoAdjacentCells(),
                GEOMETRY_REVISION);
        }
    }

    // A geometry cache holding two adjacent square cells, each drawing as its own star: the
    // shape the fold reads to find a flipped system's neighbours. Mocked rather than built
    // from a sector because only its two lookups are read here, and a real partition would
    // make each case depend on the Voronoi build as well as on the fold.
    private static CellGeometryCache twoAdjacentCells() {

        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(Map.of(
                FLIPPED_SYSTEM,
                squareCellFacing(NEIGHBOUR_SYSTEM, 0),
                NEIGHBOUR_SYSTEM,
                squareCellFacing(FLIPPED_SYSTEM, 100)));

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
    private static List<CellEdge> squareCellFacing(String neighbourSystemId, double offsetX) {

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

    private static PoliticalMapTerritories ownedBy(Map<String, String> factionIdBySystemId) {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var entry : factionIdBySystemId.entrySet()) {
            ownerBySystemId.put(entry.getKey(), ownerOf(entry.getValue()));
        }
        return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(ownerBySystemId);
    }

    // The fold compares whole holders, so the shades matter only in that two holders of the
    // same faction must compare equal; one shared placeholder pair gives that.
    private static DominantHolder ownerOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }

    private static Map.Entry<String, DominantHolder> entryOwnedBy(
            String systemId,
            String factionId) {
        return Map.entry(systemId, ownerOf(factionId));
    }

    private static StarSystemAPI system(String systemId) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        return systemMock;
    }

    // Matches the star system carrying the given id, so a stub names the system it answers
    // for rather than the mock instance the fixture happened to build.
    private static StarSystemAPI argThatIsSystem(String systemId) {
        return argThat(system -> system != null && systemId.equals(system.getId()));
    }
}
