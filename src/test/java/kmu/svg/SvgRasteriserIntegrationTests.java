package kmu.svg;

import kmlib.math.geometry.Bounds;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SvgRasteriser} against what {@link SvgDrawing} writes: a drawing round-tripped
 * through the file comes back as a picture with each shape where it was drawn, in the colour it
 * was drawn in, whichever notation spelt that colour.
 */
final class SvgRasteriserIntegrationTests {

    // A square world drawn one pixel per unit, so a world point and a pixel are the same pair.
    private static final Bounds WORLD = new Bounds(0, 0, 100, 100);

    private static final int VIEW_WIDTH = 100;

    @TempDir
    private Path folder;

    @Nested
    class RasteriseToPng {

        @Test
        void paintsTheBackdropWhereNothingIsDrawn()
                throws IOException {

            var image = roundTrip(new SvgDrawing(WORLD, VIEW_WIDTH, "#abc"));

            assertThat(new Color(image.getRGB(50, 95)))
                .isEqualTo(new Color(0xaa, 0xbb, 0xcc));
        }

        // Read as HSL, the colour model the notation names. Read as HSB it would come out
        // (0, 128, 0), half the brightness a browser shows.
        @Test
        void readsHslAsHueSaturationAndLightness()
                throws IOException {

            var drawing = new SvgDrawing(WORLD, VIEW_WIDTH, "#000");

            drawing.drawPolygon(buildSquare(10, 10, 40), SvgPaint.fillOnly("hsl(120 100% 50%)"));

            assertThat(new Color(roundTrip(drawing).getRGB(30, 70)))
                .isEqualTo(new Color(0, 255, 0));
        }

        @Test
        void readsAPaleHslShade()
                throws IOException {

            var drawing = new SvgDrawing(WORLD, VIEW_WIDTH, "#000");

            drawing.drawPolygon(buildSquare(10, 10, 40), SvgPaint.fillOnly("hsl(240 50% 75%)"));

            assertThat(new Color(roundTrip(drawing).getRGB(30, 70)))
                .isEqualTo(new Color(159, 159, 223));
        }

        // World y grows upward and image y downward, so a square drawn low in the world lands
        // low in the picture.
        @Test
        void placesAShapeWhereItWasDrawn()
                throws IOException {

            var drawing = new SvgDrawing(WORLD, VIEW_WIDTH, "#000");

            drawing.drawPolygon(buildSquare(60, 10, 30), SvgPaint.fillOnly("#ff0000"));

            var image = roundTrip(drawing);

            assertThat(new Color(image.getRGB(75, 75)))
                .isEqualTo(new Color(255, 0, 0));
            assertThat(new Color(image.getRGB(75, 25)))
                .isEqualTo(new Color(0, 0, 0));
        }
    }

    private BufferedImage roundTrip(SvgDrawing drawing)
            throws IOException {

        var svg = folder.resolve("drawing.svg");
        var png = folder.resolve("drawing.png");

        Files.writeString(svg, drawing.finishDrawing(), StandardCharsets.UTF_8);
        SvgRasteriser.rasteriseToPng(svg, png);

        return ImageIO.read(png.toFile());
    }

    private static List<double[]> buildSquare(double x, double y, double side) {

        return List.of(
            new double[] {x, y},
            new double[] {x + side, y},
            new double[] {x + side, y + side},
            new double[] {x, y + side});
    }
}
