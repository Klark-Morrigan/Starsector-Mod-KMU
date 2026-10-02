package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.NamedRegion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.Face;
import kmu.maplayers.base.geometry.v4.LakePieces;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.VoidPartition;
import kmu.maplayers.base.geometry.v4.ui.PaintedPixels;
import kmu.maplayers.base.geometry.v4.ui.VoidPartitionOverlay;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static kmu.maplayers.base.geometry.v4.SectorPartitions.KNOBS;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.loadFixture;
import static kmu.maplayers.base.geometry.v4.SectorPartitions.readCellEdges;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the lake tier's water and margin, over the real sectors.
 *
 * <p>Here rather than beside {@link LakeTier}, because it reads both constructions: the lakes
 * the pieces are counted against are v3's trace, which is where the coast came from.
 *
 * <p><b>The fill check is the one 1.6 states for every tier</b>: every piece the tier closed is
 * filled, and none twice. The tier reads each piece as one kind, so none can be filled twice;
 * what is pinned is that the kinds come out as the lines say they must - one lake piece per
 * lake and one more per bridge, one margin per reach - that every one of them lies inside a
 * lake, and that each is named apart from every other.
 */
class LakeWaterIntegrationTests {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.ui.LakeWaterIntegrationTests#provideSectorNames";

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class CollectLakePieces {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theCoastLeavesEachLakeOnePieceOfWater(String sector) {
            // A lake's coast divides its water from its bays and nothing else, so with the coast
            // alone each lake is one piece - which is also what says no lake was missed, since
            // a lake read as anything else would leave the count short. A lake whose coast lays
            // no reach, which 491 has at these knobs, is in the count by its ring of cells.
            assertThat(collectLakePieces(LakePartitions.readCoastPartition(sector), sector).water())
                .hasSize(LakePartitions.readTracedLakes(sector).rings().size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachReachGivesUpOneBay(String sector) {

            assertThat(collectLakePieces(LakePartitions.readCoastPartition(sector), sector).margin())
                .hasSize(LakePartitions.readTracedLakes(sector).reaches().size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachBridgeAddsOnePieceOfLakeAndNoMargin(String sector) {
            // A bridge runs from the lake's frontage to its frontage, which is in front of the
            // coast: it divides the water, and the bays behind the reaches are untouched.
            var coast = collectLakePieces(LakePartitions.readCoastPartition(sector), sector);
            var bridged = collectLakePieces(LakePartitions.readBridgedPartition(sector), sector);

            assertThat(bridged.water())
                .hasSize(coast.water().size()
                    + LakePartitions.layContinents(sector).layLakeSpans().size());
            assertThat(bridged.margin())
                .hasSameSizeAs(coast.margin());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theBridgesDivideTheLakesWaterWithoutTakingAny(String sector) {
            // The same water before the bridges and after, bar the slivers the walk does not
            // keep - which is what says the extra pieces are the lake divided rather than water
            // from elsewhere read as the lake.
            var bridgeCount = LakePartitions.layContinents(sector).layLakeSpans().size();

            assertThat(measureWater(LakePartitions.readCoastPartition(sector), sector)
                    - measureWater(LakePartitions.readBridgedPartition(sector), sector))
                .isBetween(
                    -LakePartitions.AREA_ROUNDING,
                    LakePartitions.measureSliverAllowance(bridgeCount)
                        + LakePartitions.AREA_ROUNDING);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyPieceTheTierClosedLiesInsideALake(String sector) {
            // Read at the point its name is written, which is inside it by construction: a
            // piece's own middle can sit in the bite of a crescent.
            var lakes = LakePartitions.layContinents(sector).traceCoasts().lakes();

            assertThat(collectLakePieces(LakePartitions.readBridgedPartition(sector), sector).names())
                .isNotEmpty()
                .allSatisfy(named -> assertThat(lakes)
                    .as("a lake holding %s", named.name())
                    .anySatisfy(lake -> assertThat(PolygonRegions.isPointInsideRing(
                            lake.waterEdge(), named.anchor()[0], named.anchor()[1]))
                        .isTrue()));
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyPieceTheTierClosedIsNamedApart(String sector) {
            // Two lake pockets on the same cells either side of their narrowest crossing are
            // told apart by the side; a lake piece and a margin on the same cells by the prefix.
            assertThat(collectLakePieces(LakePartitions.readBridgedPartition(sector), sector).names())
                .extracting(NamedRegion::name)
                .doesNotHaveDuplicates();
        }
    }

    @Nested
    class PaintLakeFills {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theWaterAndTheMarginEachPaint(String sector) {
            // Through the overlay as the window refreshes it, with the coast and the bridges the
            // window hands over. Two settings, since a layer reads its switch as it paints.
            var waterSettings = buildSettings();
            var marginSettings = buildSettings();

            waterSettings.showLakeWaterV4 = true;
            marginSettings.showLakeMarginV4 = true;

            var waterOnly = refreshOverlay(sector, waterSettings);
            var marginOnly = refreshOverlay(sector, marginSettings);

            assertThat(PaintedPixels.countPaintedPixels(sector, waterOnly::paintLakeFills))
                .isPositive();
            assertThat(PaintedPixels.countPaintedPixels(sector, marginOnly::paintLakeFills))
                .isPositive();
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void withBothOffNothingIsPaintedOrNamed(String sector) {

            var overlay = refreshOverlay(sector, buildSettings());

            assertThat(PaintedPixels.countPaintedPixels(sector, overlay::paintLakeFills))
                .isZero();
            assertThat(overlay.collectLakeNames())
                .isEmpty();
        }
    }

    @Nested
    class CollectLakeNames {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theNamesAreHandedUpWhileTheirSwitchIsOn(String sector) {
            // With every other layer off, so the walk runs for the names alone.
            var settings = buildSettings();

            settings.showLakeNamesV4 = true;

            assertThat(refreshOverlay(sector, settings).collectLakeNames())
                .hasSameSizeAs(collectLakePieces(
                    LakePartitions.readBridgedPartition(sector), sector).names());
        }
    }

    // The tier's own pieces of a partition, read as the window reads them.
    private static LakePieces collectLakePieces(VoidPartition partition, String sector) {

        return LakePieces.collectLakePieces(
            partition.collectPieces(),
            LakePartitions.layCoastWalls(sector),
            LakePartitions.readTracedLakes(sector).rings(),
            loadFixture(sector));
    }

    private static double measureWater(VoidPartition partition, String sector) {

        return collectLakePieces(partition, sector).water().stream()
            .mapToDouble(Face::measureArea)
            .sum();
    }

    // v4 on with the coast and the bridges laid, at the knobs every suite reads the void at,
    // and nothing read off the walk switched on - each case switches on what it asks about.
    private static ViewerSettings buildSettings() {

        var settings = new ViewerSettings();

        settings.parameters = KNOBS;
        settings.showVoidV4 = true;
        settings.showVoidPiecesV4 = false;
        settings.showLakeCoastV4 = true;
        settings.showLakeBridgesV4 = true;

        return settings;
    }

    private static VoidPartitionOverlay refreshOverlay(String sector, ViewerSettings settings) {

        var overlay = new VoidPartitionOverlay(settings);

        overlay.refresh(
            readCellEdges(sector),
            loadFixture(sector),
            LakePartitions.readTracedLakes(sector),
            LakePartitions.layContinents(sector)::layLakeSpans);

        return overlay;
    }
}
