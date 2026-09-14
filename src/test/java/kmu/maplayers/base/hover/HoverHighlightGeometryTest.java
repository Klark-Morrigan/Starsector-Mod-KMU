package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquare;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.buildSquareRun;
import static kmu.maplayers.base.hover.HighlightShapeFixtures.computeTotalTriangleArea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the contract of {@link HoverHighlightGeometry#resolveHighlightFor}:
 *  - the cluster the hovered cell belongs to is what haloes, and the cell clamped to it what
 *    washes,
 *  - a cell no loop encloses still washes, with no halo,
 *  - nothing hovered, or a cell with no drawable shape, resolves nothing,
 *  - a resting cursor resolves once and reuses the answer until the geometry under it changes,
 *    tracked by the identity of what the source handed back rather than of the source itself.
 *
 * <p>Which loop is the cell's cluster, and what the clamp leaves of it, are
 * {@link CellFrontierGeometry}'s own rules and are pinned there; what is here is the hover
 * composing them into a highlight.
 *
 * <p>The fixtures are hand-built squares standing in for cells and traced border loops: the
 * resolve reads only the polygons and runs it is handed, so no shaping, styling, or GL is
 * involved in what it lights up.
 */
final class HoverHighlightGeometryTest {

    private static final String CELL_ID = "A";

    @Nested
    class ResolveHighlightFor {

        @Test
        void aHoveredCellResolvesTheLoopThatEnclosesIt() {

            var loop = buildSquareRun(0, 0, 100);

            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(loop));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .containsExactly(loop);
        }

        @Test
        void aCellFullyInsideItsFrontierWashesItsWholeExtent() {
            // The clip must be a no-op for an interior cell: it washes its full 80x80 area (6400),
            // not a clamped-down piece, so only cells that reach the frontier are ever trimmed.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(6400.0, within(1e-2));
        }

        @Test
        void aCellPokingPastTheFrontierWashesOnlyUpToIt() {
            // The shaped cell reaches past the frontier that encloses its centre - the corner the
            // border's rounding cut, which the raw cell keeps. The wash must clamp to the loop, so
            // the overlap [50,100]x[50,100] (area 2500) washes, not the whole 80x80 cell (6400).
            var sourceFake = readSourceOf(
                buildSquare(50, 50, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(computeTotalTriangleArea(highlight.washTriangles()))
                .isCloseTo(2500.0, within(1e-2));
            assertThat(highlight.washOutline())
                .hasSize(1);
        }

        @Test
        void aCellWithNoCandidateLoopsWashesWithNoHalo() {
            // A cell that fuses into no cluster, or whose cluster traced no border at all: there is
            // no frontier to bloom - but the cell itself is still what the cursor is on.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of());

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .isEmpty();
            assertThat(highlight.washOutline())
                .isNotEmpty();
            assertThat(highlight.washTriangles())
                .isNotEmpty();
        }

        @Test
        void aCellNoCandidateEnclosesWashesWithNoHalo() {
            // Candidates exist but the cell sits outside every one of them, which reads the same
            // as having none: the cell washes and nothing haloes.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(500, 500, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.glowLoops())
                .isEmpty();
            assertThat(highlight.washOutline())
                .isNotEmpty();
        }

        @Test
        void nothingHoveredLightsNothingUp() {

            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, MapHover.NONE);

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void aHoveredCellWithNoDrawableShapeLightsNothingUp() {

            var sourceFake = readSourceOf(
                List.of(),
                List.of(buildSquareRun(0, 0, 100)));

            var highlight = new HoverHighlightGeometry()
                .resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(highlight.isEmpty())
                .isTrue();
        }

        @Test
        void aRestingCursorReusesTheAnswerItAlreadyResolved() {
            // The whole point of the memo: this runs every frame, and re-tracing the same loops
            // sixty times a second for an answer that cannot have changed is pure waste.
            var sourceFake = readSourceOf(
                buildSquare(10, 10, 80),
                List.of(buildSquareRun(0, 0, 100)));

            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));
            var second = geometry.resolveHighlightFor(sourceFake, hoverOf(CELL_ID));

            assertThat(second)
                .isSameAs(first);
        }

        @Test
        void aFreshSourceOverUnchangedGeometryStillReusesTheAnswer() {
            // The memo keys on the extent and the loops the source handed back, not on the source
            // itself - which is what lets a layer wrap its current draw lists afresh each frame
            // without costing a re-trace on every one of them.
            var paintedExtent = buildSquare(10, 10, 80);
            var frontierLoops = List.of(buildSquareRun(0, 0, 100));
            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                readSourceOf(paintedExtent, frontierLoops),
                hoverOf(CELL_ID));

            var second = geometry.resolveHighlightFor(
                readSourceOf(paintedExtent, frontierLoops),
                hoverOf(CELL_ID));

            assertThat(second)
                .isSameAs(first);
        }

        @Test
        void aRebuiltCellResolvesAgainRatherThanTracingAShapeThatIsGone() {

            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(
                readSourceOf(
                    buildSquare(10, 10, 80),
                    List.of(buildSquareRun(0, 0, 100))),
                hoverOf(CELL_ID));

            // An incremental re-shape replaces the cell's extent and its cluster's loops; the
            // retained answer describes geometry the map no longer paints.
            var reshapedLoop = buildSquareRun(0, 0, 60);

            var second = geometry.resolveHighlightFor(
                readSourceOf(
                    buildSquare(5, 5, 50),
                    List.of(reshapedLoop)),
                hoverOf(CELL_ID));

            assertThat(second)
                .isNotSameAs(first);
            assertThat(second.glowLoops())
                .containsExactly(reshapedLoop);
        }
    }

    private static MapHover hoverOf(String cellId) {
        return new MapHover(buildCellKey(cellId), List.of(buildCellKey(cellId)));
    }

    // A source answering for the one hovered cell every case here uses, handing back the very
    // instances it was built with so the memo's identity comparison is exercised as a real
    // layer's answers would exercise it.
    private static HoverHighlightSourceFake readSourceOf(
            List<double[]> paintedExtent,
            List<float[]> frontierLoops) {

        return new HoverHighlightSourceFake(
            buildKeyedValues(Map.of(CELL_ID, paintedExtent)),
            buildKeyedValues(Map.of(CELL_ID, frontierLoops)));
    }

}
