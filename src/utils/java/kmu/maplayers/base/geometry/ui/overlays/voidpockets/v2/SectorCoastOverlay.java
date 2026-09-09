package kmu.maplayers.base.geometry.ui.overlays.voidpockets.v2;

import kmu.maplayers.base.geometry.CoastCrossings;
import kmu.maplayers.base.geometry.CoastPocketFaults;
import kmu.maplayers.base.geometry.Coastlines;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SettledCoast;
import kmu.maplayers.base.geometry.render.MapLook;
import kmu.maplayers.base.geometry.render.MapPainting;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.maplayers.base.geometry.ui.overlays.voidpockets.CoastalPocketsOverlay;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * <b>The settled coast (v2), orchestrated.</b> One line round each run of cells that the
 * bridges have joined, and the void that line shuts in behind it.
 *
 * <p>What makes this one the settled coast is two choices and nothing else: it traces with the
 * bridges laid, so runs of cells a bridge joins come back as one shape, and it traces under the
 * settled coast rules. Those two lines are the whole of v2. Every step after them is
 * {@link CoastalPocketsOverlay}, which the continent coast in the neighbouring package runs
 * exactly as well - so a difference between the two on screen is a difference between the
 * coasts rather than between two copies of the drawing.
 *
 * <p>The marks below the coast are this construction's own, and are why it is a class rather
 * than a call: crossings and spills are faults a coast traced THIS way can have, and there is
 * nowhere else they would mean anything.
 *
 * <p>Drawn as a line over everything rather than as a fill under it. A fill would hide the arcs
 * the line is meant to be compared with, which is the one thing looking at it is for. The
 * outline it hands back is an ordinary closed ring, so filling it later needs nothing this
 * class does not already produce.
 */
public final class SectorCoastOverlay {

    private final ViewerSettings settings;

    // The half of this overlay the rival construction runs identically. Held rather than
    // inherited from, because what v2 and v3 share is a sequence of steps rather than an
    // identity - neither is a kind of the other, and nothing ever asks for "a coast overlay"
    // without knowing which.
    private final CoastalPocketsOverlay coast;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt any of these would be drawing marks measured against geometry the rest of the
    // frame is not being drawn from.
    private List<CoastCrossings.Penetration> penetrations = List.of();
    private List<CoastPocketFaults.Spill> spills = List.of();

    public SectorCoastOverlay(ViewerSettings settings) {

        this.settings = settings;
        this.coast = new CoastalPocketsOverlay(settings);
    }

    /**
     * Traces the smoothed edges again, or drops them when the overlay is switched off.
     *
     * <p>The pockets the coast shut in come off the same trace, so the line and the fills
     * inset from it cannot be built under two different settings within one frame.
     *
     * @param fixture the sector to trace in
     */
    public void refresh(SectorFixture fixture) {

        // Traced while either half of it is on screen. The line and the void it shuts in are
        // one construction seen twice, and the pockets come off this same trace - so a frame
        // showing one of them has already paid for both.
        // Under the switch over the whole settled construction, which suppresses this without
        // touching either of its own - so what was showing comes back when it is lifted.
        if (!settings.showSectorVoid
                || (!settings.showCoastline && !settings.showCoastalFill)) {

            coast.acceptTrace(null);
            penetrations = List.of();
            spills = List.of();
            return;
        }

        // The two lines that make this v2: bridges laid, settled rules.
        coast.acceptTrace(SettledCoast.traceAcrossBridges(
            fixture.getSites(),
            settings.parameters,
            settings.resolveCoastRules()));

        coast.findPockets(fixture);

        penetrations = CoastCrossings.findVisibleCrossings(
            coast.getTrace(),
            MapLook.RING_STROKE);

        // Against the border, not the rounded line drawn over it: rounding only pulls sharp
        // corners inwards, so a spill measured against that would mark every pocket the
        // rounding stepped inside of as a fault of this construction.
        spills = CoastPocketFaults.findSpills(
            coast.getPockets(),
            Coastlines.collectCoastOutlines(coast.getTrace()));
    }

    /**
     * Draws the void the coast shut in, beneath the cells.
     *
     * @param g2 what to draw with
     */
    public void paintPocketFills(Graphics2D g2) {

        if (!settings.showSectorVoid || !settings.showCoastalFill) {
            return;
        }

        coast.paintPocketFills(
            g2, settings.coastalVoidColour, settings.coastalVoidEdge);
    }

    /**
     * Draws each smoothed edge, over the top of everything.
     *
     * @param g2 what to draw with
     */
    public void paintCoasts(Graphics2D g2) {

        if (!settings.showSectorVoid || !coast.hasTrace() || !settings.showCoastline) {
            return;
        }

        coast.paintCoastRings(g2, settings.coastlineColour);
        coast.paintDroppedStretches(g2);
        coast.paintLandableFrontage(g2);

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

        var union = coast.getTrace().union();

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.piercedCellColour,
            MapLook.OPAQUE_ALPHA));

        for (var penetration : penetrations) {
            for (var circle : penetration.circles()) {

                g2.draw(MapPainting.buildCircle(
                    union.sites().get(circle),
                    union.reach()));
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
