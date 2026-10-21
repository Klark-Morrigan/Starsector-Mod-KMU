package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.SquareCellFixture.SquareSide;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ContentInputsFixtures;
import kmu.maplayers.ownermap.OwnerPaintedViewFake;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.OwnerSourceFake;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.OwnerCategoriesFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;
import static kmu.maplayers.base.geometry.SquareCellFixture.buildSquareCell;
import static kmu.maplayers.base.geometry.SquareCellFixture.buildSquareCellFacing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the full build as the default wiring of its stages rather than the only way to reach them:
 * a cell builder and a cluster-group builder stood up alone, on values a layer states for itself,
 * bake exactly what the build bakes from the same values.
 *
 * <p>Also pins what the build does with the group builder's answers, which no stage owns: one entry
 * per owner, keyed by the grouping, and none for an owner that baked nothing.
 *
 * <p>Nothing between the build and the stages is stood in for, which is the point. The sector is
 * five hand-built 2000-unit squares: two of one owner sharing an edge, a rival on its own, a
 * settled system nobody owns, and empty space - every form a cell or an owner can take.
 */
final class OwnerMapBuilderIntegrationTests {

    // The side of every square cell here, wide enough to clear the fixed border channel.
    private static final double CELL_SIDE = 2000;

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    private static final String HELD_SYSTEM = "held";
    private static final String NEIGHBOUR_SYSTEM = "neighbour";
    private static final String RIVAL_SYSTEM = "rival";
    private static final String SETTLED_SYSTEM = "settled";
    private static final String EMPTY_SYSTEM = "empty";

    private static final SystemOwner HEGEMONY_OWNER =
        new SystemOwner(HEGEMONY, new ColourPair(Color.RED, Color.BLUE));

    private static final SystemOwner TRITACHYON_OWNER =
        new SystemOwner(TRITACHYON, new ColourPair(Color.CYAN, Color.MAGENTA));

    // The scheme every build here is styled by: every category in one bundle that draws fill,
    // border and seam alike, so each form of cell and every owner puts ink down.
    private static final MapStyling STYLING = new MapStyling(
        OwnerMapClusterFixtures.createRenderStyleForEveryCategory(
            OwnerMapClusterFixtures.createInertCategoryStyle()),
        OwnerMapClusterFixtures.BORDER_TRACE,
        OwnerCategoriesFake.SHARED_FAKE,
        OwnerMapClusterFixtures.NEUTRAL_PALETTE,
        new ColourPair(Color.GREEN, Color.YELLOW),
        new ColourPair(Color.WHITE, Color.WHITE));

    private static final ContentInputs CONTENT_INPUTS = ContentInputsFixtures.createInertInputs();
    private static final OwnerReading READING = OwnerReadingFake.createAnsweringNothing();

    // The two owners, the inhabitation and one unfilled system, so the owned path, both unowned
    // categories and the fill split all run.
    private static final ResolvedOwners OWNERS = new ResolvedOwners(
        buildKeyedValues(Map.of(
            HELD_SYSTEM,
            HEGEMONY_OWNER,
            NEIGHBOUR_SYSTEM,
            HEGEMONY_OWNER,
            RIVAL_SYSTEM,
            TRITACHYON_OWNER)),
        SystemFillStates.createOf(Map.of(buildCellKey(NEIGHBOUR_SYSTEM), FillState.UNFILLED)),
        buildKeyedSystemKeys(List.of(HELD_SYSTEM, NEIGHBOUR_SYSTEM, RIVAL_SYSTEM, SETTLED_SYSTEM)),
        Set.of());

    @Nested
    class BuildClusters {

        @Test
        void bakesEachCellAsTheCellBuilderStoodUpAloneBakesIt() {

            var geometryCache = buildFiveCells();

            var grouping = SystemOwner.mapCellGrouping(
                geometryCache.getSystemKeyByCellKey(),
                OWNERS.ownerBySystemKey());

            var shapedCells = CellShaper.shapeCells(
                geometryCache.getCellEdgesByCellKey(),
                grouping,
                EdgeInset.asTheMapDraws());

            var cellBuilder = new PaintedCellBuilder(
                new MapPaints(STYLING, READING, CONTENT_INPUTS),
                SystemOccupancy.createCopyOf(
                    OWNERS.ownerBySystemKey(),
                    OWNERS.inhabitedSystemKeys(),
                    OWNERS.spotlitPresenceSystemKeys()));

            var expectedCellByCellKey = new LinkedHashMap<SystemKey, PaintedCell>();

            for (var entry : shapedCells.entrySet()) {

                expectedCellByCellKey.put(
                    entry.getKey(),
                    cellBuilder.buildPaintedCell(entry.getKey(), entry.getValue()));
            }

            var actualClusters = buildClustersOver(geometryCache);

            assertThat(actualClusters.getStyledCellByCellKey())
                .containsOnlyKeys(expectedCellByCellKey.keySet());

            for (var entry : expectedCellByCellKey.entrySet()) {

                assertThat(actualClusters.getStyledCellByCellKey().get(entry.getKey()))
                    .usingRecursiveComparison()
                    .isEqualTo(entry.getValue().styledCell());

                assertThat(actualClusters.getFillPolygonByCellKey().get(entry.getKey()))
                    .usingRecursiveComparison()
                    .isEqualTo(entry.getValue().paintedExtent());
            }
        }

