package kmu.maplayers.base.labels;

import kmlib.math.geometry.Segment;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;

import kmu.maplayers.base.labels.anchor.ClusterAnchor;

import java.util.ArrayList;
import java.util.List;

/**
 * The room the drawn cluster names take up read line by line: one world-space box per line of
 * words, sized to what that line measures at the size it renders at.
 *
 * <p>The tighter of the two readings of a name's extent. The other one - a placement's accepted
 * line at its fitted girth - is the chord the search worked with, and a chord is accepted as soon
 * as it is at least as long as the widest wrapped line, so it reaches past the words at both ends
 * by however much it beat them. Read per line instead, what is kept clear of is what is drawn, by
 * construction: the same argument the fitted reading makes, taken one level finer.
 *
 * <p>Per glyph would be the level below and is the wrong one - it cuts a ring into a comb of short
 * arcs between letters, and anything laying one shape into the room left would end up in the gap
 * between two words.
 *
 * <p>The lines and their hang points are not derived here: they come from the same plan the name
 * renderer mints its strings from, so a box and the words inside it cannot be laid out by two
 * different rules. What this adds is each line's measured length and the turn from a centred line
 * into an oriented rectangle.
 */
public final class NameLineBoxes {

    // Derives only; never instantiated.
    private NameLineBoxes() {
    }

    /**
     * The boxes the given placements' drawn lines occupy, measured with the map-label face.
     *
     * @param anchors the placements to read, in any order
     * @return one closed ring of {x, y} corners per drawn line. Empty when the face will not load,
     *         which is also when no name draws at all - a line nothing renders is a line nothing
     *         has to keep clear of
     */
    public static List<List<double[]>> listLineBoxes(List<ClusterAnchor> anchors) {

        var resolvedFont = LabelFonts.loadMapLabelFont();

        return resolvedFont == null
            ? List.of()
            : measureLineBoxes(anchors, new LazyFontMeasurer(resolvedFont));
    }

    // The same boxes over an injected measurement, so the geometry - centre, slant, length, girth
    // to corners - is stated apart from the loaded face it needs a width from.
    static List<List<double[]>> measureLineBoxes(
            List<ClusterAnchor> anchors,
            LineWidthMeasurer measurer) {

        var boxes = new ArrayList<List<double[]>>();

        for (var line : LabelsBuilder.planLabels(anchors)) {

            var corners = computeLineBox(line, measurer);

            if (!corners.isEmpty()) {
                boxes.add(corners);
            }
        }
        return boxes;
    }

    // One drawn line as the rectangle it fills: its measured length along the slant it reads at,
    // one line height across, centred on the point the renderer hangs the string from. The string
    // is drawn centre-anchored on that point, so a box centred there covers the words rather than
    // starting where they do.
    private static List<double[]> computeLineBox(
            LabelsBuilder.LabelPlan line,
            LineWidthMeasurer measurer) {

        var halfLength = measurer.measureLineWidth(line.text(), line.fontHeight()) / 2;
        var slantRadians = Math.toRadians(line.slantDegrees());
        var halfX = Math.cos(slantRadians) * halfLength;
        var halfY = Math.sin(slantRadians) * halfLength;

        return new Segment(
            line.hangX() - halfX,
            line.hangY() - halfY,
            line.hangX() + halfX,
            line.hangY() + halfY)
            .computeBandCorners(line.fontHeight());
    }
}
