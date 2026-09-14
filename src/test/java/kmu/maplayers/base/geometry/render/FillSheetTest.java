package kmu.maplayers.base.geometry.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the sheet the map's fills are drawn as, checked by drawing and reading the
 * pixels back.
 *
 * <p>Asked of the raster rather than of the path, because what these rules are FOR is how the
 * result looks: whether two shapes come out the same shade is a question about the pixels, and
 * a path assertion would pass for a path that draws wrong.
 */
class FillSheetTest {

    // Drawn at the scale the map is drawn at, because the edge stroke is in map units and is
    // tens of them wide: shapes sized in pixels are all edge, and a sample taken anywhere in
    // one reads the opaque outline rather than the body under test.
    private static final int CANVAS = 600;

    private static final int TRANSLUCENT = 100;

    // Two squares arranged so each has a corner the other does not reach, and a middle they
    // share. Three sample points then cover every case the union rule is about, each of them a
    // good many stroke widths clear of the nearest edge.
    private static final List<double[]> LOWER_SQUARE = buildSquare(50, 50, 350, 350);
    private static final List<double[]> UPPER_SQUARE = buildSquare(250, 250, 550, 550);

    private static final int[] IN_LOWER_ONLY = {150, 150};
    private static final int[] IN_UPPER_ONLY = {450, 450};
    private static final int[] IN_BOTH = {300, 300};

    @Nested
    class AddRings {

        @Test
        void whatTwoRingsShareComesOutTheShadeOfWhatOneOfThemHolds() {

            // The whole point of the sheet. Filled ring by ring, the shared part takes two
            // translucent bodies and comes out darker, so what two layers both hold reads as a
            // third kind of thing - and the patch moves whenever either is switched off.
            var canvas = paintSheet(sheet -> sheet.addRings(
                List.of(LOWER_SQUARE, UPPER_SQUARE)));

            assertThat(readPixel(canvas, IN_BOTH))
                .as("the shared part is not the shade of a single body")
                .isEqualTo(readPixel(canvas, IN_LOWER_ONLY))
                .isEqualTo(readPixel(canvas, IN_UPPER_ONLY));
        }

        @Test
        void ringsThatArrivedWoundOppositeWaysAreStillOneBody() {

            // The rings come from traces that had no reason to agree on a direction, and under
            // the non-zero rule two that disagree cancel where they overlap - a hole through
            // the fill exactly where two layers meet. So the sheet decides each ring's
            // direction rather than taking it as it came.
            var canvas = paintSheet(sheet -> sheet.addRings(
                List.of(LOWER_SQUARE, windTheOtherWay(UPPER_SQUARE))));

            assertThat(readPixel(canvas, IN_BOTH))
                .as("the overlap of two opposed rings cancelled instead of filling")
                .isEqualTo(readPixel(canvas, IN_LOWER_ONLY));
        }

        @Test
        void aRingIsFilledRatherThanLeftAsTheBackdrop() {

            // Guards the assertions above from passing on an empty canvas: equal pixels prove
            // nothing if they are all the colour that was there to begin with.
            var canvas = paintSheet(sheet -> sheet.addRing(LOWER_SQUARE));

            assertThat(readPixel(canvas, IN_LOWER_ONLY))
                .as("a filled ring is still the backdrop colour")
                .isNotEqualTo(Color.WHITE.getRGB());
        }
    }

    // The composed sheet painted on a fresh canvas, which is the whole of what every test sets
    // up. The backdrop is known and antialiasing is off, so a pixel reads as exactly the colour
    // that was laid on it rather than as a blend with its neighbours.
    private static BufferedImage paintSheet(Consumer<FillSheet> compose) {

        var canvas = new BufferedImage(CANVAS, CANVAS, BufferedImage.TYPE_INT_RGB);
        var g2 = canvas.createGraphics();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, CANVAS, CANVAS);

        var sheet = new FillSheet();

        compose.accept(sheet);
        sheet.paint(g2, FillLook.paintedIn(Color.RED, TRANSLUCENT));

        g2.dispose();

        return canvas;
    }

    private static int readPixel(BufferedImage canvas, int[] at) {
        return canvas.getRGB(at[0], at[1]);
    }

    private static List<double[]> windTheOtherWay(List<double[]> ring) {

        var reversed = new ArrayList<>(ring);

        Collections.reverse(reversed);

        return reversed;
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
