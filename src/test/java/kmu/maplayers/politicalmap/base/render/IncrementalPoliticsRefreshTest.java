package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
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
 * incremental path exists to make - that it touches the systems whose ownership actually
 * moved and their neighbours, and nothing else - since anything wider costs the frame it
 * was written to save, and anything narrower leaves the map drawing a stale owner.
 *
 * <p>A unit test, so the primitives the fold delegates to are mocked at their static seams:
 * the dominance resolve that answers who holds a system, the two builders that turn a
 * re-shaped cell and a faction's members into draw records, and the anchor and label
 * rebuilds that ride along. Each is pinned by its own suite; what belongs here is only the
 * decision about which of them to call and with what.
 */
final class IncrementalPoliticsRefreshTest {
    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    // The flipping system and the neighbour whose shared edge flips with it. Both seed a
    // cell, so both are re-shapeable; the third seeds none.
    private static final String FLIPPED_SYSTEM = "flipped";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String CELL_LESS_SYSTEM = "cellless";

    @Nested
    class ApplyStalePoliticsUpdates {
        // Closed in reverse on the way out, so a seam opened over another is never left
        // standing when the inner one is already gone.
        private final List<MockedStatic<?>> openStaticSeams = new ArrayList<>();
        private MockedStatic<Global> globalMock;
        private MockedStatic<SectorPolitics> politicsMock;
        private MockedStatic<StyledCellBuilder> styledCellsMock;
        private MockedStatic<FactionTerritoryBuilder> territoriesMock;
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
            when(sectorMock.getStarSystems()).thenReturn(systems);
            globalMock.when(Global::getSector).thenReturn(sectorMock);

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
            // leaves each a no-op, so a case here asserts the fold and not their output.
            openSeam(ClusterAnchorsBuilder.class);
            openSeam(LabelsBuilder.class);
            // Read as an argument to the label rebuild, so it evaluates even with that
            // rebuild neutralised - and it reads save-backed memory no test JVM has.
            openSeam(NameFormatPreference.class)
                    .when(NameFormatPreference::getSelectedNameFormat)
                    .thenReturn(FactionNameFormatChoice.NONE);

            // The stale set is static and shared, so a residue from another suite would
            // read here as a system this one never marked.
            MapLayerRefresh.drainStalePoliticsSystemIds();
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

            globalMock.verify(Global::getSector, never());
            assertThat(territories.getStyledCellByCellId()).isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesSkipsAStaleSystemThatSeedsNoCell() {
            // A resize changes ownership over ground already drawn; it never admits a
            // system to the map, so one with no cell has nothing to re-shape and must not
            // reach the re-derive at all.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));
            MapLayerRefresh.markSystemPoliticsStale(CELL_LESS_SYSTEM);

            applyTo(territories);

            politicsMock.verifyNoInteractions();
            assertThat(territories.getStyledCellByCellId()).isEmpty();
        }

        @Test
        void applyStalePoliticsUpdatesRedrawsNothingWhenTheOwnerDidNotChange() {
            // The common resize: a colony grows, its faction still wins, and the drawing is
            // identical - so the whole redraw below the re-derive must be skipped.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));
            resolvesTo(FLIPPED_SYSTEM, ownerOf(HEGEMONY));
            MapLayerRefresh.markSystemPoliticsStale(FLIPPED_SYSTEM);

            applyTo(territories);

            assertThat(territories.getOwnerBySystemId())
                    .containsExactly(entryOwnedBy(FLIPPED_SYSTEM, HEGEMONY));
            styledCellsMock.verifyNoInteractions();
            territoriesMock.verifyNoInteractions();
        }

