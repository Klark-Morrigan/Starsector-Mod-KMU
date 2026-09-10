package kmu.svg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Turns an SVG this package wrote into a PNG, so the shape it describes can be looked at.
 *
 * <p>An SVG is the right thing to keep: diffable, deterministic, and it opens in a browser.
 * What it is not is viewable by anything that only understands raster images, and the answer
 * reached for every time somebody needs one is a second, bespoke drawing routine - two drawers
 * that can disagree about the very shape under test.
 *
 * <p>So this converts rather than redraws. It reads back exactly what {@link SvgDrawing} emits
 * - the root, one backdrop rectangle, one flipping group, polygons, polylines, paths of
 * straight segments under either fill rule, and circles - and that narrowness is the point: a
 * general SVG renderer
 * would be a dependency and a surface, where a reader of these two files together can check in
 * a minute that the picture shows what was drawn.
 *
 * <p>Anything outside that subset is ignored rather than guessed at, so a drawing that grows a
 * feature this cannot render comes out visibly missing it instead of subtly wrong.
 */
public final class SvgRasteriser {

    // The writer's root carries the world size in its viewBox and the pixel size in its width
    // and height, which between them are the whole transform from one to the other.
    private static final Pattern ROOT =
        Pattern.compile("<svg[^>]*?width=\"(\\d+)\"[^>]*?height=\"(\\d+)\"");

    private static final Pattern VIEW_BOX =
        Pattern.compile("viewBox=\"0 0 ([\\d.]+) ([\\d.]+)\"");

    private static final Pattern GROUP_TRANSFORM = Pattern.compile(
        "<g transform=\"translate\\(([-\\d.]+) ([-\\d.]+)\\) scale\\(([-\\d.]+) ([-\\d.]+)\\)\"");

    private static final Pattern BACKDROP = Pattern.compile("<rect[^>]*?fill=\"([^\"]+)\"");
    private static final Pattern ELEMENT =
        Pattern.compile("<(polygon|polyline|path|circle)\\b([^>]*)>");

    private static final Pattern POINTS = Pattern.compile("points=\"([^\"]*)\"");
    private static final Pattern PATH_DATA = Pattern.compile(" d=\"([^\"]*)\"");
    private static final Pattern FILL_RULE = Pattern.compile("fill-rule=\"([^\"]+)\"");

    // The two rules the writer merges rings under. Named so the path builder can be handed
    // the rule read off the element rather than assume one, since the two give different
    // pictures of the same sub-paths wherever they overlap.
    private static final String EVEN_ODD = "evenodd";
    private static final Pattern FILL = Pattern.compile("[^-]fill=\"([^\"]+)\"");
    private static final Pattern FILL_OPACITY = Pattern.compile("fill-opacity=\"([^\"]+)\"");
    private static final Pattern STROKE = Pattern.compile("[^-]stroke=\"([^\"]+)\"");
    private static final Pattern STROKE_WIDTH = Pattern.compile("stroke-width=\"([^\"]+)\"");
    private static final Pattern CENTRE_X = Pattern.compile("cx=\"([-\\d.]+)\"");
    private static final Pattern CENTRE_Y = Pattern.compile("cy=\"([-\\d.]+)\"");
    private static final Pattern RADIUS = Pattern.compile(" r=\"([-\\d.]+)\"");

    private static final Pattern HSL =
        Pattern.compile("hsl\\((\\d+) (\\d+)% (\\d+)%\\)");

    private static final String NO_PAINT = "none";
    private static final int OPAQUE_ALPHA = 255;
    private static final int SHORT_HEX_LENGTH = 4;
    private static final int HEX_RADIX = 16;
    private static final double PERCENT = 100.0;

    // Hue is a turn in degrees where the SVG writes it and a fraction of a turn where Java
    // reads it, so the one converts to the other.
    private static final double DEGREES_PER_TURN = 360.0;

    private static final int BYTE_MASK = 0xff;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int PAIRED_HEX_DIGITS = 2;

    // A radius either side of the centre makes the box a circle is drawn in.
    private static final int DIAMETERS = 2;

    // Mid grey, for a colour notation this does not know. Visible and obviously not chosen,
    // so an unrendered shade reads as one rather than as a decision.
    private static final int FALLBACK_GREY = 0x80;

