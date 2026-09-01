package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquare;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquareRun;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.computeTotalTriangleArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins where a lit set joins and where it does not: two cells of one cluster wash as one shape
 * with no edge between them, two in different clusters keep the seam the map draws, and a cell no
 * frontier encloses joins nothing. Beside that, the halo tracing those joined outlines rather than
 * a cluster frontier, and the memo that keeps a pointer resting on one set from re-tessellating it
 * every frame.
 */
final class PreviewHighlightGeometryTest {

    private static final String CELL_A_ID = "system-a";

    private static final String CELL_B_ID = "system-b";

    private static final String PREVIEW_KEY = "previewed-set";

    // Two 40x40 cells sharing the edge at x = 50, so a join is visible as one loop over 3200 of
    // area and a refusal to join as two loops of 1600 each.
    private static final double JOINED_AREA = 3200.0;

    @Nested
    class ResolveHighlightFor {

        @Test
        void resolveHighlightForJoinsTwoCellsOfOneClusterIntoOneOutline() {
            // The same loop instance for both cells is what says they fused into one cluster on the
            // map, so the wash drops the edge they share rather than drawing a seam the map is not
            // drawing either.
            var sharedFrontier = buildSquareRun(0, 0, 200);

            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceOf(Map.of(
                    CELL_A_ID, List.of(sharedFrontier),
                    CELL_B_ID, List.of(sharedFrontier))),
                PREVIEW_KEY,
                List.of(CELL_A_ID, CELL_B_ID));

            assertThat(highlight.washOutline())
                .hasSize(1);
            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(JOINED_AREA, within(1e-2));
        }

        @Test
        void resolveHighlightForKeepsTwoCellsOfDifferentClustersApart() {
            // The same two abutting cells, now enclosed by different frontiers: one lit set
            // reaching into two rival clusters, with the map drawing a border between them - so
            // the wash must not paint over it.
            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceOf(Map.of(
                    CELL_A_ID, List.of(buildSquareRun(0, 0, 60)),
                    CELL_B_ID, List.of(buildSquareRun(40, 0, 100)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID, CELL_B_ID));

            assertThat(highlight.washOutline())
                .hasSize(2);
            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(JOINED_AREA, within(1e-2));
        }

        @Test
        void resolveHighlightForNeverJoinsACellNoFrontierEncloses() {
            // A cell that fuses into no cluster is nobody's neighbour: it has no loop identity to
            // share, so it stays its own region even against a cell it abuts.
            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceOf(Map.of(
                    CELL_A_ID, List.of(buildSquareRun(0, 0, 200)),
                    CELL_B_ID, List.of())),
                PREVIEW_KEY,
                List.of(CELL_A_ID, CELL_B_ID));

            assertThat(highlight.washOutline())
                .hasSize(2);
        }

        @Test
        void resolveHighlightForLeavesAHoleWhereLitCellsWallInAnUnlitOne() {
            // The eight cells around one the set does not reach, all in one cluster: they wash as a
            // single region with a hole in it, so the halo traces the ring's outer edge and the
            // edge of the hole - and nothing of the seams between the eight. Filling the hole would
            // claim a cell the set never reached.
            var extentsByCellId = Map.of(
                "system-sw", buildSquare(10, 10, 40),
                "system-s", buildSquare(50, 10, 40),
                "system-se", buildSquare(90, 10, 40),
                "system-w", buildSquare(10, 50, 40),
                "system-e", buildSquare(90, 50, 40),
                "system-nw", buildSquare(10, 90, 40),
                "system-n", buildSquare(50, 90, 40),
                "system-ne", buildSquare(90, 90, 40));

            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceSharingOneFrontier(extentsByCellId, buildSquareRun(0, 0, 200)),
                PREVIEW_KEY,
                extentsByCellId.keySet());

            assertThat(highlight.washOutline())
                .hasSize(2);

