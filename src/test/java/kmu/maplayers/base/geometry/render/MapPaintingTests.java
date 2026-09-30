package kmu.maplayers.base.geometry.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the one mark that is not a line: a point of frontage as a disc.
 *
 * <p>Painted into a picture at one unit per pixel and read back, since what is pinned is the
 * shape on screen: a disc as wide as a run's stroke, and a disc rather than the square a
 * zero-length stroke with square caps would leave.
 */
class MapPaintingTests {

    // Room round a mark centred in the picture.
    private static final int PICTURE_SIDE = 200;

    private static final double[] CENTRE = {PICTURE_SIDE / 2.0, PICTURE_SIDE / 2.0};

    // Half the stroke, which is the disc's radius; one pixel of slack for the edge.
    private static final double RADIUS = MapLook.SPAN_STROKE / 2;

    private static final double EDGE_SLACK = 1;

    // How far the painted area may sit from the disc's, in percent, for the pixels along the
    // edge that are only partly covered.
    private static final double AREA_SLACK_PERCENT = 3;

    @Nested
    class PaintPointMarks {

        @Test
        void aMarkIsADiscAsWideAsTheStroke() {

            var picture = paintOneMark();
            var painted = 0;

            for (var x = 0; x < PICTURE_SIDE; x++) {
                for (var y = 0; y < PICTURE_SIDE; y++) {

                    if (picture.getRGB(x, y) == 0) {
                        continue;
                    }
                    painted++;

                    assertThat(Math.hypot(x + 0.5 - CENTRE[0], y + 0.5 - CENTRE[1]))
                        .as("a painted pixel at %d,%d", x, y)
                        .isLessThanOrEqualTo(RADIUS + EDGE_SLACK);
                }
            }

            assertThat(painted)
                .isCloseTo(
                    (int) Math.round(Math.PI * RADIUS * RADIUS),
                    org.assertj.core.api.Assertions.withinPercentage(AREA_SLACK_PERCENT));
        }

        @Test
        void noMarksPaintNothing() {

            var picture = new BufferedImage(PICTURE_SIDE, PICTURE_SIDE, BufferedImage.TYPE_INT_ARGB);
            var g2 = picture.createGraphics();

            MapPainting.paintPointMarks(g2, List.of(), Color.WHITE);
            g2.dispose();

            for (var x = 0; x < PICTURE_SIDE; x++) {
                for (var y = 0; y < PICTURE_SIDE; y++) {
                    assertThat(picture.getRGB(x, y)).isZero();
                }
            }
        }
    }

    private static BufferedImage paintOneMark() {

        var picture = new BufferedImage(PICTURE_SIDE, PICTURE_SIDE, BufferedImage.TYPE_INT_ARGB);
        var g2 = picture.createGraphics();

        MapPainting.paintPointMarks(g2, List.of(CENTRE), Color.WHITE);
        g2.dispose();

        return picture;
    }
}
