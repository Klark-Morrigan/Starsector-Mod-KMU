package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.SectorPartitions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for what the overlay asks for and lays on a refresh, over a real sector.
 *
 * <p>The switches are the subject. Two promises hang on them that nothing else checks: the
 * bridge search, which is the dearest thing the other construction runs, is not asked for while
 * nothing will lay what it finds; and a tier switched off at a refresh lays nothing, so turning
 * it on again shows nothing until the next refresh reads it.
 *
 * <p>Pieces and frontage are off throughout, so no refresh walks the void: what is asserted is
 * which lines the tiers took up, which the walk has no say in.
 */
class VoidPartitionOverlayIntegrationTests {

    // Any one of the fixtures: what is asserted is which switches the overlay reads, and every
    // sector reads them alike.
    private static final String SECTOR = SectorFixture.listSectorNames().get(0);

    // No lake at all: what these cases assert is which switches the overlay reads.
    private static final LakeTier.TracedLakes NO_LAKES =
        new LakeTier.TracedLakes(List.of(), List.of());

    @Nested
    class Refresh {

        @Test
        void theBridgeSearchIsNotAskedForWhileTheBridgesAreOff() {

            var asks = new AtomicInteger();

            buildOverlay(true, false).refresh(
                SectorPartitions.readCellEdges(SECTOR),
                SectorFixture.loadSector(SECTOR),
                NO_LAKES,
                countAsks(asks));

            assertThat(asks)
                .hasValue(0);
        }

        @Test
        void theBridgeSearchIsAskedForOnceWhileTheBridgesAreOn() {

            var asks = new AtomicInteger();

            buildOverlay(true, true).refresh(
                SectorPartitions.readCellEdges(SECTOR),
                SectorFixture.loadSector(SECTOR),
                NO_LAKES,
                countAsks(asks));

            assertThat(asks)
                .hasValue(1);
        }

        @Test
        void nothingIsAskedForWhileV4IsSetDown() {
            // The master over the whole construction outranks the bridges' own switch.
            var asks = new AtomicInteger();
            var settings = buildSettings(false, true);

            new VoidPartitionOverlay(settings).refresh(
                SectorPartitions.readCellEdges(SECTOR),
                SectorFixture.loadSector(SECTOR),
                NO_LAKES,
                countAsks(asks));

            assertThat(asks)
                .hasValue(0);
        }
    }

    @Nested
    class PaintLakeBridges {

        @Test
        void theBridgesLaidAtTheRefreshAreDrawn() {

            var settings = buildSettings(true, true);
            var overlay = new VoidPartitionOverlay(settings);

            refreshWithOneLine(overlay);

            assertThat(countPaintedPixels(overlay::paintLakeBridges))
                .isPositive();
        }

        @Test
        void bridgesOffAtTheRefreshLayNothingToDraw() {
            // Switched on after the refresh, the tier has nothing to show: the refresh took its
            // lines out, which is what keeps them from dividing pieces nobody can see.
            var settings = buildSettings(true, false);
            var overlay = new VoidPartitionOverlay(settings);

            refreshWithOneLine(overlay);
            settings.showLakeBridgesV4 = true;

            assertThat(countPaintedPixels(overlay::paintLakeBridges))
                .isZero();
        }
    }

    @Nested
    class PaintLakeCoast {

        @Test
        void theCoastLaidAtTheRefreshIsDrawn() {

            var settings = buildSettings(true, false);
            var overlay = new VoidPartitionOverlay(settings);

            settings.showLakeCoastV4 = true;
            refreshWithOneLine(overlay);

            assertThat(countPaintedPixels(overlay::paintLakeCoast))
                .isPositive();
        }

        @Test
        void aCoastOffAtTheRefreshLaysNothingToDraw() {
            // The bridges' promise made for the coast, which reads its own switch.
            var settings = buildSettings(true, false);
            var overlay = new VoidPartitionOverlay(settings);

            refreshWithOneLine(overlay);
            settings.showLakeCoastV4 = true;

            assertThat(countPaintedPixels(overlay::paintLakeCoast))
                .isZero();
        }
    }

    @Nested
    class PaintLandableFrontage {

        @Test
        void theFrontageTheCoastLeavesIsDrawn() {
            // With the coast laid the frontage is read off the walked void, runs and points:
            // this is the one case here that walks the sector, and the one that pins the layer
            // painting at all.
            var settings = buildSettings(true, false);
            var overlay = new VoidPartitionOverlay(settings);

            settings.showLakeCoastV4 = true;
            settings.showLandableFrontageV4 = true;
            refreshWithOneLine(overlay);

            assertThat(countPaintedPixels(overlay::paintLandableFrontage))
                .isPositive();
        }
    }

    private static VoidPartitionOverlay buildOverlay(boolean isV4Shown, boolean areBridgesShown) {
        return new VoidPartitionOverlay(buildSettings(isV4Shown, areBridgesShown));
    }

    // The construction's master and the bridges' switch as given, and nothing that walks the
    // void switched on.
    private static ViewerSettings buildSettings(boolean isV4Shown, boolean areBridgesShown) {

        var settings = new ViewerSettings();

        settings.showVoidV4 = isV4Shown;
        settings.showVoidPiecesV4 = false;
        settings.showLandableFrontageV4 = false;
        settings.showLakeBridgesV4 = areBridgesShown;

        return settings;
    }

    private static Supplier<List<CellGap>> countAsks(AtomicInteger asks) {

        return () -> {
            asks.incrementAndGet();
            return List.of();
        };
    }

    // One line between the first two cells, handed over as the only reach and the only bridge,
    // each end a cell radius off its own site towards the other's - which is where a real
    // line's ends stand.
    private static void refreshWithOneLine(VoidPartitionOverlay overlay) {

        var sites = SectorFixture.loadSector(SECTOR).getSites();
        var from = sites.get(0);
        var to = sites.get(1);
        var radius = SectorPartitions.KNOBS.cellRadius();
        var start = stepTowards(from, to, radius);
        var end = stepTowards(to, from, radius);
        var line = new CellGap(0, 1, start, end, Points.computeDistance(start, end));

        overlay.refresh(
            SectorPartitions.readCellEdges(SECTOR),
            SectorFixture.loadSector(SECTOR),
            new LakeTier.TracedLakes(List.of(line), List.of()),
            () -> List.of(line));
    }

    private static double[] stepTowards(double[] from, double[] to, double distance) {

        var length = Points.computeDistance(from, to);

        return new double[] {
            from[0] + (to[0] - from[0]) * distance / length,
            from[1] + (to[1] - from[1]) * distance / length};
    }

    // One layer painted into a picture the sector fits in, and how many pixels it touched.
    private static int countPaintedPixels(Consumer<Graphics2D> paintLayer) {
        return PaintedPixels.countPaintedPixels(SECTOR, paintLayer);
    }
}