            // The ring's eight cells, and not the 1600 of the cell walled in by them.
            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(12800.0, within(1e-2));
        }

        @Test
        void resolveHighlightForBloomsTheJoinedOutlineRatherThanTheFrontier() {
            // The halo says how far the lit set reaches, not who holds the cells under it - so it
            // strokes what the wash traced and never the cluster's own border.
            var frontier = buildSquareRun(0, 0, 200);

            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceOf(Map.of(CELL_A_ID, List.of(frontier))),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            assertThat(highlight.glowLoops())
                .isEqualTo(highlight.washOutline());
            assertThat(highlight.glowLoops())
                .doesNotContain(frontier);
        }

        @Test
        void resolveHighlightForSkipsCellsTheLayerPaintedNothingFor() {
            // A set can name a cell the map is not drawing - one filtered out of the frame it is
            // read against - and that is a cell to pass over, not an empty shape to light.
            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                new HoverHighlightSourceFake(
                    Map.of(CELL_A_ID, buildSquare(10, 10, 40)),
                    Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID, "system-the-map-never-drew"));

            assertThat(highlight.washOutline())
                .hasSize(1);
        }

        @Test
        void resolveHighlightForLightsNothingUpWhenNoPreviewedCellIsDrawn() {

            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                new HoverHighlightSourceFake(Map.of(), Map.of()),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void resolveHighlightForLightsNothingUpWhenNothingIsPreviewed() {
            // The pointer on no row at all reaches this as a null key, which answers nothing rather
            // than throwing at the top of a render pass.
            var highlight = new PreviewHighlightGeometry().resolveHighlightFor(
                readSourceOf(Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                null,
                List.of(CELL_A_ID));

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void resolveHighlightForReusesTheAnswerItAlreadyResolved() {
            // The whole point of the memo: this runs every frame the pointer rests on one row, and
            // re-tessellating a set of cells sixty times a second for an answer that cannot have
            // changed is what would make the preview stick.
            var sourceFake = readSourceOf(Map.of(
                CELL_A_ID, List.of(buildSquareRun(0, 0, 200))));

            var geometry = new PreviewHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, PREVIEW_KEY, List.of(CELL_A_ID));
            var second = geometry.resolveHighlightFor(sourceFake, PREVIEW_KEY, List.of(CELL_A_ID));

            assertThat(second)
                .isSameAs(first);
        }

        @Test
        void resolveHighlightForResolvesAgainWhenARebuildReplacesOneExtent() {
            // An incremental re-shape replaces the cell's extent and its cluster's loops; the
            // retained answer describes geometry the map no longer paints.
            var geometry = new PreviewHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                readSourceOf(Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            var second = geometry.resolveHighlightFor(
                new HoverHighlightSourceFake(
                    Map.of(CELL_A_ID, buildSquare(5, 5, 20)),
                    Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            assertThat(second)
                .isNotSameAs(first);
            assertThat(computeTotalTriangleArea(second.washTriangles()))
                .isCloseTo(400.0, within(1e-2));
        }

        @Test
        void resolveHighlightForResolvesAgainWhenTheBordersAreRetraced() {
            // The cells kept their shapes and the map re-traced its borders around them, which is
            // what a neighbouring change does: the loops the cells were grouped by are gone, so the
            // grouping has to be settled again even though nothing the wash covers moved.
            var paintedExtent = buildSquare(10, 10, 40);
            var geometry = new PreviewHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                new HoverHighlightSourceFake(
                    Map.of(CELL_A_ID, paintedExtent),
                    Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            var second = geometry.resolveHighlightFor(
                new HoverHighlightSourceFake(
                    Map.of(CELL_A_ID, paintedExtent),
                    Map.of(CELL_A_ID, List.of(buildSquareRun(0, 0, 200)))),
                PREVIEW_KEY,
                List.of(CELL_A_ID));

            assertThat(second)
                .isNotSameAs(first);
        }

        @Test
        void resolveHighlightForResolvesAgainWhenTheSetReachesOneMoreDrawnCell() {
            // The same set read against a frame drawing one more of its cells - a filter widening,
            // or a cell the map had nothing to paint for. The retained answer lights fewer cells
            // than the set now reaches, so it cannot stand.
            var sourceFake = readSourceOf(Map.of(
                CELL_A_ID, List.of(buildSquareRun(0, 0, 200))));

            var geometry = new PreviewHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, PREVIEW_KEY, List.of(CELL_A_ID));

            var second = geometry.resolveHighlightFor(
                sourceFake,
                PREVIEW_KEY,
                List.of(CELL_A_ID, CELL_B_ID));

            assertThat(second)
                .isNotSameAs(first);
            assertThat(computeTotalTriangleArea(second.washTriangles()))
                .isCloseTo(JOINED_AREA, within(1e-2));
        }

        @Test
        void resolveHighlightForResolvesAgainForAnotherPreviewedSet() {
            // The key is the memo's first test, so the pointer crossing to another row resolves
            // afresh even where the two sets happen to be drawn from the same geometry.
            var sourceFake = readSourceOf(Map.of(
                CELL_A_ID, List.of(buildSquareRun(0, 0, 200))));

            var geometry = new PreviewHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, PREVIEW_KEY, List.of(CELL_A_ID));
            var second = geometry.resolveHighlightFor(sourceFake, "another-set", List.of(CELL_A_ID));

            assertThat(second)
                .isNotSameAs(first);
        }
    }

    // A source drawing the two abutting cells every join case uses, answering the given candidate
    // loops for each.
    private static HoverHighlightSourceFake readSourceOf(
            Map<String, List<float[]>> frontierLoopsByCellId) {

        return new HoverHighlightSourceFake(
            Map.of(
                CELL_A_ID, buildSquare(10, 10, 40),
                CELL_B_ID, buildSquare(50, 10, 40)),
            frontierLoopsByCellId);
    }

    // A source drawing the given cells, every one of them enclosed by the same loop instance - the
    // shape of a cluster whose cells the set reaches several of.
    private static HoverHighlightSourceFake readSourceSharingOneFrontier(
            Map<String, List<double[]>> paintedExtentByCellId,
            float[] frontier) {

        var frontierLoopsByCellId = new LinkedHashMap<String, List<float[]>>();

        for (var cellId : paintedExtentByCellId.keySet()) {
            frontierLoopsByCellId.put(cellId, List.of(frontier));
        }
        return new HoverHighlightSourceFake(paintedExtentByCellId, frontierLoopsByCellId);
    }
}
