package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.ribbon.CellRibbon;
import kmu.maplayers.base.ribbon.RibbonBand;
import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.base.ribbon.overlay.RibbonPathVerdict;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the invariant the cursor read leans on, which stays invisible until a hover lands on the
 * wrong cell: everything a cell carries is written and dropped together with the ring it was
 * fitted to.
 *
 * <p>A band and its diagnostic path are each cut to one particular ring, so a cell re-shaped or
 * dropped while keeping either would draw the last shape's work inside this shape's cell. Each gets
 * its own case over each of the two writes, which is what says both writes reach the band store's
 * one drop. That the drop takes the traced rings too is the band store's own suite's to pin.
 */
final class PaintedCellStoreTests {

    // The cells the stores are exercised over, each keyed as the cut keys a cell.
    private static final SystemKey SYSTEM_CELL = buildCellKey("system");
    private static final SystemKey KEPT_CELL = buildCellKey("kept");
    private static final SystemKey DROPPED_CELL = buildCellKey("dropped");

    // One draw record of each form, for the cases about how the store parts them. Neither is drawn,
    // so neither needs a paint.
    private static final StyledCell.FusedCell FUSED_CELL =
        new StyledCell.FusedCell(new float[0], null, 0f);

    private static final StyledCell.LoneCell LONE_CELL_RECORD =
        new StyledCell.LoneCell(new float[0], new float[0], null, null, 0f);

    @Nested
    class PutPaintedCell {

        @Test
        void recordsTheDrawRecordAndItsShapeUnderTheSameSystem() {

            var store = new PaintedCellStore();
            var paintedCell = buildPaintedCellOn(buildSquareRing());

            store.putPaintedCell(SYSTEM_CELL, paintedCell);

            assertThat(store.getStyledCellByCellKey())
                .containsOnlyKeys(SYSTEM_CELL);
            assertThat(store.getStyledCellByCellKey()
                .get(SYSTEM_CELL)).isSameAs(paintedCell.styledCell());

            assertThat(store.getFillPolygonByCellKey())
                .containsOnlyKeys(SYSTEM_CELL);
            assertThat(store.getFillPolygonByCellKey()
                .get(SYSTEM_CELL)).isSameAs(paintedCell.paintedExtent());
        }

        @Test
        void replacesBothHalvesWhenACellIsReshaped() {
            // The drift the paired write exists to prevent: a re-shaped cell must not keep
            // answering the cursor with the extent it had before it was re-shaped.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));

            var reshaped = buildPaintedCellOn(buildTriangleRing());

            store.putPaintedCell(SYSTEM_CELL, reshaped);

            assertThat(store.getStyledCellByCellKey().get(SYSTEM_CELL))
                .isSameAs(reshaped.styledCell());
            assertThat(store.getFillPolygonByCellKey().get(SYSTEM_CELL))
                .isSameAs(reshaped.paintedExtent());
        }

        @Test
        void dropsTheBandLaidInsideTheShapeItReplaces() {
            // A band is triangles fitted to one particular ring, so a re-shaped cell keeping its
            // band would draw the last shape's stripe inside this shape's cell. The band pass
            // lays a fresh one afterwards; what must not survive is the old one.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRibbonStore().putCellRibbon(SYSTEM_CELL, buildAnyRibbon());
            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildTriangleRing()));

