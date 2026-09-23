package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.v4.BareVoid;
import kmu.maplayers.base.geometry.v4.Face;
import kmu.maplayers.base.geometry.v4.PieceShaper;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for turning pieces of void into fillable regions, over the real sectors.
 *
 * <p><b>Drawability is the check, and it needs a real sector to mean anything.</b> The fault
 * this sequence exists to prevent is a ring that crosses itself: it fills to something other
 * than its outline and strokes a line through its own interior. No hand-built fixture produces
 * one, because a shape simple enough to write out is convex enough to inset cleanly. A sector's
 * pieces are all necks, and the raw miter crosses itself in a quarter of them - so this asserts
 * on the sectors and reports the raw count beside it, which is what says the check still has
 * something to catch.
 *
 * <p>Under {@link EdgeInsetRule#NOWHERE} the same sequence runs over the partition itself: no
 * edge moves, nothing can fold, and the pieces must all survive. That is the half which would
 * catch a resolve that dropped bodies it should have kept.
 */
class PieceRegionsIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.v4.ui.PieceRegionsIntegrationTest#provideSectorNames";

    private static final SectorGeometryParameters KNOBS =
        SectorGeometryParameters.createDefaults();

    // Both passes on, as the window runs them: what is asserted is the sequence the viewer
    // actually draws through, not a quieter one chosen to make the assertion easier.
    private static final BorderSmoothingStyle SMOOTHING = new BorderSmoothingStyle(
        new SpikeSandingStyle(true, 40, Math.toRadians(35)),
        new CornerRoundingStyle(true, 200, 4, Math.toRadians(40), Math.toRadians(150)));

    private static final EdgeInset INSET =
        new EdgeInset(EdgeInsetRule.AT_EVERY_BORDER, KNOBS.borderInset());

    private static final EdgeInset NO_INSET =
        new EdgeInset(EdgeInsetRule.NOWHERE, KNOBS.borderInset());

    static Stream<String> provideSectorNames() {
        return SectorFixture.listSectorNames().stream();
    }

    private static List<Face> readPieces(String sectorName) {

        var fixture = SectorFixture.loadSector(sectorName);

        return BareVoid.readBareVoid(
            fixture.buildCellEdgesBySystemKey(KNOBS), fixture.getSites(), KNOBS)
            .collectPieces();
    }

    @Nested
    class CollectDrawableRegions {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void noRingOfAnyRegionCrossesItself(String sectorName) {

            var regions = PieceRegions.collectDrawableRegions(
                readPieces(sectorName), INSET, KNOBS.miterSpikeLimit(), SMOOTHING);

            assertThat(regions).isNotEmpty();

            for (var region : regions) {
                for (var ring : region.toRings()) {

                    assertThat(PolygonRegions.countSelfCrossings(ring))
                        .as("a ring of a region on %s", sectorName)
                        .isZero();
                }
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void theRawShapingCrossesItselfSoTheCleanupHasSomethingToDo(String sectorName) {
            // The other half of the test above. Were the miter already clean on these fixtures,
            // that assertion would pass over a sequence that had been deleted.
            var crossed = 0;

            for (var piece : readPieces(sectorName)) {

                var shaped = PieceShaper.shapePiece(piece, INSET, KNOBS.miterSpikeLimit());

                if (!shaped.outerRing().isEmpty()
                        && PolygonRegions.countSelfCrossings(shaped.outerRing()) > 0) {

                    crossed++;
                }
            }
            assertThat(crossed)
                .as("pieces of %s whose raw miter crosses itself", sectorName)
                .isPositive();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyRegionEnclosesSomething(String sectorName) {
            // A region with an empty outer ring is one the sequence lost hold of rather than
            // one it dropped: a folded piece contributes no region at all.
            var regions = PieceRegions.collectDrawableRegions(
                readPieces(sectorName), INSET, KNOBS.miterSpikeLimit(), SMOOTHING);

            for (var region : regions) {

                assertThat(PolygonRegions.computeSignedArea(region.outerRing()))
                    .as("the area a region of %s encloses", sectorName)
                    .isPositive();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void withNothingInsetEveryPieceStillDraws(String sectorName) {
            // Nothing moves, so nothing can fold, and every piece has to come back. A resolve
            // that dropped bodies would show here and nowhere else.
            var pieces = readPieces(sectorName);

            var regions = PieceRegions.collectDrawableRegions(
                pieces, NO_INSET, KNOBS.miterSpikeLimit(), SMOOTHING);

            assertThat(regions)
                .as("regions of %s with nothing inset", sectorName)
                .hasSizeGreaterThanOrEqualTo(pieces.size());
        }
    }
}
