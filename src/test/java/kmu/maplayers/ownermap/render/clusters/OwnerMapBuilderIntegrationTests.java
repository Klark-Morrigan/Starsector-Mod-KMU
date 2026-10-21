package kmu.maplayers.ownermap.render.clusters;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.render.clusters.PaintedCell;
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

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

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
        Set.of(),
        Set.of(buildCellKey(NEIGHBOUR_SYSTEM)),
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
                OWNERS.unfilledSystemKeys(),
                OWNERS.contestedSystemKeys());

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
                .thenReturn(buildKeyedValues(Map.of(HELD_SYSTEM, buildSquareCellAt(0, null))));

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

        edgesBySystemId.put(HELD_SYSTEM, buildSquareCellAt(0, NEIGHBOUR_SYSTEM));
        edgesBySystemId.put(NEIGHBOUR_SYSTEM, buildLeftFacingSquareCellAt(2000, HELD_SYSTEM));
        edgesBySystemId.put(RIVAL_SYSTEM, buildSquareCellAt(10000, null));
        edgesBySystemId.put(SETTLED_SYSTEM, buildSquareCellAt(20000, null));
        edgesBySystemId.put(EMPTY_SYSTEM, buildSquareCellAt(30000, null));

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

    // A 2000-unit square from the given x, counter-clockwise from its bottom-left corner, its right
    // edge facing the given system and every other edge facing open space.
    private static List<CellEdge> buildSquareCellAt(double minX, String rightNeighbourId) {

        var maxX = minX + 2000;

        return List.of(
            buildEdgeFacing(minX, 0, maxX, 0, null),
            buildEdgeFacing(maxX, 0, maxX, 2000, rightNeighbourId),
            buildEdgeFacing(maxX, 2000, minX, 2000, null),
            buildEdgeFacing(minX, 2000, minX, 0, null));
    }

    // The same square with its left edge facing the given system instead.
    private static List<CellEdge> buildLeftFacingSquareCellAt(double minX, String leftNeighbourId) {

        var maxX = minX + 2000;

        return List.of(
            buildEdgeFacing(minX, 0, maxX, 0, null),
            buildEdgeFacing(maxX, 0, maxX, 2000, null),
            buildEdgeFacing(maxX, 2000, minX, 2000, null),
            buildEdgeFacing(minX, 2000, minX, 0, leftNeighbourId));
    }
}
