package kmu.maplayers.base.geometry.ui.overlays.voidpockets;

import kmu.maplayers.base.geometry.CoastRounding;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.LandableFrontages;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * A traced coast held for drawing: its lines as the map strokes them, and the marks read off
 * the same trace that explain where those lines went.
 *
 * <p>Apart from the construction that traces it, because the two are different questions. What
 * a coast IS comes out of the geometry and answers to the knobs; what a picture of one shows -
 * which lines, in what colour, with which diagnostics over them - answers to the switches. Held
 * together, a change to either reads as a change to both.
 *
 * <p>Not driven from the window either. The trace arrives from whatever made it, because a
 * trace is the one thing this cannot do for itself, and everything that happens to a trace
 * afterwards then lives in one place.
 *
 * <p>Colours are passed per call rather than held. They are live settings a panel writes to
 * while the window is open, and a set captured when this was built would go on drawing the map
 * in whatever the colours were at startup.
 */
public final class CoastalPocketsOverlay {

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt it would be drawing marks measured against geometry the rest of the frame is not
    // being drawn from.
    private Coastlines.TracedCoasts traced;

    // The accepted trace's lines with their sharp joins taken off, which is what this actually
    // strokes. Rounded on acceptance rather than while painting: a sector's coasts are tens of
    // thousands of points, and a pass repeated per frame is paid for per frame.
    private CoastRounding.RoundedCoasts rounded = CoastRounding.RoundedCoasts.NONE;

    // Where a straight line could arrive on the accepted trace's cells from the open void.
    // Measured on acceptance for the reason above and more so: the answer costs a sweep over
    // every disc per sampled angle, which is the most expensive thing this overlay draws.
    //
    // Behind its own switch rather than measured whenever a trace arrives, since nothing reads
    // it - a sector nobody has asked the question of should not pay for the answer.
    private List<List<double[]>> landable = List.of();

    public CoastalPocketsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Takes the trace a construction has just made, and forgets whatever came before it.
     *
     * <p>The rounding goes with it. It is a fact about a particular trace, and keeping the old
     * one alongside a new coast would stroke a line the trace never drew.
     *
     * <p>The rounded lines come in beside the trace rather than being made here. A construction
     * that holds a rounding already - because its fills are measured from one - would otherwise
     * pay for a second pass over the same tens of thousands of points, and the two answers could
     * drift apart under different knobs while both looked right.
     *
     * @param traced  the trace, or null where the construction is switched off entirely
     * @param rounded that trace's lines as the map strokes them, one ring for one ring
     */
    public void acceptTrace(
            Coastlines.TracedCoasts traced,
            CoastRounding.RoundedCoasts rounded) {

        this.traced = traced;
        this.rounded = traced == null ? CoastRounding.RoundedCoasts.NONE : rounded;
        this.landable = traced == null || !settings.showLandableFrontage
            ? List.of()
            : LandableFrontages.collectLandableRuns(
                traced, settings.parameters.measureArcSegments());
    }

    /**
     * Whether a trace has been accepted and not since dropped.
     *
     * @return true where there is something to draw
     */
    public boolean hasTrace() {
        return traced != null;
    }

    /**
     * The accepted trace, for the marks a construction draws on its own.
     *
     * @return it, or null where none has been accepted
     */
    public Coastlines.TracedCoasts getTrace() {
        return traced;
    }

    /**
     * Draws each coast, over the top of everything.
     *
     * @param g2     what to draw with
     * @param colour what to draw the line in
     */
    public void paintCoastRings(Graphics2D g2, Color colour) {

        MapPainting.paintLineRings(g2, rounded.coasts(), colour);
    }

    /**
     * Draws each lake's shore, over the top of everything.
     *
     * <p>At the outer shores' own weight and in their colour, because a lake shore IS one of
     * these coasts: drawn any other way, the same line would read as two kinds of thing
     * depending on which side of the land it fell.
     *
     * @param g2     what to draw with
     * @param colour what to draw the line in
     */
    public void paintLakeRings(Graphics2D g2, Color colour) {

        MapPainting.paintLineRings(g2, rounded.lakes(), colour);
    }

    /**
     * Draws the frontages the smoothing left out, on the borders they sit on.
     *
     * <p>Over the coast rather than under it, because the two are read together: what a drop
     * bought is the gap between the stretch and the line that replaced it, and a mark hidden
     * beneath that line says nothing.
     *
     * <p>In one colour whichever shore they sit beside. They are told apart by the line each
     * drop belongs to, and a second colour would imply the drops themselves differ in kind,
     * which they do not.
     *
     * @param g2 what to draw with
     */
    public void paintDroppedStretches(Graphics2D g2) {

        if (!settings.showDroppedStretches) {
            return;
        }

        MapPainting.paintLineRuns(
            g2,
            Coastlines.collectDroppedRuns(traced, settings.parameters.measureArcSegments()),
            settings.droppedStretchColour);
    }

    /**
     * Draws the stretches of border a straight line could arrive at, on the borders they sit on.
     *
     * <p>Beside the coast and the drops rather than instead of either, because the three only
     * mean anything read together: a drop over landable border is detail the rules gave up that
     * something could have used, and coast over border nothing can arrive at is line the map
     * draws where no wall will ever meet it.
     *
     * @param g2 what to draw with
     */
    public void paintLandableFrontage(Graphics2D g2) {

        if (!settings.showLandableFrontage) {
            return;
        }

        MapPainting.paintLineRuns(g2, landable, settings.landableFrontageColour);
    }
}
