package kmu.maplayers.base.labels;

import com.fs.starfarer.api.Global;

import kmlib.math.geometry.Segment;
import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the cached cluster-name labels from the resolved cluster placements. One name
 * block per cluster whose placement search accepted a box: the owner's display name in the
 * lines and at the font size the fit already chose, the lines stacked perpendicular to the
 * accepted line and centred as a block on its midpoint, slanted to its slope, in the
 * owner's bright colour.
 *
 * <p>Reuses the cluster-anchor placements rather than re-fitting: the anchor carries the
 * wrapped lines and font height its box was sized for, so the drawn block matches the
 * fitted footprint by construction and the (costly) placement search runs once. This class
 * adds only the geometry of the stack - each line's own hang point along the block's
 * perpendicular - and the GL strings; it never touches the sector. The label font is the
 * fixed face loaded and cached by {@link LabelFonts} - the same face whose metrics
 * sized the boxes - and a face that failed to load leaves the labels empty.
 *
 * <p>The {@link DrawableString}s own GL buffers, so a rebuild disposes the previous list's
 * strings before minting the new ones; nothing here runs per frame.
 */
public final class LabelsBuilder {
    private static final Logger LOG = Global.getLogger(LabelsBuilder.class);

    // Builds only; never instantiated.
    private LabelsBuilder() {
    }

    // Rebuilds the label list in place from the current placements: disposes the standing
    // strings (they hold GL buffers), clears, and - only when names are drawn at all - mints
    // one string per planned line. Whether they are is the caller's answer, not a setting read
    // here: the placements can be built for the debug overlay alone, and only the layer that
    // asked for them knows whether its names are showing. Profiled and timed on its own so the
    // label build's cost is visible next to the drawables and anchor builds; a failed font
    // load leaves the list empty. Runs at rebuild time only, never per frame.
    public static void rebuildLabels(
            List<Label> labels,
            List<ClusterAnchor> anchors,
            boolean areNamesDrawn) {

        disposeAll(labels);
        labels.clear();
        if (!areNamesDrawn) {
            return;
        }
        var resolvedFont = LabelFonts.loadMapLabelFont();
        if (resolvedFont == null) {
            return;
        }
        var buildStart = System.nanoTime();
        KmuProfiling.getProfiler().measure("politicalMap.buildLabels", () -> {
            // The plan step (each line's text, colour, hang point, slant, and font size)
            // is pure computation; only the mint below touches GL, so the stacking
            // geometry stays a self-contained calculation apart from GL resource creation.
            for (var plan : planLabels(
                    anchors,
                    KmuLunaSettings.getPoliticalMapNameLineSpacing())) {

                var text = resolvedFont.createText(
                        plan.text(),
                        plan.color(),
                        plan.fontHeight());

                text.setAnchor(LazyFont.TextAnchor.CENTER);
                labels.add(new Label(
                        text,
                        plan.color(),
                        plan.hangX(),
                        plan.hangY(),
                        plan.slantDegrees()));
            }
        });
        if (LOG.isDebugEnabled()) {
            LOG.debug("Map layer labels built; lines=" + labels.size()
                    + " ofClusters=" + anchors.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - buildStart));
        }
    }

    /**
     * One planned label line, before any GL string is minted: its text, the colour to draw
     * it in, the world point its centre hangs on, its slant, and the font height
     * (single-line world height) it renders at. The pure output of {@link #planLabels},
     * holding the placement-to-line geometry as plain data, separate from the GL string
     * minting that consumes it.
     */
    public record LabelPlan(
            String text,
            Color color,
            float hangX,
            float hangY,
            float slantDegrees,
            float fontHeight) {
    }

    // Plans every label line: skipping a collapsed placement (dot only, no accepted
    // axis) and any cluster with no wrapped name (an owner whose font or display name
    // did not resolve at fit time), then laying the cluster's lines out as a block -
    // stacked along the accepted line's perpendicular at the given line-spacing multiple,
    // centred on the anchor, first line on the upper side so the block reads top-down.
    // The slant is folded upright first, so the stacking normal is taken from the
    // direction the text actually reads in. Pure - no GL, no font, no sector.
    public static List<LabelPlan> planLabels(
            List<ClusterAnchor> anchors,
            double lineSpacing) {

        var plans = new ArrayList<LabelPlan>(anchors.size());
        for (var anchor : anchors) {
            if (anchor.acceptedAxis() == null || anchor.nameLines().isEmpty()) {
                continue;
            }
            var slantDegrees = computeSlantDegrees(anchor.acceptedAxis());
            planBlockLines(plans, anchor, slantDegrees, lineSpacing);
        }
        return plans;
    }

    // Disposes every standing label's DrawableString so their GL buffers are freed at the
    // moment of rebuild rather than left to LazyLib's finalizer sweep. Public entry for the
    // plugin to call on cleanup as well as the rebuild here.
    public static void disposeAll(List<Label> labels) {
        for (var label : labels) {
            label.text().dispose();
        }
    }

    // Lays one cluster's lines out around its anchor: line centres spaced one
    // line-height-times-spacing apart along the upright slant's "up" perpendicular,
    // the whole stack centred on the anchor point, first line highest. The distance
    // from first to last centre plus one line height is exactly the band thickness the
    // fit reserved, so the block fills the fitted box.
    private static void planBlockLines(
            List<LabelPlan> plans,
            ClusterAnchor anchor,
            float slantDegrees,
            double lineSpacing) {

        var lines = anchor.nameLines();
        var slantRadians = Math.toRadians(slantDegrees);

        // The unit perpendicular on the reading direction's upper side: for an upright
        // slant (|slant| <= 90) its y-component is non-negative, so "up" is screen-up.
        var upX = (float) -Math.sin(slantRadians);
        var upY = (float) Math.cos(slantRadians);
        var lineStep = (float) (anchor.fontHeight() * lineSpacing);

        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            // Offsets run from +((L-1)/2)*step for the first line down to its negative
            // for the last, symmetric about the anchor.
            var offset = ((lines.size() - 1) / 2f - lineIndex) * lineStep;
            plans.add(new LabelPlan(
                    lines.get(lineIndex),
                    anchor.color(),
                    anchor.anchorX() + upX * offset,
                    anchor.anchorY() + upY * offset,
                    slantDegrees, anchor.fontHeight()));
        }
    }

    // The slope of the accepted line in degrees, folded upright so the name reads
    // left-to-right: a line pointing into the left half-plane is reversed first, so a
    // name never renders upside down. The remaining lean (bounded by the anchor's
    // max-slant cap) is kept as every line's slant.
    private static float computeSlantDegrees(Segment axis) {
        var deltaX = axis.endX() - axis.startX();
        var deltaY = axis.endY() - axis.startY();
        if (deltaX < 0.0) {
            deltaX = -deltaX;
            deltaY = -deltaY;
        }
        return (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    }
}