            assertThat(store.getRibbonStore().getRibbonByCellKey())
                .isEmpty();
        }

        @Test
        void dropsTheBandPathTracedInsideTheShapeItReplaces() {
            // The diagnostic goes with the band for the same reason the band goes: a path traced
            // in the last shape drawn over this one would report the overlay's own staleness as
            // the cell's geometry, which is the one thing a diagnostic must not do.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRibbonStore().putCellRibbonPath(SYSTEM_CELL, buildAnyRibbonPath());
            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildTriangleRing()));

            assertThat(store.getRibbonStore().getRibbonPathByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class RemovePaintedCell {

        @Test
        void dropsTheDrawRecordAndItsShapeTogether() {
            // A cell that draws nothing can be hovered no more than it can be seen, so the
            // shape must go with the draw record rather than linger as a phantom hit cluster.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getStyledCellByCellKey())
                .isEmpty();
            assertThat(store.getFillPolygonByCellKey())
                .isEmpty();
        }

        @Test
        void leavesEveryOtherCellStanding() {

            var store = new PaintedCellStore();

            store.putPaintedCell(DROPPED_CELL, buildPaintedCellOn(buildSquareRing()));
            store.putPaintedCell(KEPT_CELL, buildPaintedCellOn(buildTriangleRing()));
            store.removePaintedCell(DROPPED_CELL);

            assertThat(store.getStyledCellByCellKey())
                .containsOnlyKeys(KEPT_CELL);
            assertThat(store.getFillPolygonByCellKey())
                .containsOnlyKeys(KEPT_CELL);
        }

        @Test
        void dropsThePresenceBandWithTheCell() {
            // A band is drawn inside a cell, so a cell that stops drawing takes its band with it -
            // otherwise a dropped cell keeps painting a floating stripe of triangles.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRibbonStore().putCellRibbon(SYSTEM_CELL, buildAnyRibbon());
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getRibbonStore().getRibbonByCellKey())
                .isEmpty();
        }

        @Test
        void dropsTheBandPathWithTheCell() {
            // A path is a ring around a cell, so a cell that stops drawing leaves the overlay
            // marking out a shape nothing paints any more.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRibbonStore().putCellRibbonPath(SYSTEM_CELL, buildAnyRibbonPath());
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getRibbonStore().getRibbonPathByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class GetStyledCellByCellKey {

        @Test
        void refusesAWriteThatBypassesTheStore() {
            // Every write has to go through the store, which is what tells the parted lists that
            // the cells moved: a record put straight into the map would leave the emission drawing
            // the cells as they stood before it.
            var store = new PaintedCellStore();

            assertThatThrownBy(() -> store
                    .getStyledCellByCellKey()
                    .put(SYSTEM_CELL, FUSED_CELL))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetFusedCells {

        @Test
        void listsTheFusedCellsInTheOrderTheyWereRecorded() {

            var store = new PaintedCellStore();
            var secondFusedCell = new StyledCell.FusedCell(new float[0], null, 0f);

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));
            store.putPaintedCell(DROPPED_CELL, new PaintedCell(LONE_CELL_RECORD, buildSquareRing()));
            store.putPaintedCell(KEPT_CELL, new PaintedCell(secondFusedCell, buildSquareRing()));

            assertThat(store.getFusedCells())
                .containsExactly(FUSED_CELL, secondFusedCell);
        }

        @Test
        void keepsTheSameListWhileNoCellIsWritten() {
            // The emission reads the cells several times a frame and only a rebuild writes, so a
            // fresh parting per read would be the per-frame work this list exists to save.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));

            assertThat(store.getFusedCells())
                .isSameAs(store.getFusedCells());
        }

        @Test
        void partsTheCellsAgainAfterAWrite() {

            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));
            store.getFusedCells();
            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(LONE_CELL_RECORD, buildSquareRing()));

            assertThat(store.getFusedCells())
                .isEmpty();
        }

        @Test
        void partsTheCellsAgainAfterARemoval() {

            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));
            store.getFusedCells();
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getFusedCells())
                .isEmpty();
        }
    }

    @Nested
    class GetLoneCells {

        @Test
        void listsOnlyTheLoneCells() {

            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));
            store.putPaintedCell(KEPT_CELL, new PaintedCell(LONE_CELL_RECORD, buildSquareRing()));

            assertThat(store.getLoneCells())
                .containsExactly(LONE_CELL_RECORD);
        }

        @Test
        void partsTheCellsAgainAfterAWrite() {

            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(FUSED_CELL, buildSquareRing()));
            store.getLoneCells();
            store.putPaintedCell(SYSTEM_CELL, new PaintedCell(LONE_CELL_RECORD, buildSquareRing()));

            assertThat(store.getLoneCells())
                .containsExactly(LONE_CELL_RECORD);
        }
    }

    // A painted cell whose record only has to exist, against the ring the case is about: these
    // stores are exercised on which ring is held and what goes when it is replaced, never on ink.
    private static PaintedCell buildPaintedCellOn(List<double[]> paintedExtent) {
        return OwnerMapClusterFixtures.createPlaceholderPaintedCellOn(paintedExtent);
    }

    // Two distinct rings, so a case that swaps one for the other is caught by identity. Nothing
    // here reads the geometry, only which instance is held against a cell.
    private static List<double[]> buildSquareRing() {

        return List.of(
            new double[] {0, 0},
            new double[] {1, 0},
            new double[] {1, 1},
            new double[] {0, 1});
    }

    private static List<double[]> buildTriangleRing() {

        return List.of(
            new double[] {0, 0},
            new double[] {2, 0},
            new double[] {0, 2});
    }

    // A band that draws something, which is all these cases ask of it: what distinguishes it from
    // CellRibbon.NONE is that it has a run at all, not what the run looks like.
    private static CellRibbon buildAnyRibbon() {

        return new CellRibbon(List.of(
            new RibbonBand(Color.WHITE, new float[] {0, 0, 1, 0, 1, 1})));
    }

    // A path that draws something, which is all these cases ask of it: what distinguishes it from
    // CellRibbonPath.NONE is that it has a stretch at all, not where that stretch runs.
    private static CellRibbonPath buildAnyRibbonPath() {

        return new CellRibbonPath(
            List.of(new float[] {0, 0, 1, 0, 1, 1}),
            List.of(),
            new float[] {0, 0},
            RibbonPathVerdict.LAID_AT_PAD);
    }
}