        @Test
        void applyStalePoliticsUpdatesRecordsTheNewOwnerWhenASystemChangesHands() {
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));
            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));
            MapLayerRefresh.markSystemPoliticsStale(FLIPPED_SYSTEM);

            applyTo(territories);

            assertThat(territories.getOwnerBySystemId().get(FLIPPED_SYSTEM).factionId())
                    .isEqualTo(TRITACHYON);
        }

        @Test
        void applyStalePoliticsUpdatesDropsTheOwnerOfASystemThatLostItsLastColony() {
            // Decivilised or bombed out: the system stays drawn but holds no owner, so the
            // entry goes rather than being left pointing at the faction that lost it.
            var territories = ownedBy(Map.of(FLIPPED_SYSTEM, HEGEMONY));
            resolvesTo(FLIPPED_SYSTEM, null);
            MapLayerRefresh.markSystemPoliticsStale(FLIPPED_SYSTEM);

            applyTo(territories);

            assertThat(territories.getOwnerBySystemId()).doesNotContainKey(FLIPPED_SYSTEM);
        }

        @Test
        void applyStalePoliticsUpdatesReshapesTheFlippedSystemAndItsNeighbour() {
            // The neighbour's own owner did not move, but the edge it shares with the
            // flipped system just turned from a same-faction seam into a national border,
            // so it has to be re-shaped too or the border draws down one side only.
            var territories = ownedBy(Map.of(
                    FLIPPED_SYSTEM, HEGEMONY, NEIGHBOUR_SYSTEM, HEGEMONY));
            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));
            MapLayerRefresh.markSystemPoliticsStale(FLIPPED_SYSTEM);

            applyTo(territories);

            styledCellsMock.verify(() -> StyledCellBuilder.buildStyledCellForSystem(
                    any(), eq(FLIPPED_SYSTEM), any()));
            styledCellsMock.verify(() -> StyledCellBuilder.buildStyledCellForSystem(
                    any(), eq(NEIGHBOUR_SYSTEM), any()));
        }

        @Test
        void applyStalePoliticsUpdatesRebuildsTheLosingAndGainingFactionsTerritories() {
            // Both sides of the transfer change shape - one loses the cell, the other gains
            // it - and no third faction's rings trace a cell that moved, so exactly these
            // two rebuild.
            var territories = ownedBy(Map.of(
                    FLIPPED_SYSTEM, HEGEMONY, NEIGHBOUR_SYSTEM, HEGEMONY));
            resolvesTo(FLIPPED_SYSTEM, ownerOf(TRITACHYON));
            MapLayerRefresh.markSystemPoliticsStale(FLIPPED_SYSTEM);

            applyTo(territories);

            territoriesMock.verify(() -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(), any(), eq(HEGEMONY), anyList()));
            territoriesMock.verify(() -> FactionTerritoryBuilder.buildFactionTerritory(
                    any(), any(), eq(TRITACHYON), anyList()));
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
        // owner rather than as a missing stub.
        private void resolvesTo(String systemId, DominantOwner owner) {
            // The grouping matcher is typed because a sibling entry point takes a resolved
            // dominance pass in the same slot, and a bare any() would name neither.
            politicsMock.when(() -> SectorPolitics.resolveDominantOwner(
                            any(),
                            argThatIsSystem(systemId),
                            any(OwnershipGrouping.class)))
                    .thenReturn(owner);
        }

        // Runs the refresh over the two-cell geometry every case shares, with the anchor and
        // label lists the plugin owns standing in as empty ones.
        private void applyTo(PoliticalMapTerritories territories) {
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                    territories,
                    new ArrayList<ClusterAnchor>(),
                    new ArrayList<Label>(),
                    twoAdjacentCells());
        }
    }

    // A geometry cache holding two adjacent square cells, each drawing as its own star: the
    // shape the fold reads to find a flipped system's neighbours. Mocked rather than built
    // from a sector because only its two lookups are read here, and a real partition would
    // make each case depend on the Voronoi build as well as on the fold.
    private static CellGeometryCache twoAdjacentCells() {
        var geometryCacheMock = mock(CellGeometryCache.class);
        when(geometryCacheMock.getCellEdgesByCellId()).thenReturn(Map.of(
                FLIPPED_SYSTEM, squareCellFacing(NEIGHBOUR_SYSTEM, 0),
                NEIGHBOUR_SYSTEM, squareCellFacing(FLIPPED_SYSTEM, 100)));
        when(geometryCacheMock.getSystemIdByCellId()).thenReturn(Map.of(
                FLIPPED_SYSTEM, FLIPPED_SYSTEM,
                NEIGHBOUR_SYSTEM, NEIGHBOUR_SYSTEM));
        return geometryCacheMock;
    }

    // A closed four-edge cell offset along x, one edge of which faces the given neighbour
    // while the rest face open space. Real coordinates because the re-shape insets these
    // edges for real; only the adjacency tag is what the fold itself reads.
    private static List<CellEdge> squareCellFacing(String neighbourSystemId, double offsetX) {
        var frontier = new EdgeTarget.NoSystem("frontier");
        return List.of(
                new CellEdge(offsetX, 0, offsetX + 50, 0,
                        new EdgeTarget.AcrossSystem(neighbourSystemId)),
                new CellEdge(offsetX + 50, 0, offsetX + 50, 50, frontier),
                new CellEdge(offsetX + 50, 50, offsetX, 50, frontier),
                new CellEdge(offsetX, 50, offsetX, 0, frontier));
    }

    private static PoliticalMapTerritories ownedBy(Map<String, String> factionIdBySystemId) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        for (var entry : factionIdBySystemId.entrySet()) {
            ownerBySystemId.put(entry.getKey(), ownerOf(entry.getValue()));
        }
        return PoliticalMapTerritoryFixtures.createTerritoriesOwnedBy(ownerBySystemId);
    }

    // The fold compares whole owners, so the shades matter only in that two owners of the
    // same faction must compare equal; one shared placeholder pair gives that.
    private static DominantOwner ownerOf(String factionId) {
        return new DominantOwner(factionId, Color.GRAY, Color.GRAY);
    }

    private static Map.Entry<String, DominantOwner> entryOwnedBy(
            String systemId, String factionId) {
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
