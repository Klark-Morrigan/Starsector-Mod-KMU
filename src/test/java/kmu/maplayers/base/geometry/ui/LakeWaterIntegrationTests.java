package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.NamedRegion;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.v4.Face;
import kmu.maplayers.base.geometry.v4.LakeTier;
import kmu.maplayers.base.geometry.v4.VoidPartition;
import kmu.maplayers.base.geometry.v4.ui.PaintedPixels;
import kmu.maplayers.base.geometry.v4.ui.VoidPartitionOverlay;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
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

    // How many slivers each bridge can close off below the map's resolution: one where each end
    // meets the shore, as for a coast's reach.
    private static final int SLIVERS_PER_BRIDGE = 2;

    // How far two sums of the same water may differ by rounding alone, in units squared.
    private static final double AREA_ROUNDING = 1e-3;

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    @Nested
    class ReadKind {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theCoastLeavesEachLakeOnePieceOfWater(String sector) {
            // A lake's coast divides its water from its bays and nothing else, so with the coast
            // alone each lake is one piece - which is also what says no lake was missed, since
            // a lake read as anything else would leave the count short. A lake whose coast lays
            // no reach, which 491 has at these knobs, is in the count by its ring of cells.
            assertThat(countPieces(LakePartitions.readCoastPartition(sector), sector, LakeTier.Kind.LAKE))
                .isEqualTo(LakePartitions.layContinents(sector).traceCoasts().lakes().size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachReachGivesUpOneBay(String sector) {

            assertThat(countPieces(
                    LakePartitions.readCoastPartition(sector), sector, LakeTier.Kind.MARGIN))
                .isEqualTo(LakePartitions.collectReaches(LakePartitions.layContinents(sector)).size());
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void eachBridgeAddsOnePieceOfLakeAndNoMargin(String sector) {
            // A bridge runs from the lake's frontage to its frontage, which is in front of the
            // coast: it divides the water, and the bays behind the reaches are untouched.
            var continents = LakePartitions.layContinents(sector);
            var bridged = LakePartitions.readBridgedPartition(sector);

            assertThat(countPieces(bridged, sector, LakeTier.Kind.LAKE))
                .isEqualTo(continents.traceCoasts().lakes().size() + continents.layLakeSpans().size());
            assertThat(countPieces(bridged, sector, LakeTier.Kind.MARGIN))
                .isEqualTo(countPieces(
                    LakePartitions.readCoastPartition(sector), sector, LakeTier.Kind.MARGIN));
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theBridgesDivideTheLakesWaterWithoutTakingAny(String sector) {
            // The same water before the bridges and after, bar the slivers the walk does not
            // keep - which is what says the extra pieces are the lake divided rather than water
            // from elsewhere read as the lake.
            var bridgeCount = LakePartitions.layContinents(sector).layLakeSpans().size();
            var sagitta = KNOBS.measureBoundSagitta();

            assertThat(measureArea(LakePartitions.readCoastPartition(sector), sector, LakeTier.Kind.LAKE)
                    - measureArea(LakePartitions.readBridgedPartition(sector), sector, LakeTier.Kind.LAKE))
                .isBetween(
                    -AREA_ROUNDING,
                    SLIVERS_PER_BRIDGE * bridgeCount * sagitta * sagitta + AREA_ROUNDING);
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyPieceTheTierClosedLiesInsideALake(String sector) {
            // Read at the point its name is written, which is inside it by construction: a
            // piece's own middle can sit in the bite of a crescent.
            var lakes = LakePartitions.layContinents(sector).traceCoasts().lakes();

            assertThat(nameLakePieces(LakePartitions.readBridgedPartition(sector), sector))
                .isNotEmpty()
                .allSatisfy(named -> assertThat(lakes)
                    .as("a lake holding %s", named.name())
                    .anySatisfy(lake -> assertThat(PolygonRegions.isPointInsideRing(
                            lake.waterEdge(), named.anchor()[0], named.anchor()[1]))
                        .isTrue()));
        }
    }

    @Nested
    class NamePiece {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void everyPieceTheTierClosedIsNamedApart(String sector) {
            // Two lake pockets on the same cells either side of their narrowest crossing are
            // told apart by the side; a lake piece and a margin on the same cells by the prefix.
            var names = nameLakePieces(LakePartitions.readBridgedPartition(sector), sector)
                .stream()
                .map(NamedRegion::name)
                .toList();

            assertThat(names)
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

        @ParameterizedTest
        @MethodSource(SECTORS)
        void theNamesAreHandedUpWhileTheirSwitchIsOn(String sector) {
            // With every other layer off, so the walk runs for the names alone.
            var settings = buildSettings();

            settings.showLakeNamesV4 = true;

            assertThat(refreshOverlay(sector, settings).collectLakeNames())
                .hasSize(nameLakePieces(LakePartitions.readBridgedPartition(sector), sector).size());
        }
    }

    // How many of a partition's pieces the tier reads as the given kind.
    private static long countPieces(VoidPartition partition, String sector, LakeTier.Kind kind) {

        return partition.collectPieces().stream()
            .filter(piece -> readKind(piece, sector) == kind)
            .count();
    }

    private static double measureArea(VoidPartition partition, String sector, LakeTier.Kind kind) {

        return partition.collectPieces().stream()
            .filter(piece -> readKind(piece, sector) == kind)
            .mapToDouble(Face::measureArea)
            .sum();
    }

    // What the tier reads a piece as, against the coast as laid and the lakes as traced.
    private static LakeTier.Kind readKind(Face piece, String sector) {

        return LakeTier.readKind(
            piece,
            LakePartitions.layCoastWalls(sector),
            LakePartitions.collectTracedLakes(LakePartitions.layContinents(sector)).rings());
    }

    // Every piece the tier closed, named as the window names it.
    private static List<NamedRegion> nameLakePieces(VoidPartition partition, String sector) {

        var fixture = loadFixture(sector);

        return partition.collectPieces().stream()
            .filter(piece -> readKind(piece, sector) != LakeTier.Kind.UNTOUCHED)
            .map(piece -> NamedRegion.nameRegion(
                LakeTier.namePiece(
                    piece, readKind(piece, sector), fixture.getSites(), fixture.getSystemIds()),
                piece.boundary()))
            .toList();
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

        var continents = LakePartitions.layContinents(sector);
        var overlay = new VoidPartitionOverlay(settings);

        overlay.refresh(
            readCellEdges(sector),
            loadFixture(sector),
            LakePartitions.collectTracedLakes(continents),
            continents::layLakeSpans);

        return overlay;
    }
}
