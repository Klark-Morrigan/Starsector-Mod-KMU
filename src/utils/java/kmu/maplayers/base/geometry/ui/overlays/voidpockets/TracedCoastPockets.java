package kmu.maplayers.base.geometry.ui.overlays.voidpockets;

import kmu.maplayers.base.geometry.CoastPockets;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.maplayers.base.geometry.WalledPocket;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.ui.settings.ViewerSettings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * A traced coast and the void it shut in: everything the rival constructions do identically.
 *
 * <p>Two constructions are on screen to be compared, and they differ in exactly one thing -
 * how the coast is traced. Everything after that trace is one answer to one question: what a
 * coast reach shuts in is the same question however the coast offering it was arrived at. Each
 * construction holding its own copy of those steps is how the pair stops being a comparison:
 * the moment one copy is tuned and the other is not, a difference on screen is a difference in
 * the drawing rather than in the coasts, and looking at them side by side answers nothing.
 *
 * <p>So a construction owns its trace and hands it here, and what happens to a trace afterwards
 * lives in one place. What is left in each construction's own file is the part that makes it
 * that construction - which is what a reader comes to those files to find.
 *
 * <p>Colours are passed per call rather than held. They are live settings a panel writes to
 * while the window is open, and a set captured when this was built would go on drawing the map
 * in whatever the colours were at startup.
 */
public final class TracedCoastPockets {

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt either would be drawing marks measured against geometry the rest of the frame
    // is not being drawn from.
    private Coastlines.TracedCoasts traced;
    private List<WalledPocket> pockets = List.of();

    public TracedCoastPockets(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Takes the trace a construction has just made, and forgets whatever came before it.
     *
     * <p>The pockets go with it. They are a fact about a particular trace, and keeping the old
     * ones alongside a new coast would draw the void one line shut in underneath another.
     *
     * @param traced the trace, or null where the construction is switched off entirely
     */
    public void acceptTrace(Coastlines.TracedCoasts traced) {

        this.traced = traced;
        this.pockets = List.of();
    }

    /**
     * Works out the void the accepted trace shut in.
     *
     * <p>Asked for separately rather than done on acceptance, because a construction may want
     * its coast on screen without its fill - and the pockets are the expensive half.
     *
     * @param fixture the sector the trace was made in
     */
    public void findPockets(SectorFixture fixture) {

        pockets = CoastPockets.findCoastPockets(
            traced,
            fixture.getOwnerBySite(),
            new VoidPockets.PocketRules(
                settings.parameters, settings.resolvePocketShaping()));
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
     * The void found behind the accepted trace, for the faults a construction checks it for.
     *
     * @return the pockets, empty until {@link #findPockets} has been asked for
     */
    public List<WalledPocket> getPockets() {
        return pockets;
    }

    /**
     * Draws the void the coast shut in, beneath the cells.
     *
     * <p>Under them, so a stray edge reads as the mistake it is rather than painting over the
     * shape it got wrong. Unlike the coast LINE, which goes over everything: the line is a
     * proposal to be judged against the arcs underneath it, and a fill of the space it closed
     * off hides none of them.
     *
     * @param g2   what to draw with
     * @param fill what to fill the pockets with
     * @param edge what to outline them in
     */
    public void paintPocketFills(Graphics2D g2, Color fill, Color edge) {

        MapPainting.paintPocketFills(g2, pockets, fill, settings.voidFillOpacity, edge);
    }

    /**
     * Draws each coast, over the top of everything.
     *
     * @param g2     what to draw with
     * @param colour what to draw the line in, which is what tells the two constructions apart
     */
    public void paintCoastRings(Graphics2D g2, Color colour) {

        MapPainting.paintLineRings(g2, Coastlines.collectCoastRings(traced), colour);
    }

    /**
     * Draws the frontages the smoothing left out, on the borders they sit on.
     *
     * <p>Over the coast rather than under it, because the two are read together: what a drop
     * bought is the gap between the stretch and the line that replaced it, and a mark hidden
     * beneath that line says nothing.
     *
     * <p>In one colour for both constructions. They are told apart by the line each drop sits
     * beside, and a second colour would imply the drops themselves differ in kind, which they
     * do not.
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
}
