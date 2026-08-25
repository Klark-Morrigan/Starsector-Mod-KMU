package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.settings.KmuMapLayerSettings;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what a bloc's baked territory is: one frontier around everything it holds contiguously,
 * a separate ring per disjoint cluster, and the two paints its fill and border draw under - plus
 * the two cases that bake nothing at all rather than a record the draw pass would skip.
 *
 * <p>How the rings fall into bodies is the claim worth guarding. A bloc is traced from its member
 * cells in one pass, so two systems that touch must come back as one body under a single
 * continuous border rather than two squares drawn over each other; two that do not touch must
 * stay two bodies; and a rival a bloc has surrounded must come back as an enclave of the one body
 * around it. Each of those goes wrong silently - every ring still strokes, in the right colour,
 * with only the filled cells wrong.
 *
 * <p>The geometry underneath is pinned elsewhere and only wired here: the ring trace by
 * {@link kmu.maplayers.base.render.clusters.ClusterBorderTraceIntegrationTest}, the fill's carve by
 * {@link kmu.maplayers.base.render.clusters.SplitFillBuilderTest}, the palette rules by
 * {@link kmu.maplayers.politicalmap.base.render.style.MapPalettes}'s own suite. Cells are
 * hand-built 2000-unit squares, sized well clear of the fixed border channel, so which cells
 * touch is plain to read.
 */
final class FactionTerritoryBuilderTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Two cells of one bloc meeting along x = 2000, so the pair can be asked for a fused
    // frontier. The three that follow share no edge with anything: each is enclosed by the reach
    // bound alone, so a case naming one of them traces a closed ring whoever else is on the map.
    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String ISLAND_SYSTEM = "island";
    private static final String EXCLAVE_SYSTEM = "exclave";
    private static final String RIVAL_SYSTEM = "rival";

    // The 3x3 block of cells the enclave case is traced over: eight cells of one bloc ringing a
    // rival in the middle. Kept apart from the cells above because it is the only case whose
    // shape is a topology rather than a handful of squares, and generating it is what keeps that
    // topology readable.
    private static final int GRID_SPAN = 3;
    private static final int GRID_CELL_SIDE = 2000;
    private static final int GRID_CENTRE = 1;

    // The holder's two shades, kept distinct so an observed paint names which slot it came from.
    private static final Color OWNER_PRIMARY = Color.RED;
    private static final Color OWNER_SECONDARY = Color.BLUE;
    private static final DominantHolder HEGEMONY_OWNER =
        new DominantHolder(HEGEMONY, OWNER_PRIMARY, OWNER_SECONDARY);

    private static final DominantHolder TRITACHYON_OWNER =
        new DominantHolder(TRITACHYON, OWNER_PRIMARY, OWNER_SECONDARY);

    private static final double FILL_OPACITY = 0.5;
    private static final double BORDER_OPACITY = 0.25;
    private static final double BORDER_WIDTH = 3.0;

    // The live Dev-tab trace parameters, stubbed at their read: a weld tolerance loose enough to
    // chain the hand-built corners, and the miter limit the shipped border uses.
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    private static final Map<String, List<CellEdge>> EDGES = Map.of(
        HELD_SYSTEM, List.of(
            buildEdgeFacing(0, 0, 2000, 0, null),
            buildEdgeFacing(2000, 0, 2000, 2000, NEIGHBOUR_SYSTEM),
            buildEdgeFacing(2000, 2000, 0, 2000, null),
            buildEdgeFacing(0, 2000, 0, 0, null)),
        NEIGHBOUR_SYSTEM, List.of(
            buildEdgeFacing(2000, 0, 4000, 0, null),
            buildEdgeFacing(4000, 0, 4000, 2000, null),
            buildEdgeFacing(4000, 2000, 2000, 2000, null),
            buildEdgeFacing(2000, 2000, 2000, 0, HELD_SYSTEM)),
        ISLAND_SYSTEM, List.of(
            buildEdgeFacing(10000, 0, 12000, 0, null),
            buildEdgeFacing(12000, 0, 12000, 2000, null),
            buildEdgeFacing(12000, 2000, 10000, 2000, null),
            buildEdgeFacing(10000, 2000, 10000, 0, null)),
        EXCLAVE_SYSTEM, List.of(
            buildEdgeFacing(30000, 0, 32000, 0, null),
            buildEdgeFacing(32000, 0, 32000, 2000, null),
            buildEdgeFacing(32000, 2000, 30000, 2000, null),
            buildEdgeFacing(30000, 2000, 30000, 0, null)),
        RIVAL_SYSTEM, List.of(
            buildEdgeFacing(20000, 0, 22000, 0, null),
            buildEdgeFacing(22000, 0, 22000, 2000, null),
            buildEdgeFacing(22000, 2000, 20000, 2000, null),
            buildEdgeFacing(20000, 2000, 20000, 0, null)));

    // The trace reads its parameters off the live settings, so the seam stands for every case
    // here; without it the two Dev-tab reads would fault outside the game.
    private MockedStatic<KmuMapLayerSettings> settingsMock;

    @BeforeEach
    void openTheBorderTraceSeam() {

        settingsMock = mockStatic(KmuMapLayerSettings.class);
        settingsMock
            .when(KmuMapLayerSettings::getMapBorderWeldTolerance)
            .thenReturn(WELD_TOLERANCE);
        settingsMock
            .when(KmuMapLayerSettings::getMapBorderMiterLimit)
            .thenReturn(MITER_SPIKE_LIMIT);
    }

    @AfterEach
    void closeTheBorderTraceSeam() {
        settingsMock.close();
    }

    @Nested
    class BuildFactionTerritory {

        @Test
        void buildFactionTerritoryTracesTwoTouchingSystemsAsOneFrontier() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildDrawnStyle()),
                listCellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM),
                HEGEMONY,
                List.of(HELD_SYSTEM, NEIGHBOUR_SYSTEM));

            // The shared edge is a same-bloc seam, so it is never a border: the pair reads as one
            // body rather than two squares stroked along the line between them. Held as one
            // cluster carrying one loop rather than as one loop: a welded pair and a pair that
            // stayed apart differ in the cluster count, and only in the loop count by accident.
            assertThat(clusterGroup.clusters())
                .hasSize(1);
            assertThat(clusterGroup.clusters().get(0).outerLoop())
                .isNotEmpty();
            assertThat(clusterGroup.clusters().get(0).enclaveLoops())
                .isEmpty();
            assertThat(clusterGroup.clusters().get(0).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildFactionTerritoryTracesDisjointHoldingsAsAClusterApiece() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildDrawnStyle()),
                listCellsFor(ISLAND_SYSTEM, EXCLAVE_SYSTEM),
                HEGEMONY,
                List.of(ISLAND_SYSTEM, EXCLAVE_SYSTEM));

            // Rebuilding a bloc from its current members re-splits it: an exclave keeps its own
            // frontier instead of being welded to the homeland by the trace. Two clusters, each
            // with its own outer loop and nothing cut out of it - not one cluster carrying two
            // loops, which is what an enclave inside a single body would look like.
            assertThat(clusterGroup.clusters())
                .hasSize(2);
            assertThat(clusterGroup.clusters())
                .allSatisfy(cluster -> {
                    assertThat(cluster.outerLoop()).isNotEmpty();
                    assertThat(cluster.enclaveLoops()).isEmpty();
            });
        }

        @Test
        void buildFactionTerritoryTracesAnEnclosedRivalAsAnEnclaveOfTheOneBody() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildDrawnStyle(), listGridHolders()),
                listGridCells(),
                HEGEMONY,
                listGridRingCellIds());

            // A ring of cells is connected, so it is one body - and the rival it encloses is a
            // hole in that body rather than area outside it. The three ways this goes wrong all
            // still draw: two clusters (the ring read as split), one cluster with no enclave (the
            // hole lost, so the fill covers the rival), or the enclave promoted to a body of its
            // own (the rival painted in the bloc's own colour).
            assertThat(clusterGroup.clusters())
                .hasSize(1);
            assertThat(clusterGroup.clusters().get(0).enclaveLoops())
                .hasSize(1);
            assertThat(clusterGroup.clusters().get(0).outerLoop())
                .isNotEmpty();
            assertThat(clusterGroup.clusters().get(0).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildFactionTerritoryPaintsEachSlotFromItsOwnPaletteChoiceAndOpacity() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildDrawnStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY,
                List.of(ISLAND_SYSTEM));

            // Read off the group, since a bloc's paints are its own wherever its bodies sit.
            assertThat(clusterGroup.fill().colour())
                .isEqualTo(OWNER_PRIMARY);
            assertThat(clusterGroup.fill().alpha())
                .isEqualTo((float) FILL_OPACITY);
            assertThat(clusterGroup.border().colour())
                .isEqualTo(OWNER_SECONDARY);
            assertThat(clusterGroup.border().alpha())
                .isEqualTo((float) BORDER_OPACITY);
            assertThat(clusterGroup.borderWidth())
                .isEqualTo((float) BORDER_WIDTH);
        }

        @Test
        void buildFactionTerritoryBakesNoBorderRunsForANoColourBorder() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildFillOnlyStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY,
                List.of(ISLAND_SYSTEM));

            // A border switched off bakes no runs at all rather than runs the draw pass skips -
            // while the fill, which is still on, comes back as the frontier's own tessellation.
            // The cluster itself survives either way: it is the body, not the stroke.
            assertThat(clusterGroup.clusters())
                .hasSize(1);
            assertThat(clusterGroup.clusters().get(0).outerLoop())
                .isEmpty();
            assertThat(clusterGroup.clusters().get(0).enclaveLoops())
                .isEmpty();
            assertThat(clusterGroup.clusters().get(0).fillTriangles())
                .isNotEmpty();
        }

        @Test
        void buildFactionTerritoryBakesNothingWhenNeitherFillNorBorderDrawsAColour() {

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildNoColourStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY,
                List.of(ISLAND_SYSTEM));

            // Short-circuited before the trace: a bloc that paints nothing must not pay for the
            // ring walk that only its paints would have used.
            assertThat(clusterGroup)
                .isNull();
        }

        @Test
        void buildFactionTerritoryBakesNothingForMembersThatYieldNoBorderableGeometry() {

            var geometryCacheMock = mock(CellGeometryCache.class);

            when(geometryCacheMock.getCellEdgesByCellId())
                .thenReturn(Map.of());

            when(geometryCacheMock.getSystemIdByCellId())
                .thenReturn(Map.of(
                    HELD_SYSTEM,
                    HELD_SYSTEM));

            var clusterGroup = FactionTerritoryBuilder.buildFactionTerritory(
                buildTerritoriesStyledBy(buildDrawnStyle()),
                geometryCacheMock,
                HEGEMONY,
                List.of(HELD_SYSTEM));

            // A member whose cell carries no edges traces no ring, and a bloc with no frontier
            // has nothing to clip its fill against, so the whole record is dropped.
            assertThat(clusterGroup)
                .isNull();
        }
    }

    @Nested
    class BuildAllFactionTerritories {

        @Test
        void buildAllFactionTerritoriesKeysEachBlocsTerritoryByItsGroupingKey() {

            var territories = buildTerritoriesStyledBy(buildDrawnStyle(), Map.of(
                HELD_SYSTEM,
                HEGEMONY_OWNER,
                NEIGHBOUR_SYSTEM,
                HEGEMONY_OWNER,
                RIVAL_SYSTEM,
                TRITACHYON_OWNER));

            FactionTerritoryBuilder.buildAllFactionTerritories(
                territories,
                listCellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM, RIVAL_SYSTEM));

            // One entry per bloc rather than per system: the two Hegemony systems fuse into the
            // single territory their shared key groups them into.
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .containsOnlyKeys(HEGEMONY, TRITACHYON);

            assertThat(territories.getStyledClusterGroupByOwnerId().get(HEGEMONY).clusters())
                .hasSize(1);
        }

        @Test
        void buildAllFactionTerritoriesSkipsABlocThatBakesNothing() {

            var territories = buildTerritoriesStyledBy(buildDrawnStyle(), Map.of(
                HELD_SYSTEM,
                HEGEMONY_OWNER,
                RIVAL_SYSTEM,
                TRITACHYON_OWNER));

            // The rival's cell is grouped but carries no edges, so its bloc bakes nothing.
            var geometryCacheMock = mock(CellGeometryCache.class);

            when(geometryCacheMock.getCellEdgesByCellId())
                .thenReturn(Map.of(HELD_SYSTEM, EDGES.get(HELD_SYSTEM)));

            when(geometryCacheMock.getSystemIdByCellId())
                .thenReturn(Map.of(
                    HELD_SYSTEM,
                    HELD_SYSTEM,
                    RIVAL_SYSTEM,
                    RIVAL_SYSTEM));

            FactionTerritoryBuilder.buildAllFactionTerritories(territories, geometryCacheMock);

            // Absent rather than mapped to null: every reader of this map paints what it finds.
            assertThat(territories.getStyledClusterGroupByOwnerId())
                .containsOnlyKeys(HEGEMONY);
        }
    }

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge buildEdgeFacing(
            double x1,
            double y1,
            double x2,
            double y2,
            String neighbourSystemId) {
        return new CellEdge(
            x1,
            y1,
            x2,
            y2,
            neighbourSystemId == null
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(neighbourSystemId));
    }

    // A geometry cache holding just the named cells, each drawing as its own star - the raw
    // partition a bloc's border is traced from.
    // The 3x3 block as a geometry cache, every cell drawing as its own system.
    private static CellGeometryCache listGridCells() {

        var geometryCacheMock = mock(CellGeometryCache.class);
        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();
        var systemIdByCellId = new LinkedHashMap<String, String>();

        for (var column = 0; column < GRID_SPAN; column++) {
            for (var row = 0; row < GRID_SPAN; row++) {

                edgesByCellId.put(readGridCellId(column, row), listGridCellEdges(column, row));
                systemIdByCellId.put(readGridCellId(column, row), readGridCellId(column, row));
            }
        }

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(edgesByCellId);
        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(systemIdByCellId);

        return geometryCacheMock;
    }

    // The ring to the Hegemony and the middle to its rival - the holding that makes the middle an
    // enclave rather than a gap in the trace.
    private static Map<String, DominantHolder> listGridHolders() {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var column = 0; column < GRID_SPAN; column++) {
            for (var row = 0; row < GRID_SPAN; row++) {

                ownerBySystemId.put(
                    readGridCellId(column, row),
                    isGridCentre(column, row) ? TRITACHYON_OWNER : HEGEMONY_OWNER);
            }
        }
        return ownerBySystemId;
    }

    // The eight cells the Hegemony draws - every one but the enclosed middle.
    private static List<String> listGridRingCellIds() {

        var cellIds = new ArrayList<String>();
        for (var column = 0; column < GRID_SPAN; column++) {
            for (var row = 0; row < GRID_SPAN; row++) {

                if (!isGridCentre(column, row)) {
                    cellIds.add(readGridCellId(column, row));
                }
            }
        }
        return cellIds;
    }

    // One grid cell's four edges, counter-clockwise from its bottom-left corner, each naming the
    // neighbour across it - or the reach bound, past the block's rim.
    private static List<CellEdge> listGridCellEdges(int column, int row) {

        var minX = column * GRID_CELL_SIDE;
        var minY = row * GRID_CELL_SIDE;
        var maxX = minX + GRID_CELL_SIDE;
        var maxY = minY + GRID_CELL_SIDE;

        return List.of(
            buildEdgeFacing(minX, minY, maxX, minY, findGridNeighbourId(column, row - 1)),
            buildEdgeFacing(maxX, minY, maxX, maxY, findGridNeighbourId(column + 1, row)),
            buildEdgeFacing(maxX, maxY, minX, maxY, findGridNeighbourId(column, row + 1)),
            buildEdgeFacing(minX, maxY, minX, minY, findGridNeighbourId(column - 1, row)));
    }

    // The cell across one edge, or null where the edge is on the block's rim and faces nothing.
    private static String findGridNeighbourId(int column, int row) {
        var isOffTheGrid = column < 0 || column >= GRID_SPAN || row < 0 || row >= GRID_SPAN;
        return isOffTheGrid
            ? null
            : readGridCellId(column, row);
    }

    private static String readGridCellId(int column, int row) {
        return "grid-" + column + "-" + row;
    }

    private static boolean isGridCentre(int column, int row) {
        return column == GRID_CENTRE && row == GRID_CENTRE;
    }

    private static CellGeometryCache listCellsFor(String... systemIds) {

        var geometryCacheMock = mock(CellGeometryCache.class);
        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();
        var systemIdByCellId = new LinkedHashMap<String, String>();

        for (var systemId : systemIds) {
            edgesByCellId.put(systemId, EDGES.get(systemId));
            systemIdByCellId.put(systemId, systemId);
        }

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(edgesByCellId);
        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(systemIdByCellId);

        return geometryCacheMock;
    }

    // An unfiltered pass over the four fixture systems, every category drawing under the one
    // style a test names - so which category the resolver picks cannot account for an outcome.
    private static PoliticalMapTerritories buildTerritoriesStyledBy(CategoryStyle style) {
        return buildTerritoriesStyledBy(style, Map.of(
            HELD_SYSTEM,
            HEGEMONY_OWNER,
            NEIGHBOUR_SYSTEM,
            HEGEMONY_OWNER,
            ISLAND_SYSTEM,
            HEGEMONY_OWNER,
            EXCLAVE_SYSTEM,
            HEGEMONY_OWNER,
            RIVAL_SYSTEM,
            TRITACHYON_OWNER));
    }

    private static PoliticalMapTerritories buildTerritoriesStyledBy(
            CategoryStyle style,
            Map<String, DominantHolder> ownerBySystemId) {

        return new PoliticalMapTerritories(
            SystemOccupancy.createCopyOf(ownerBySystemId, Set.of(), Set.of()),
            Set.of(),
            new MapStyling(
                PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory(style),
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                new FactionPalette(Color.GREEN, Color.YELLOW),
                PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
            new ViewGrouping(buildViewMockAdjustingNothing(), HolderGrouping.identity()),
            new FilterSnapshot(null, ElementStyleAdjustment.NONE, Set.of()));
    }

    // A view stub that styles every bloc as its own faction and recedes none of them, so the
    // paints below come from the category style and the holder's palette alone.
    private static PoliticalMapView buildViewMockAdjustingNothing() {

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.shouldUseIndependentStyle(any(), any(), any()))
            .thenReturn(false);
        when(viewMock.resolveBlocStyleAdjustment(any(), any()))
            .thenReturn(ElementStyleAdjustment.NONE);

        return viewMock;
    }

    // Fill and national border both drawn, each from a different palette slot so the two paints
    // are told apart by colour; the interior seam is a per-cell record and never read here.
    private static CategoryStyle buildDrawnStyle() {
        return new CategoryStyle(
            new ElementStyle(
                FactionPaletteSlot.PRIMARY,
                FILL_OPACITY),
            new ElementStyle(
                FactionPaletteSlot.SECONDARY,
                BORDER_OPACITY),
                BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0),
                1.0);
    }

    // The fill on and the border switched off - the one slot combination that still draws.
    private static CategoryStyle buildFillOnlyStyle() {
        return new CategoryStyle(
            new ElementStyle(
                FactionPaletteSlot.PRIMARY,
                FILL_OPACITY),
            new ElementStyle(
                null,
                BORDER_OPACITY), BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0), 1.0);
    }

    // Every slot "No color" - a bloc the player has switched off entirely.
    private static CategoryStyle buildNoColourStyle() {
        return new CategoryStyle(
            new ElementStyle(
                null,
                FILL_OPACITY),
            new ElementStyle(
                null,
                BORDER_OPACITY),
                BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0),
                1.0);
    }
}
