package kmu.maplayers.base.geometry.walls;

import kmlib.math.geometry.DirectedLine;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.WallKind;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for why each offered wall was or was not laid, read off one laying.
 *
 * <p>Two discs a reach apart rim to rim, and a bridge along the line between their sites: the
 * one wall the laying has no reason to refuse. Each case then adds the one thing that refuses
 * it.
 */
class ChordRefusalsTests {

    private static final double REACH = 100;
    private static final double CHANNEL = 10;

    private static final double[] LEFT_SITE = {0, 0};
    private static final double[] RIGHT_SITE = {400, 0};

    // Sixty above where the bridge leaves the left disc, so it covers that end and both edges of
    // the mouth there, and reaches nowhere near the right disc.
    private static final double[] SITE_OVER_THE_LEFT_END = {100, 60};

    private static final DiscUnion TWO_DISCS = new DiscUnion(List.of(LEFT_SITE, RIGHT_SITE), REACH);

    private static final DiscUnion THREE_DISCS =
        new DiscUnion(List.of(LEFT_SITE, RIGHT_SITE, SITE_OVER_THE_LEFT_END), REACH);

    @Nested
    class DescribeRefusal {

        @Test
        void aWallNothingStopsIsLaid() {

            var bridge = buildBridge();
            var refusals = ChordRefusals.judgeEveryRefusal(
                TWO_DISCS,
                new Walls(List.of(bridge), CHANNEL));

            assertThat(refusals.describeRefusal(bridge))
                .isEqualTo(new ChordRefusal(RefusalReason.LAID, null));
        }

        @Test
        void aWallWhoseEndAThirdCellCoversIsOffTheBoundaryNamingThatCell() {

            var bridge = buildBridge();
            var refusal = ChordRefusals.judgeEveryRefusal(
                    THREE_DISCS,
                    new Walls(List.of(bridge), CHANNEL))
                .describeRefusal(bridge);

            assertThat(refusal.reason())
                .isEqualTo(RefusalReason.OFF_BOUNDARY);

            assertThat(refusal.detail())
                .startsWith("on cell 0, ")
                .endsWith(" inside cell 2");
        }

        @Test
        void aSecondWallOnTheSameMouthIsCrowdedOutNamingTheFirst() {

            var first = buildBridge();
            var second = buildBridge();
            var refusals = ChordRefusals.judgeEveryRefusal(
                TWO_DISCS,
                new Walls(List.of(first, second), CHANNEL));

            assertThat(refusals.describeRefusal(first).reason())
                .isEqualTo(RefusalReason.LAID);
            assertThat(refusals.describeRefusal(second))
                .isEqualTo(new ChordRefusal(RefusalReason.CROWDED_OUT, "BRIDGE 0-1 on cell 0"));
        }

        @Test
        void aWallTheLayingWasNeverHandedWasNotOffered() {
            // The same line built a second time is a different wall to the laying: it answers
            // about the walls it was handed.
            var offered = buildBridge();
            var refusals = ChordRefusals.judgeEveryRefusal(
                TWO_DISCS,
                new Walls(List.of(offered), CHANNEL));

            assertThat(refusals.describeRefusal(buildBridge()))
                .isEqualTo(new ChordRefusal(RefusalReason.NOT_OFFERED, null));
        }
    }

    // From the left disc's rim to the right disc's, along the line between the sites.
    private static Chord buildBridge() {

        return new Chord(
            0,
            1,
            new DirectedLine(100, 0, 200, 0),
            WallKind.BRIDGE);
    }
}
