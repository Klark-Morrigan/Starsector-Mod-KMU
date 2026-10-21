package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.SquareCellFixture.SquareSide;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;
import static kmu.maplayers.base.geometry.SquareCellFixture.buildSquareCell;
import static kmu.maplayers.base.geometry.SquareCellFixture.buildSquareCellFacing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what an owner's baked cluster group is: one frontier around everything it holds contiguously,
 * a separate ring per disjoint cluster, and the two paints its fill and border draw under - plus
 * the two cases that bake nothing at all rather than a record the draw pass would skip.
 *
 * <p>How the rings fall into bodies is the rule worth guarding. An owner is traced from its member
 * cells in one pass, so two systems that touch must come back as one body under a single
 * continuous border rather than two squares drawn over each other; two that do not touch must
 * stay two bodies; and a rival an owner has surrounded must come back as an enclave of the one body
 * around it. Each of those goes wrong silently - every ring still strokes, in the right colour,
 * with only the filled cells wrong.
 *
 * <p>Every case stands the builder up on the values it states - the paints, the cells, their
 * grouping and the fill sets - with no build around it, which is the whole of what a layer needs to
 * trace owners.
 *
 * <p>The geometry underneath is pinned elsewhere and only wired here: the ring trace by
 * {@link kmu.maplayers.base.render.clusters.ClusterBorderTraceIntegrationTests}, the fill's carve by
 * {@link kmu.maplayers.base.render.clusters.SplitFillBuilderTests}, the palette rules by
 * {@link kmu.maplayers.ownermap.render.style.MapPalettes}'s own suite. Cells are
 * hand-built 2000-unit squares, sized well clear of the fixed border channel, so which cells
 * touch is plain to read.
 */
final class ClusterGroupBuilderTests {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Two cells of one owner meeting along x = 2000, so the pair can be asked for a fused
    // frontier. The three that follow share no edge with anything: each is enclosed by the reach
    // bound alone, so a case naming one of them traces a closed ring whoever else is on the map.
    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String ISLAND_SYSTEM = "island";
    private static final String EXCLAVE_SYSTEM = "exclave";
    private static final String RIVAL_SYSTEM = "rival";

    // The 3x3 block of cells the enclave case is traced over: eight cells of one owner ringing a
    // rival in the middle. Kept apart from the cells above because it is the only case whose
    // shape is a topology rather than a handful of squares, and generating it is what keeps that
    // topology readable.
    private static final int GRID_SPAN = 3;
    private static final int GRID_CELL_SIDE = 2000;
    private static final int GRID_CENTRE = 1;

    // The owner's two shades, kept distinct so an observed paint names which slot it came from.
    private static final Color OWNER_PRIMARY = Color.RED;
    private static final Color OWNER_SECONDARY = Color.BLUE;

    private static final SystemOwner HEGEMONY_OWNER =
        new SystemOwner(HEGEMONY, new ColourPair(OWNER_PRIMARY, OWNER_SECONDARY));

    private static final SystemOwner TRITACHYON_OWNER =
        new SystemOwner(TRITACHYON, new ColourPair(OWNER_PRIMARY, OWNER_SECONDARY));

    private static final double FILL_OPACITY = 0.5;
    private static final double BORDER_OPACITY = 0.25;
    private static final double BORDER_WIDTH = 3.0;

    // The side of every square cell here, sized well clear of the fixed border channel.
    private static final double CELL_SIDE = 2000;

    private static final Map<String, List<CellEdge>> EDGES = Map.of(
        HELD_SYSTEM,
        buildSquareCellFacing(0, 0, CELL_SIDE, SquareSide.RIGHT, NEIGHBOUR_SYSTEM),
        NEIGHBOUR_SYSTEM,
        buildSquareCellFacing(2000, 0, CELL_SIDE, SquareSide.LEFT, HELD_SYSTEM),
        ISLAND_SYSTEM,
        buildSquareCell(10000, 0, CELL_SIDE),
        EXCLAVE_SYSTEM,
        buildSquareCell(30000, 0, CELL_SIDE),
        RIVAL_SYSTEM,
        buildSquareCell(20000, 0, CELL_SIDE));

    @Nested
    class BuildClusterGroup {

        @Test
        void tracesTwoTouchingSystemsAsOneFrontier() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildDrawnStyle()),
                listCellsFor(HELD_SYSTEM, NEIGHBOUR_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(HELD_SYSTEM, NEIGHBOUR_SYSTEM));

