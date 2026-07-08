package kmu.maplayers.politicalmap.base.render;

import kmlib.math.geometry.Polygons;

import kmu.settings.KmuLunaSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * The two national-border smoothing passes, shared by the production
 * {@link DrawablesBuilder} and the debug {@link DebugBorderTracingBuilder} so both smooth
 * identical geometry from one source.
 *
 * <p>Each pass is honest mechanism - it always does what its name says. The on/off
 * decision is the two Dev-tab gates, and it lives at each caller's call site, not here, so
 * the same loops smooth the same way whether the caller keeps only the final result (the
 * production build) or captures every stage (the debug overlay).
 */
public final class BorderSmoothing {
    private BorderSmoothing() {
    }

    // Splices out of every clean border loop the needle protrusions and inward cusps too
    // thin for rounding to fix (the arc's step-back clamps to their tiny edges), so a
    // rounding pass afterwards runs on clean geometry. Always sands - the caller gates it
    // on the spike-sanding switch.
    public static List<List<double[]>> sandBorderSpikes(List<List<double[]>> loops) {
        var spikeHeight = KmuLunaSettings.getPoliticalMapBorderSpikeHeight();
        var spikeAngle = KmuLunaSettings.getPoliticalMapBorderSpikeAngleRadians();
        var sanded = new ArrayList<List<double[]>>(loops.size());
        for (var loop : loops) {
            sanded.add(Polygons.removeSpikes(loop, spikeHeight, spikeAngle));
        }
        return sanded;
    }

    // Rounds each border loop's corners into arcs with the current corner settings, applied
    // to the resolved envelope rather than a self-crossing inset (a crossing would clip the
    // arc back to a sharp point). Always rounds - the caller gates it on the corner-rounding
    // switch.
    public static List<List<double[]>> roundBorderCorners(List<List<double[]>> loops) {
        var radius = KmuLunaSettings.getPoliticalMapBorderCornerRadius();
        var segments = KmuLunaSettings.getPoliticalMapBorderCornerSegments();
        var chamfer = KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians();
        var rounded = new ArrayList<List<double[]>>(loops.size());
        for (var loop : loops) {
            rounded.add(Polygons.roundCorners(loop, radius, segments, chamfer));
        }
        return rounded;
    }
}