        @Test
        void tracesEachOwnerAsTheGroupBuilderStoodUpAloneTracesIt() {

            var geometryCache = buildFiveCells();

            var grouping = SystemOwner.mapCellGrouping(
                geometryCache.getSystemKeyByCellKey(),
                OWNERS.ownerBySystemKey());

            var groupBuilder = new ClusterGroupBuilder(
                new MapPaints(STYLING, READING, CONTENT_INPUTS),
                grouping,
                geometryCache.getCellEdgesByCellKey(),
                OWNERS.fillStates());

            var expectedHegemonyGroup = groupBuilder.buildClusterGroup(
                HEGEMONY_OWNER,
                buildCellKeys(HELD_SYSTEM, NEIGHBOUR_SYSTEM));

            var expectedTritachyonGroup = groupBuilder.buildClusterGroup(
                TRITACHYON_OWNER,
                buildCellKeys(RIVAL_SYSTEM));

            var actualClusters = buildClustersOver(geometryCache);

            assertThat(actualClusters.getStyledClusterGroupByOwnerId().get(HEGEMONY))
                .usingRecursiveComparison()
                .isEqualTo(expectedHegemonyGroup);

            assertThat(actualClusters.getStyledClusterGroupByOwnerId().get(TRITACHYON))
                .usingRecursiveComparison()
                .isEqualTo(expectedTritachyonGroup);
        }

        @Test
        void keysEachOwnersClusterGroupByItsGroupingKey() {
            // One entry per owner rather than per system: the two Hegemony systems fuse into the
            // single cluster group their shared key groups them into.
            var geometryCache = buildFiveCells();
            var actualClusters = buildClustersOver(geometryCache);

            assertThat(actualClusters.getStyledClusterGroupByOwnerId())
                .containsOnlyKeys(HEGEMONY, TRITACHYON);

            assertThat(actualClusters.getStyledClusterGroupByOwnerId().get(HEGEMONY).clusters())
                .hasSize(1);
        }

        @Test
        void leavesOutAnOwnerThatBakesNothing() {
            // The rival's cell is grouped but carries no edges, so its owner traces no frontier -
            // and is absent rather than mapped to null, since every reader of the map paints what
            // it finds.
            var geometryCacheMock = mock(CellGeometryCache.class);

            when(geometryCacheMock.getCellEdgesByCellKey())
                .thenReturn(buildKeyedValues(Map.of(HELD_SYSTEM, buildSquareCell(0, 0, CELL_SIDE))));

            when(geometryCacheMock.getSystemKeyByCellKey())
                .thenReturn(buildDrawnSystemKeys(Map.of(
                    HELD_SYSTEM,
                    HELD_SYSTEM,
                    RIVAL_SYSTEM,
                    RIVAL_SYSTEM)));

            var actualClusters = buildClustersOver(geometryCacheMock);

            assertThat(actualClusters.getStyledClusterGroupByOwnerId())
                .containsOnlyKeys(HEGEMONY);
        }
    }

    // The build over the stated cells, under the shared scheme, reading, picks and owners.
    private static OwnerMapClusters buildClustersOver(CellGeometryCache geometryCache) {

        var sourceFake = new OwnerSourceFake();

        return OwnerMapBuilder.buildClusters(
            geometryCache,
            new ViewReading(new OwnerPaintedViewFake(sourceFake), READING, sourceFake),
            CONTENT_INPUTS,
            OWNERS,
            STYLING);
    }

    // The five squares, each drawing as its own star. The held system and its neighbour share the
    // edge at x = 2000; every other square stands alone, enclosed by the reach bound.
    private static CellGeometryCache buildFiveCells() {

        var edgesBySystemId = new LinkedHashMap<String, List<CellEdge>>();

        edgesBySystemId.put(HELD_SYSTEM, buildSquareCellFacing(0, 0, CELL_SIDE, SquareSide.RIGHT, NEIGHBOUR_SYSTEM));
        edgesBySystemId.put(NEIGHBOUR_SYSTEM, buildSquareCellFacing(2000, 0, CELL_SIDE, SquareSide.LEFT, HELD_SYSTEM));
        edgesBySystemId.put(RIVAL_SYSTEM, buildSquareCell(10000, 0, CELL_SIDE));
        edgesBySystemId.put(SETTLED_SYSTEM, buildSquareCell(20000, 0, CELL_SIDE));
        edgesBySystemId.put(EMPTY_SYSTEM, buildSquareCell(30000, 0, CELL_SIDE));

        var systemIdByCellId = new LinkedHashMap<String, String>();

        for (var systemId : edgesBySystemId.keySet()) {
            systemIdByCellId.put(systemId, systemId);
        }

        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellKey())
            .thenReturn(buildKeyedValues(edgesBySystemId));
        when(geometryCacheMock.getSystemKeyByCellKey())
            .thenReturn(buildDrawnSystemKeys(systemIdByCellId));

        return geometryCacheMock;
    }
}
