package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;
import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.base.render.DiagnosticPalette;
import kmu.settings.KmuMapLabelSettings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/**
 * The debug cluster-anchor overlay packed into the runs it draws as: per verdict, one run of band
 * fills, one of band outlines and lane rules, and one of centrelines; and one run of anchor dots per
 * shade.
 *
 * <p>Packed once per standing pass rather than per frame. The overlay is drawn every frame it is on,
 * and walking the placements to cut their bands, rules and lines afresh each time allocated per
 * anchor and issued a draw call per line, for geometry that only a re-fit can move.
 *
 * <p>The two opacity knobs are sampled here too. Changing either one is a settings change, which
 * rebuilds the map and so starts a new pass, so the runs never outlive the opacities they carry.
 *
 * <p>The runs hold world coordinates; the renderer scales them by the frame's zoom as it emits them.
 *
 * @param verdictRuns  each verdict's runs, in draw order, bottom to top
 * @param dotRuns      the anchor dots, one run per shade, in the order the shades first appear
 * @param bandOpacity  the band fills' opacity knob, before the map's fade
 * @param lineOpacity  the outlines', rules', centrelines' and dots' opacity knob, before the map's fade
 */
public record ClusterAnchorRuns(
    List<VerdictRuns> verdictRuns,
    List<DotRun> dotRuns,
    float bandOpacity,
    float lineOpacity) {

    /** The overlay of a pass that placed nothing, which draws nothing. */
    public static final ClusterAnchorRuns NONE = new ClusterAnchorRuns(List.of(), List.of(), 0f, 0f);

    // Below this world girth a band is drawn as its centreline alone: a collapsed fit
    // (thickness zero) and the sub-unit residue of the thickness bisection both have no
    // meaningful area to fill, so only the line shows. A rendering floor, distinct from
    // the geometric degenerate-length guard (kmlib's Limits.MIN_EDGE_LENGTH) a segment's
    // own direction is checked against - the two happen to differ by orders of
    // magnitude, so conflating them would silently change one when tuning the other.
    private static final float MIN_BAND_THICKNESS = 1f;

    // The verdict layers in draw order, bottom to top. Every run a verdict packs reads this one
    // list, so the order a verdict layers in and the shade it grades to cannot drift apart between
    // the bands and the centrelines.
    private static final List<AnchorVerdictLayer> VERDICT_LAYERS = List.of(
        new AnchorVerdictLayer(ClusterAnchor::rejectedAxis, DiagnosticPalette.DISCARDED_COLOUR),
        new AnchorVerdictLayer(ClusterAnchor::unbiasedAxis, DiagnosticPalette.INTERMEDIATE_COLOUR),
        new AnchorVerdictLayer(ClusterAnchor::acceptedAxis, DiagnosticPalette.ACCEPTED_COLOUR));

    /**
     * Packs a pass's placements, at the opacities the player has set now.
     *
     * @param anchors the standing placements
     * @return the overlay's runs, {@link #NONE} where nothing was placed
     */
    public static ClusterAnchorRuns packAnchorRuns(List<ClusterAnchor> anchors) {

        // Answered before the knobs are read: an empty pass draws nothing at either opacity.
        if (anchors.isEmpty()) {
            return NONE;
        }

        return packAnchorRunsAt(
            anchors,
            (float) KmuMapLabelSettings.getMapAnchorBandOpacity(),
            (float) KmuMapLabelSettings.getMapAnchorBandLineOpacity());
    }

    /** @return whether there is nothing to draw: every placement draws its dot, so no dot means none */
    public boolean isEmpty() {
        return dotRuns.isEmpty();
    }

    // The packing itself at the given opacities, apart from the settings read so the runs are
    // arithmetic over the values handed in.
    static ClusterAnchorRuns packAnchorRunsAt(
            List<ClusterAnchor> anchors,
            float bandOpacity,
            float lineOpacity) {

        if (anchors.isEmpty()) {
            return NONE;
        }

        var verdictRuns = new ArrayList<VerdictRuns>(VERDICT_LAYERS.size());

        for (var layer : VERDICT_LAYERS) {
            verdictRuns.add(packVerdictRuns(anchors, layer));
        }

        return new ClusterAnchorRuns(
            verdictRuns,
            packDotRuns(anchors),
            bandOpacity,
            lineOpacity);
    }

    // Adds one packed piece to the run a verdict is accumulating.
    private static void appendRun(List<Float> runFloats, float[] piece) {

        for (var coordinate : piece) {
            runFloats.add(coordinate);
        }
    }

    // A segment as the two vertices one GL_LINES line is drawn from.
    private static float[] flattenSegment(Segment segment) {

        return GlVertexRuns.flattenVertices(
            List.of(segment.readStart(),
            segment.readEnd()));
    }

    // The dots, one run per shade: a dot draws in its cluster's own shade, and the clusters of one
    // owner share theirs, so the runs are as many as the shades rather than the anchors.
    private static List<DotRun> packDotRuns(List<ClusterAnchor> anchors) {

        var dotsByColour = new LinkedHashMap<Color, List<double[]>>();

        for (var anchor : anchors) {

            dotsByColour
                .computeIfAbsent(anchor.colour(), colour -> new ArrayList<>())
                .add(new double[] {anchor.anchorX(), anchor.anchorY()});
        }

        var dotRuns = new ArrayList<DotRun>(dotsByColour.size());

        for (var shade : dotsByColour.entrySet()) {
            dotRuns.add(new DotRun(shade.getKey(), GlVertexRuns.flattenVertices(shade.getValue())));
        }

        return dotRuns;
    }

    // One verdict's runs across every anchor that carries that line. An anchor without the line
    // carries null and adds nothing. A band thinner than the floor, or with no length to take a
    // direction from, adds its centreline alone.
    //
    // The band's box comes from the segment rather than being widened here, because the same box is
    // what a name is kept clear of elsewhere: an overlay drawing its own reading of the footprint
    // would be a diagnostic that agrees with the thing it is meant to check only by coincidence.
    // The lane rules come from the segment for the same reason, so they cannot step across the band
    // any differently from the box they divide.
    //
    // A band's outline goes in as line segments rather than as a loop of its own, so every outline
    // of a verdict strokes in the one run its lane rules go in.
    private static VerdictRuns packVerdictRuns(
            List<ClusterAnchor> anchors,
            AnchorVerdictLayer layer) {

        var fillFloats = new ArrayList<Float>();
        var strokeFloats = new ArrayList<Float>();
        var centrelineFloats = new ArrayList<Float>();

        for (var anchor : anchors) {

            var segment = layer.axisAccessor().apply(anchor);
            if (segment == null) {
                continue;
            }

            appendRun(centrelineFloats, flattenSegment(segment));

            if (anchor.thickness() < MIN_BAND_THICKNESS) {
                continue;
            }

            var corners = segment.computeBandCorners(anchor.thickness());
            if (corners.isEmpty()) {
                continue;
            }

            appendRun(fillFloats, GlVertexRuns.flattenVertices(corners));
            appendRun(strokeFloats, GlVertexRuns.flattenClosedLoopAsSegments(corners));

            for (var divider : segment.computeLaneDividers(anchor.thickness(), anchor.lineCount())) {
                appendRun(strokeFloats, flattenSegment(divider));
            }
        }

        return new VerdictRuns(
            layer.layerColour(),
            GlVertexRuns.packFloats(fillFloats),
            GlVertexRuns.packFloats(strokeFloats),
            GlVertexRuns.packFloats(centrelineFloats));
    }

    /**
     * One verdict's runs, in the verdict's palette shade.
     *
     * @param verdictColour the shade every run of the verdict draws in
     * @param bandFills     the bands' quads, four corners each
     * @param bandStrokes   the bands' outlines and lane rules, as line segments
     * @param centrelines   the axes themselves, as line segments
     */
    public record VerdictRuns(
        Color verdictColour,
        float[] bandFills,
        float[] bandStrokes,
        float[] centrelines) {
    }

    /**
     * The anchor dots of one shade.
     *
     * @param dotColour the shade the dots draw in
     * @param dots      one point per anchor
     */
    public record DotRun(
        Color dotColour,
        float[] dots) {
    }

    /**
     * One verdict's presentation: the accessor that pulls that verdict's axis off an anchor -
     * null on an anchor that carries no such line - paired with the palette shade the whole layer
     * draws in. Pairing the two makes every run share one declaration of what a verdict looks like,
     * instead of each repeating the accessor and the shade per verdict.
     */
    private record AnchorVerdictLayer(
        Function<ClusterAnchor, Segment> axisAccessor,
        Color layerColour) {
    }
}