            // The shared edge is a same-owner seam, so it is never a border: the pair reads as one
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
        void tracesDisjointHoldingsAsAClusterApiece() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildDrawnStyle()),
                listCellsFor(ISLAND_SYSTEM, EXCLAVE_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(ISLAND_SYSTEM, EXCLAVE_SYSTEM));

            // Rebuilding an owner from its current members re-splits it: an exclave keeps its own
            // frontier instead of being welded to the homeland by the trace. Two clusters, each
            // with its own outer loop and nothing cut out of it - not one cluster carrying two
            // loops, which is what an enclave inside a single body would look like.
            assertThat(clusterGroup.clusters())
                .hasSize(2);

            assertThat(clusterGroup.clusters())
                .allSatisfy(cluster -> {

                    assertThat(cluster.outerLoop())
                        .isNotEmpty();
                    assertThat(cluster.enclaveLoops())
                        .isEmpty();
            });
        }

        @Test
        void tracesAnEnclosedRivalAsAnEnclaveOfTheOneBody() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildDrawnStyle(), listGridOwners()),
                listGridCells(),
                HEGEMONY_OWNER,
                buildCellKeys(listGridRingCellIds().toArray(String[]::new)));

            // A ring of cells is connected, so it is one body - and the rival it encloses is a
            // hole in that body rather than area outside it. The three ways this goes wrong all
            // still draw: two clusters (the ring read as split), one cluster with no enclave (the
            // hole lost, so the fill covers the rival), or the enclave promoted to a body of its
            // own (the rival painted in the owner's own colour).
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
        void paintsEachSlotFromItsOwnPaletteChoiceAndOpacity() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildDrawnStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(ISLAND_SYSTEM));

            // Read off the group, since an owner's paints are its own wherever its bodies sit.
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
        void bakesNoBorderRunsForANoColourBorder() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildFillOnlyStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(ISLAND_SYSTEM));

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
        void bakesNoFillForANoColourFill() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildBorderOnlyStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(ISLAND_SYSTEM));

            // A fill switched off tessellates nothing rather than triangles the draw pass skips,
            // while the body still strokes its border.
            assertThat(clusterGroup.clusters())
                .hasSize(1);
            assertThat(clusterGroup.clusters().get(0).fillTriangles())
                .isEmpty();
            assertThat(clusterGroup.clusters().get(0).hatchSegments())
                .isEmpty();
            assertThat(clusterGroup.clusters().get(0).outerLoop())
                .isNotEmpty();
        }

        @Test
        void bakesNothingWhenNeitherFillNorBorderDrawsAColour() {

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildNoColourStyle()),
                listCellsFor(ISLAND_SYSTEM),
                HEGEMONY_OWNER,
                buildCellKeys(ISLAND_SYSTEM));

            // Short-circuited before the trace: an owner that paints nothing must not pay for the
            // ring walk that only its paints would have used.
            assertThat(clusterGroup)
                .isNull();
        }

        @Test
        void bakesNothingForMembersThatYieldNoBorderableGeometry() {

            var geometryCacheMock = mock(CellGeometryCache.class);

            when(geometryCacheMock.getCellEdgesByCellKey())
                .thenReturn(Map.of());

            when(geometryCacheMock.getSystemKeyByCellKey())
                .thenReturn(buildDrawnSystemKeys(Map.of(
                    HELD_SYSTEM,
                    HELD_SYSTEM)));

            var clusterGroup = buildClusterGroupOf(
                buildHoldingStyledBy(buildDrawnStyle()),
                geometryCacheMock,
                HEGEMONY_OWNER,
                buildCellKeys(HELD_SYSTEM));

            // A member whose cell carries no edges traces no ring, and an owner with no frontier
            // has nothing to clip its fill against, so the whole record is dropped.
            assertThat(clusterGroup)
                .isNull();
        }
    }

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

        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(buildKeyedValues(edgesByCellId));
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(buildDrawnSystemKeys(systemIdByCellId));

        return geometryCacheMock;
    }

    // The ring to the Hegemony and the middle to its rival - the holding that makes the middle an
    // enclave rather than a gap in the trace.
    private static Map<String, SystemOwner> listGridOwners() {

        var ownerBySystemId = new LinkedHashMap<String, SystemOwner>();

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

        var isOffTheGrid = column < 0
            || column >= GRID_SPAN
            || row < 0
            || row >= GRID_SPAN;

        return isOffTheGrid
            ? null
            : readGridCellId(column, row);
    }

    private static String readGridCellId(int column, int row) {
        return "grid-" + column + "-" + row;
    }

    private static boolean isGridCentre(int column, int row) {

        return column == GRID_CENTRE
            && row == GRID_CENTRE;
    }

    // A geometry cache holding just the named cells, each drawing as its own star - the raw
    // partition an owner's border is traced from.
    private static CellGeometryCache listCellsFor(String... systemIds) {

        var geometryCacheMock = mock(CellGeometryCache.class);
        var edgesByCellId = new LinkedHashMap<String, List<CellEdge>>();
        var systemIdByCellId = new LinkedHashMap<String, String>();

        for (var systemId : systemIds) {

            edgesByCellId.put(systemId, EDGES.get(systemId));
            systemIdByCellId.put(systemId, systemId);
        }

        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(buildKeyedValues(edgesByCellId));
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(buildDrawnSystemKeys(systemIdByCellId));

        return geometryCacheMock;
    }

    // An unfiltered pass over the four fixture systems, every category drawing under the one
    // style a test names - so which category the resolver picks cannot account for an outcome.
    private static PaintedHolding buildHoldingStyledBy(CategoryStyle style) {

        return buildHoldingStyledBy(style, Map.of(
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

    // One owner's cluster group, stood up on the values the builder states and nothing else: the
    // paints, the cells, how they group under the holding, and two empty fill sets. The grouping is
    // composed here the way a rebuild composes it once for the whole map.
    private static StyledClusterGroup buildClusterGroupOf(
            PaintedHolding holding,
            CellGeometryCache geometryCache,
            SystemOwner owner,
            List<SystemKey> memberCellKeys) {

        var groupBuilder = new ClusterGroupBuilder(
            holding.paints(),
            SystemOwner.mapCellGrouping(
                geometryCache.getSystemKeyByCellKey(),
                holding.ownerBySystemKey()),
            geometryCache.getCellEdgesByCellKey(),
            SystemFillStates.ALL_SOLID);

        return groupBuilder.buildClusterGroup(owner, memberCellKeys);
    }

    private static PaintedHolding buildHoldingStyledBy(
            CategoryStyle style,
            Map<String, SystemOwner> ownerBySystemId) {

        return buildHoldingKeyedBy(style, buildKeyedValues(ownerBySystemId));
    }

    // The same holding with its owners stated by key, for the one case a name cannot pose:
    // two systems sharing a vanilla ID, held by different owners.
    private static PaintedHolding buildHoldingKeyedBy(
            CategoryStyle style,
            Map<SystemKey, SystemOwner> ownerBySystemKey) {

        return new PaintedHolding(
            new MapPaints(
                new MapStyling(
                    OwnerMapClusterFixtures.createRenderStyleForEveryCategory(style),
                    OwnerMapClusterFixtures.BORDER_TRACE,
                    OwnerCategoriesFake.SHARED_FAKE,
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE,
                    new ColourPair(Color.GREEN, Color.YELLOW),
                    OwnerMapClusterFixtures.NEUTRAL_PALETTE),
                // A reading placing every owner at full strength and receding none of them, so the
                // paints below come from the category style and the owner's palette alone.
                OwnerReadingFake.createAnsweringNothing(),
                ContentInputsFixtures.createInertInputs()),
            ownerBySystemKey);
    }

    // Fill and cluster border both drawn, each from a different palette slot so the two paints
    // are told apart by colour; the interior seam is a per-cell record and never read here.
    private static CategoryStyle buildDrawnStyle() {

        return new CategoryStyle(
            new ElementStyle(
                OwnerPaletteSlot.PRIMARY,
                FILL_OPACITY),
            new ElementStyle(
                OwnerPaletteSlot.SECONDARY,
                BORDER_OPACITY),
                BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0),
                1.0);
    }

    // The border on and the fill switched off.
    private static CategoryStyle buildBorderOnlyStyle() {

        return new CategoryStyle(
            new ElementStyle(
                null,
                FILL_OPACITY),
            new ElementStyle(
                OwnerPaletteSlot.SECONDARY,
                BORDER_OPACITY),
                BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0),
                1.0);
    }

    // The fill on and the border switched off.
    private static CategoryStyle buildFillOnlyStyle() {

        return new CategoryStyle(
            new ElementStyle(
                OwnerPaletteSlot.PRIMARY,
                FILL_OPACITY),
            new ElementStyle(
                null,
                BORDER_OPACITY), BORDER_WIDTH,
            new ElementStyle(
                null,
                1.0), 1.0);
    }

    // Every slot "No color" - an owner the player has switched off entirely.
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

    /**
     * What a case states before the cells: the paints the owners draw in, and who holds each
     * system - the half of the builder's inputs a case varies by holding rather than by geometry.
     *
     * @param paints           the paints every owner draws in
     * @param ownerBySystemKey who holds each system, which the grouping is composed over
     */
    private record PaintedHolding(
        MapPaints paints,
        Map<SystemKey, SystemOwner> ownerBySystemKey) {
    }
}