    // The group's transform reads as four numbers in one pattern - a shift in x and y, then a
    // scale in each - and a colour as three. Named because a bare group index at the point of
    // use says nothing about which of them it is.
    private static final int SHIFT_X = 1;
    private static final int SHIFT_Y = 2;
    private static final int SCALE_X = 3;
    private static final int SCALE_Y = 4;

    private static final int HUE = 1;
    private static final int SATURATION = 2;
    private static final int BRIGHTNESS = 3;

    private SvgRasteriser() {
    }

    /**
     * Renders one SVG to a PNG beside it.
     *
     * @param source the SVG to read
     * @param target where to write the PNG
     */
    public static void rasteriseToPng(Path source, Path target) {

        var svg = readSvg(source);
        var root = matchOrFail(ROOT, svg, source);

        var image = new java.awt.image.BufferedImage(
            Integer.parseInt(root.group(1)),
            Integer.parseInt(root.group(2)),
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        var g2 = image.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(readColour(findAttribute(BACKDROP, svg, "#000"), OPAQUE_ALPHA));
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());

        applyGroupTransform(g2, svg, image.getWidth(), image.getHeight());
        paintElements(g2, svg);

        g2.dispose();

        try {
            Files.createDirectories(target.getParent());
            ImageIO.write(image, "png", target.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
    }

    // Two transforms, applied in the order the SVG itself applies them. The viewBox maps the
    // world's extent onto the pixel canvas; the group then shifts the world to the origin and
    // flips y, because world y grows upward and image y grows downward.
    private static void applyGroupTransform(
            Graphics2D g2,
            String svg,
            int pixelWidth,
            int pixelHeight) {

        var box = VIEW_BOX.matcher(svg);

        if (box.find()) {

            g2.scale(
                pixelWidth / Double.parseDouble(box.group(1)),
                pixelHeight / Double.parseDouble(box.group(2)));
        }

        var group = GROUP_TRANSFORM.matcher(svg);

        if (!group.find()) {
            return;
        }

        g2.translate(
            Double.parseDouble(group.group(SHIFT_X)),
            Double.parseDouble(group.group(SHIFT_Y)));

        g2.scale(
            Double.parseDouble(group.group(SCALE_X)),
            Double.parseDouble(group.group(SCALE_Y)));
    }

    private static void paintElements(Graphics2D g2, String svg) {

        var elements = ELEMENT.matcher(svg);

        while (elements.find()) {

            var attributes = elements.group(2);
            var shape = buildShape(elements.group(1), attributes);

            if (shape == null) {
                continue;
            }
            paintShape(g2, shape, attributes);
        }
    }

    private static java.awt.Shape buildShape(String element, String attributes) {

        return switch (element) {
            case "polygon" -> buildRun(findAttribute(POINTS, attributes, ""), true);
            case "polyline" -> buildRun(findAttribute(POINTS, attributes, ""), false);
            case "path" -> buildPath(
                findAttribute(PATH_DATA, attributes, ""),
                findAttribute(FILL_RULE, attributes, EVEN_ODD));
            case "circle" -> buildCircle(attributes);
            default -> null;
        };
    }

    private static void paintShape(Graphics2D g2, java.awt.Shape shape, String attributes) {

        var fill = findAttribute(FILL, attributes, NO_PAINT);

        if (!NO_PAINT.equals(fill)) {

            var opacity = Double.parseDouble(findAttribute(FILL_OPACITY, attributes, "1"));

            g2.setColor(readColour(fill, (int) Math.round(opacity * OPAQUE_ALPHA)));
            g2.fill(shape);
        }

        var stroke = findAttribute(STROKE, attributes, NO_PAINT);

        if (!NO_PAINT.equals(stroke)) {

            g2.setStroke(new BasicStroke(
                Float.parseFloat(findAttribute(STROKE_WIDTH, attributes, "1"))));

            g2.setColor(readColour(stroke, OPAQUE_ALPHA));
            g2.draw(shape);
        }
    }

    // Closed for a polygon and open for a polyline, which is the only difference between the
    // two: a polyline joined back up would draw an edge the writer never wrote.
    private static Path2D buildRun(String points, boolean isClosed) {

        var path = new Path2D.Double();
        var pairs = points.trim().split("\\s+");

        for (var index = 0; index < pairs.length; index++) {

            var pair = pairs[index].split(",");

            if (pair.length < 2) {
                continue;
            }
            var x = Double.parseDouble(pair[0]);
            var y = Double.parseDouble(pair[1]);

            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        if (isClosed) {
            path.closePath();
        }
        return path;
    }

    // Only the three commands the writer emits: move, line and close. A path that used
    // anything else would come out missing that stretch, which is visible, rather than
    // rendered by a guess, which is not.
    //
    // Under the rule the element names, because the writer merges rings two ways and they
    // disagree exactly where rings overlap: even-odd cancels the overlap and non-zero keeps
    // it. Read off the element rather than assumed, so a merged fill comes out as the writer
    // meant it rather than as whichever rule this happened to default to.
    private static Path2D buildPath(String data, String fillRule) {

        var path = new Path2D.Double(
            EVEN_ODD.equals(fillRule) ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        var tokens = data.trim().split("\\s+");
        var index = 0;

        while (index < tokens.length) {

            var token = tokens[index];

            if (token.startsWith("Z")) {
                path.closePath();
                index++;
                continue;
            }
            if (!token.startsWith("M") && !token.startsWith("L")) {
                index++;
                continue;
            }

            var x = Double.parseDouble(token.substring(1));
            var y = Double.parseDouble(tokens[index + 1]);

            if (token.charAt(0) == 'M') {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
            index += PAIRED_HEX_DIGITS;
        }
        return path;
    }

    private static java.awt.Shape buildCircle(String attributes) {

        var centreX = Double.parseDouble(findAttribute(CENTRE_X, attributes, "0"));
        var centreY = Double.parseDouble(findAttribute(CENTRE_Y, attributes, "0"));
        var radius = Double.parseDouble(findAttribute(RADIUS, attributes, "0"));

        // Java2D takes the box a circle sits in rather than its centre and radius. Done here
        // rather than borrowed from the map's own drawing helpers, because reaching into those
        // for two subtractions would tie a reader of SVG to a package about political maps.
        return new Ellipse2D.Double(
            centreX - radius, centreY - radius, radius * DIAMETERS, radius * DIAMETERS);
    }

    // The three notations the writer uses: a short hex, a long hex, and the hsl the owner
    // palette is spread around. Anything else falls back to grey rather than throwing, so one
    // unexpected colour costs a wrong shade instead of the whole picture.
    private static Color readColour(String notation, int alpha) {

        var hsl = HSL.matcher(notation);

        if (hsl.matches()) {

            var colour = Color.getHSBColor(
                (float) (Integer.parseInt(hsl.group(HUE)) / DEGREES_PER_TURN),
                (float) (Integer.parseInt(hsl.group(SATURATION)) / PERCENT),
                (float) (Integer.parseInt(hsl.group(BRIGHTNESS)) / PERCENT));

            return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
        }

        if (!notation.startsWith("#")) {
            return new Color(FALLBACK_GREY, FALLBACK_GREY, FALLBACK_GREY, alpha);
        }

        var digits = notation.length() == SHORT_HEX_LENGTH
            ? expandShortHex(notation)
            : notation.substring(1);

        var packed = Integer.parseInt(digits, HEX_RADIX);

        return new Color(
            packed >> RED_SHIFT & BYTE_MASK,
            packed >> GREEN_SHIFT & BYTE_MASK,
            packed & BYTE_MASK,
            alpha);
    }

    private static String expandShortHex(String notation) {

        var expanded = new StringBuilder();

        for (var index = 1; index < notation.length(); index++) {
            expanded.append(notation.charAt(index)).append(notation.charAt(index));
        }
        return expanded.toString();
    }

    private static String findAttribute(Pattern pattern, String text, String fallback) {

        var found = pattern.matcher(text);
        return found.find() ? found.group(1) : fallback;
    }

    private static Matcher matchOrFail(Pattern pattern, String text, Path source) {

        var found = pattern.matcher(text);

        if (!found.find()) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "%s is not an SVG this package wrote", source));
        }
        return found;
    }

    private static String readSvg(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + source, e);
        }
    }
}
