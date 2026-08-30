package kmu.maplayers.base.geometry.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the drawing rules the map's fills depend on, checked by drawing and
 * reading the pixels back.
 *
 * <p>Asked of the raster rather than of the path, because what these rules are FOR is how the
 * result looks: whether two shapes come out the same shade is a question about the pixels, and
 * a path assertion would pass for a path that draws wrong.
 */
class MapPaintingTest {

    // Drawn at the scale the map is drawn at, because the edge stroke is in map units and is
    // tens of them wide: shapes sized in pixels are all edge, and a sample taken anywhere in
    // one reads the opaque outline rather than the body under test.
    private static final int CANVAS = 600;

    private static final int TRANSLUCENT = 100;

    // Two squares arranged so each has a corner the other does not reach, and a middle they
    // share. Three sample points then cover every case the rule is about, each of them a good
    // many stroke widths clear of the nearest edge.
    private static final List<double[]> LOWER_SQUARE =
        buildSquare(50, 50, 350, 350);
    private static final List<double[]> UPPER_SQUARE =
        buildSquare(250, 250, 550, 550);

    private static final int[] IN_LOWER_ONLY = {150, 150};
    private static final int[] IN_UPPER_ONLY = {450, 450};
    private static final int[] IN_BOTH = {300, 300};

    @Nested
    class PaintMergedRingFills {

        @Test
        void water_two_rings_share_comes_out_the_shade_of_water_one_ring_holds() {

            // The whole point of the sheet. Filled ring by ring, the shared part takes two
            // translucent bodies and comes out darker, so water two constructions both hold
            // reads as a third kind of thing - and the patch moves whenever either is switched
            // off. Filled once over the union, the overlap costs nothing.
            var canvas = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_RGB);
            var g2 = openCanvas(canvas);

            MapPainting.paintMergedRingFills(
                g2,
                List.of(LOWER_SQUARE, UPPER_SQUARE),
                Color.RED,
                TRANSLUCENT,
                Color.RED);

            g2.dispose();

            assertThat(readPixel(canvas, IN_BOTH))
                .as("the shared part is not the shade of a single body")
                .isEqualTo(readPixel(canvas, IN_LOWER_ONLY))
                .isEqualTo(readPixel(canvas, IN_UPPER_ONLY));
        }

        @Test
        void a_ring_is_filled_rather_than_left_as_the_backdrop() {

            // Guards the assertion above from passing on an empty canvas: three equal pixels
            // prove nothing if all three are the colour that was there to begin with.
            var canvas = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_RGB);
            var g2 = openCanvas(canvas);

            MapPainting.paintMergedRingFills(
                g2, List.of(LOWER_SQUARE), Color.RED, TRANSLUCENT, Color.RED);

            g2.dispose();

            assertThat(readPixel(canvas, IN_LOWER_ONLY))
                .as("a filled ring is still the backdrop colour")
                .isNotEqualTo(Color.WHITE.getRGB());
        }
    }

    // A canvas with a known backdrop and no antialiasing, so a pixel reads as exactly the
    // colour that was laid on it rather than as a blend with its neighbours.
    private static Graphics2D openCanvas(BufferedImage canvas) {

        var g2 = canvas.createGraphics();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, CANVAS, CANVAS);

        return g2;
    }

    private static int readPixel(BufferedImage canvas, int[] at) {
        return canvas.getRGB(at[0], at[1]);
    }

    private static List<double[]> buildSquare(
            double fromX,
            double fromY,
            double toX,
            double toY) {

        return List.of(
            new double[] {fromX, fromY},
            new double[] {toX, fromY},
            new double[] {toX, toY},
            new double[] {fromX, toY});
    }
}
