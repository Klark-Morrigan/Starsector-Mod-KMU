package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbon;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonPath;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonBand;
import kmu.maplayers.politicalmap.base.render.ribbon.RibbonPathVerdict;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the invariant the cursor read leans on, which stays invisible until a hover lands on the
 * wrong cell: everything a cell carries is written and dropped together with the ring it was
 * fitted to.
 *
 * <p>A band, its diagnostic path and the traced ring behind it are each cut to one particular ring,
 * so a cell re-shaped or dropped while keeping any of them would draw the last shape's work inside
 * this shape's cell. Each of the three gets its own case over each of the two writes, since one
 * shared step drops them: a case per store is what says so for the store it names rather than for
 * whichever one that step happens to reach.
 */
final class PaintedCellStoreTest {

    // The cells the stores are exercised over, each keyed as the cut keys a cell.
    private static final SystemKey SYSTEM_CELL = buildCellKey("system");
    private static final SystemKey KEPT_CELL = buildCellKey("kept");
    private static final SystemKey DROPPED_CELL = buildCellKey("dropped");

    @Nested
    class PutPaintedCell {

        @Test
        void putPaintedCellRecordsTheDrawRecordAndItsShapeUnderTheSameSystem() {

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
        void putPaintedCellReplacesBothHalvesWhenACellIsReshaped() {
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
        void putPaintedCellDropsTheBandLaidInsideTheShapeItReplaces() {
            // A band is triangles fitted to one particular ring, so a re-shaped cell keeping its
            // band would draw the last shape's stripe inside this shape's cell. The band pass
            // lays a fresh one afterwards; what must not survive is the old one.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.putCellRibbon(SYSTEM_CELL, buildAnyRibbon());

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildTriangleRing()));

            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }

        @Test
        void putPaintedCellDropsTheBandPathTracedInsideTheShapeItReplaces() {
            // The diagnostic goes with the band for the same reason the band goes: a path traced
            // in the last shape drawn over this one would report the overlay's own staleness as
            // the cell's geometry, which is the one thing a diagnostic must not do.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.putCellRibbonPath(SYSTEM_CELL, buildAnyRibbonPath());

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildTriangleRing()));

            assertThat(store.getRibbonPathByCellKey())
                .isEmpty();
        }

        @Test
        void putPaintedCellDropsTheRingTracedInsideTheShapeItReplaces() {
            // The whole of what makes the traced ring safe to keep. It is held under no key and no
            // revision, so a cell served a ring traced inside the shape it used to have would lay
            // its band round a cell that is no longer there - and this write is what rules that
            // out, by construction rather than by the band pass remembering to ask.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRingPathCache().putRingPath(SYSTEM_CELL, RingPath.nothingLeftToTrace());

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildTriangleRing()));

            assertThat(store.getRingPathCache().findRingPathOf(SYSTEM_CELL))
                .isNull();
        }
    }

    @Nested
    class RemovePaintedCell {

        @Test
        void removePaintedCellDropsTheDrawRecordAndItsShapeTogether() {
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
        void removePaintedCellLeavesEveryOtherCellStanding() {

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
        void removePaintedCellDropsThePresenceBandWithTheCell() {
            // A band is drawn inside a cell, so a cell that stops drawing takes its band with it -
            // otherwise a dropped cell keeps painting a floating stripe of triangles.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.putCellRibbon(SYSTEM_CELL, buildAnyRibbon());
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }

        @Test
        void removePaintedCellDropsTheBandPathWithTheCell() {
            // A path is a ring around a cell, so a cell that stops drawing leaves the overlay
            // marking out a shape nothing paints any more.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.putCellRibbonPath(SYSTEM_CELL, buildAnyRibbonPath());
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getRibbonPathByCellKey())
                .isEmpty();
        }

        @Test
        void removePaintedCellDropsTheTracedRingWithTheCell() {
            // A cell that stops drawing has no shape for a ring to have been traced inside, so the
            // ring goes with it - a cell drawn again later is cut afresh and is owed a fresh walk.
            var store = new PaintedCellStore();

            store.putPaintedCell(SYSTEM_CELL, buildPaintedCellOn(buildSquareRing()));
            store.getRingPathCache().putRingPath(SYSTEM_CELL, RingPath.nothingLeftToTrace());
            store.removePaintedCell(SYSTEM_CELL);

            assertThat(store.getRingPathCache().findRingPathOf(SYSTEM_CELL))
                .isNull();
        }
    }

    @Nested
    class PutCellRibbon {

        @Test
        void putCellRibbonRecordsABandUnderTheCellThatDrawsIt() {

            var store = new PaintedCellStore();
            var ribbon = buildAnyRibbon();

            store.putCellRibbon(SYSTEM_CELL, ribbon);

            assertThat(store.getRibbonByCellKey())
                .containsOnlyKeys(SYSTEM_CELL);
            assertThat(store.getRibbonByCellKey().get(SYSTEM_CELL))
                .isSameAs(ribbon);
        }

        @Test
        void putCellRibbonKeepsABandlessCellOutOfTheBandMap() {
            // Most of the sector draws no band, so the map is kept sparse rather than parallel to
            // the cells: the render pass walks the cells that draw one and no others.
            var store = new PaintedCellStore();

            store.putCellRibbon(SYSTEM_CELL, CellRibbon.NONE);

            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }

        @Test
        void putCellRibbonClearsAStandingBandWhenTheCellStopsDrawingOne() {
            // The re-bake that finds nothing left to report - a rival's last colony in the system
            // has gone. Without the clear, the cell would keep drawing the band it no longer earns.
            var store = new PaintedCellStore();

            store.putCellRibbon(SYSTEM_CELL, buildAnyRibbon());
            store.putCellRibbon(SYSTEM_CELL, CellRibbon.NONE);

            assertThat(store.getRibbonByCellKey())
                .isEmpty();
        }
    }

    @Nested
    class PutCellRibbonPath {

        @Test
        void putCellRibbonPathRecordsAPathUnderTheCellItWasTracedIn() {

            var store = new PaintedCellStore();
            var ribbonPath = buildAnyRibbonPath();

            store.putCellRibbonPath(SYSTEM_CELL, ribbonPath);

            assertThat(store.getRibbonPathByCellKey())
                .containsOnlyKeys(SYSTEM_CELL);
            assertThat(store.getRibbonPathByCellKey().get(SYSTEM_CELL))
                .isSameAs(ribbonPath);
        }

        @Test
        void putCellRibbonPathClearsAStandingPathWhenTheOverlayIsSwitchedOff() {
            // The bake hands over nothing for every cell while the overlay is off, and that is
            // the whole of how it is switched off: nothing else clears what an earlier pass laid,
            // so a path surviving here would leave the map ringed with a diagnostic the player
            // has turned off.
            var store = new PaintedCellStore();

            store.putCellRibbonPath(SYSTEM_CELL, buildAnyRibbonPath());
            store.putCellRibbonPath(SYSTEM_CELL, CellRibbonPath.NONE);

            assertThat(store.getRibbonPathByCellKey())
                .isEmpty();
        }
    }


    // A painted cell whose record only has to exist, against the ring the case is about: these
    // stores are exercised on which ring is held and what goes when it is replaced, never on ink.
    private static PaintedCell buildPaintedCellOn(List<double[]> paintedExtent) {
        return PoliticalMapTerritoryFixtures.createPlaceholderPaintedCellOn(paintedExtent);
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
