package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.Bounds;

import kmu.maplayers.base.geometry.SectorFixture;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * One overlay layer painted into a picture a whole sector fits in, read back as how much of it
 * was touched.
 *
 * <p>Shared by the suites that check a layer paints at all, so each asks the same picture at the
 * same scale. Public because the suites that lay a tier's lines sit in the viewer's package,
 * which is the one place allowed to read both constructions.
 */
public final class PaintedPixels {

    // Pixels across the picture. Enough that a line drawn at the map's stroke covers some of
    // them at the scale a whole sector fits in.
    private static final int PICTURE_SIDE = 1000;

    private PaintedPixels() {
    }

    /**
     * @param sector     the fixture whose sites the picture is fitted to
     * @param paintLayer the layer, painting in world space
     * @return how many pixels it touched
     */
    public static int countPaintedPixels(String sector, Consumer<Graphics2D> paintLayer) {

        var around = Bounds.computeEnclosingBounds(SectorFixture.loadSector(sector).getSites());
        var scale = PICTURE_SIDE
            / Math.max(around.maxX() - around.minX(), around.maxY() - around.minY());
        var picture = new BufferedImage(PICTURE_SIDE, PICTURE_SIDE, BufferedImage.TYPE_INT_ARGB);
        var g2 = picture.createGraphics();

        g2.scale(scale, scale);
        g2.translate(-around.minX(), -around.minY());
        paintLayer.accept(g2);
        g2.dispose();

        var painted = 0;

        for (var x = 0; x < PICTURE_SIDE; x++) {
            for (var y = 0; y < PICTURE_SIDE; y++) {

                if (picture.getRGB(x, y) != 0) {
                    painted++;
                }
            }
        }
        return painted;
    }
}
