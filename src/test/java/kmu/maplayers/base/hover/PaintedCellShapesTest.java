package kmu.maplayers.base.hover;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one behaviour this seam supplies rather than declares: reading a single cell's extent
 * out of the frame's shapes.
 *
 * <p>Both properties matter to a caller. A cell the build dropped has to read as "draws nothing"
 * rather than as a null every reader would have to guard, and a cell that draws has to come back
 * as the very instance the frame holds - the highlight memoises on that identity, so a copy per
 * call would re-trace the cell every frame the cursor rests on it.
 */
final class PaintedCellShapesTest {

    private static final SystemKey DRAWN_CELL_KEY = buildCellKey("corvus");
    private static final SystemKey DROPPED_CELL_KEY = buildCellKey("yma");

    // A cell shape the seam hands back untouched, so only its identity matters here.
    private static final List<double[]> CELL_POLYGON = List.of(
        new double[] {100d, 50d},
        new double[] {300d, 50d},
        new double[] {300d, 250d},
        new double[] {100d, 250d});

    @Nested
    class ResolvePaintedExtentOf {

        @Test
        void resolvePaintedExtentOfReturnsTheFramesOwnShapeForADrawnCell() {
            var shapesFake = new PaintedCellShapesFake(Map.of(DRAWN_CELL_KEY, CELL_POLYGON));

            assertThat(shapesFake.resolvePaintedExtentOf(DRAWN_CELL_KEY))
                .isSameAs(CELL_POLYGON);
        }

        @Test
        void resolvePaintedExtentOfReturnsNothingForACellTheBuildDropped() {
            // A cell that puts no ink on the map is absent from the shapes rather than present
            // with an empty one; folding the two here is what spares every reader the distinction.
            var shapesFake = new PaintedCellShapesFake(Map.of(DRAWN_CELL_KEY, CELL_POLYGON));

            assertThat(shapesFake.resolvePaintedExtentOf(DROPPED_CELL_KEY))
                .isEmpty();
        }
    }

    // The seam over a plain map, so what is exercised is the inherited read and nothing else.
    private record PaintedCellShapesFake(
        Map<SystemKey, List<double[]>> fillPolygonByCellKey) implements PaintedCellShapes {

        @Override
        public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
            return fillPolygonByCellKey;
        }
    }
}
