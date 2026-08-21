package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * The smoothed outer edge, drawn: one line round each run of connected cells.
 *
 * <p>Its own class alongside {@link VoidBridgesOverlay}, and switched on and off like it,
 * because it is a proposal about the same map rather than a settled part of it - and the
 * only way to judge a smoothed edge is against the scalloped one it replaces, both on
 * screen at once.
 *
 * <p>Drawn as a line over everything rather than as a fill under it, for that reason. A fill
 * would hide the arcs the line is meant to be compared with, which is the one thing looking
 * at it is for. The outline it hands back is an ordinary closed ring, so filling it later
 * needs nothing this class does not already produce.
 */
final class CoastlinesOverlay {

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt any of these would be drawing marks measured against geometry the rest of the
    // frame is not being drawn from.
    private List<CoastCrossings.Penetration> penetrations = List.of();
    private List<WalledPocket> pockets = List.of();
    private List<CoastPocketFaults.Spill> spills = List.of();

    // Held from the last trace so the marks are drawn against the same discs the coast was
    // measured against, rather than against whatever the sliders have been moved to since.
    private Coastlines.TracedCoasts traced;

    CoastlinesOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Traces the smoothed edges again, or drops them when the overlay is switched off.
     *
     * <p>The pockets the coast shut in come off the same trace, so the line and the fills
     * inset from it cannot be built under two different settings within one frame.
     *
     * @param fixture the sector to trace in
     */
    void refresh(SectorFixture fixture) {

        // Traced while either half of it is on screen. The line and the void it shuts in are
        // one construction seen twice, and the pockets come off this same trace - so a frame
        // showing one of them has already paid for both.
        if (!settings.showCoastline && !settings.showCoastalFill) {

            traced = null;
            penetrations = List.of();
            pockets = List.of();
            spills = List.of();
            return;
        }

        traced = Coastlines.traceSectorCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveCoastRules());

        penetrations = CoastCrossings.findVisibleCrossings(
            traced,
            MapLook.RING_STROKE);

        pockets = CoastPockets.findCoastPockets(
            traced,
            fixture.getOwnerBySite(),
            new VoidPockets.PocketRules(
                settings.parameters,
                settings.resolvePocketShaping()));

        spills = CoastPocketFaults.findSpills(
            pockets,
            Coastlines.collectCoastRings(traced));
    }

    /**
     * Draws the void the coast shut in, beneath the cells.
     *
     * <p>Under them like every other void fill, so a stray edge reads as the mistake it is
     * rather than painting over the shape it got wrong. Unlike the coast LINE, which goes over
     * everything: the line is a proposal to be judged against the arcs underneath it, and a
     * fill of the space it closed off hides none of them.
     *
     * @param g2 what to draw with
     */
    void paintPocketFills(Graphics2D g2) {

        if (!settings.showCoastalFill) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.FILL_EDGE_STROKE));

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {

                MapPainting.paintFilledShape(
                    g2,
                    MapPainting.buildPath(outline),
                    settings.coastalVoidColour,
                    settings.voidFillOpacity,
                    settings.coastalVoidEdge);
            }
        }
    }

    /**
     * Draws each smoothed edge, over the top of everything.
     *
     * @param g2 what to draw with
     */
    void paintCoasts(Graphics2D g2) {

        if (traced == null || !settings.showCoastline) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.coastlineColour,
            MapLook.OPAQUE_ALPHA));

        for (var coast : traced.coasts()) {
            g2.draw(MapPainting.buildPath(Coastlines.collectPoints(coast)));
        }
        paintPenetrations(g2);
        paintSpills(g2);
    }

    // Only the stretch of a pocket outline that is outside the drawn coast, not the pocket it
    // belongs to. A pocket with a sliver off one corner is almost all correct, and marking the
    // whole shape points at the right part as loudly as at the wrong one.
    private void paintSpills(Graphics2D g2) {

        g2.setStroke(new BasicStroke(MapLook.CROSSING_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.coastCrossingColour,
            MapLook.OPAQUE_ALPHA));

        for (var spill : spills) {
            g2.draw(MapPainting.buildPath(spill.run()));
        }
    }

    // The runs that go inside a cell, and the cells they go inside. Last of everything and in
    // colours nothing else uses, because the whole reason to draw them is to be able to point
    // at one - a count says there are nineteen and leaves every one of them to be hunted for.
    private void paintPenetrations(Graphics2D g2) {

        if (penetrations.isEmpty()) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.piercedCellColour,
            MapLook.OPAQUE_ALPHA));

        for (var penetration : penetrations) {
            for (var circle : penetration.circles()) {

                g2.draw(MapPainting.buildCircle(
                    traced.union().sites().get(circle), traced.union().reach()));
            }
        }

        g2.setStroke(new BasicStroke(MapLook.CROSSING_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.coastCrossingColour,
            MapLook.OPAQUE_ALPHA));

        for (var penetration : penetrations) {

            g2.draw(new Line2D.Double(
                penetration.from().point()[0],
                penetration.from().point()[1],
                penetration.to().point()[0],
                penetration.to().point()[1]));
        }
    }
}
