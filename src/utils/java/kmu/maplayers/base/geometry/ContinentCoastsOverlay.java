package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Preview of per-continent coastlines: each touching-connected run of cells traced as its own
 * closed coast, with the inlet bridges that survive those coasts laid over them.
 *
 * <p>A rival construction being judged, not a part of the map. The settled coast treats a
 * bridge as connective tissue and traces one line round everything it joins; the proposal is
 * to give each continent its own smoothed coast and let the bridges cross those lines where
 * they must, with each crossing cutting the pocket behind it in two. Whether that is worth
 * building turns on where and how often the crossings actually fall, which is only legible
 * on screen - so this draws the lines and nothing else. No pockets are built from it,
 * nothing downstream reads it, and switching it on moves nothing but paint.
 *
 * <p>Drawn as a line over everything for the same reason the settled coast is: it is a
 * proposal, and the only way to judge it is against the bridges and the coast it would
 * replace, all on screen at once.
 */
final class ContinentCoastsOverlay {

    private final ViewerSettings settings;

    // What the last trace found, held rather than recomputed while painting: a frame that
    // rebuilt these would be drawing lines traced against geometry the rest of the frame is
    // not being drawn from.
    //
    // The whole trace rather than the rings alone, because what the smoothing LEFT OUT is
    // part of what a reader is judging and can only be had from the walk that left it out.
    private Coastlines.TracedCoasts traced;

    // The bridges that survive those coastlines. Held beside the trace they were filtered
    // against rather than found while painting, since which ones survive is a question about
    // THAT trace and a frame that asked it again could answer about a different one.
    private List<CellGap> bridges = List.of();

    ContinentCoastsOverlay(ViewerSettings settings) {
        this.settings = settings;
    }

    /**
     * Traces the continents again, or drops them when the preview is switched off.
     *
     * <p>Under the v3 coast rules rather than the settled coast's, so a knob moved down here
     * moves this line and leaves the map alone. What the two constructions still share is the
     * cells and the parameters they are built from - they are two readings of one sector.
     *
     * @param fixture the sector to trace in
     */
    void refresh(SectorFixture fixture) {

        traced = null;
        bridges = List.of();

        // The coasts are traced while either they or the bridges are wanted, because the
        // bridges are filtered against them: switching the bridges on without the line that
        // decides which of them survive would show a set nothing on screen accounts for.
        if (!settings.showContinentCoasts && !settings.showContinentBridges) {
            return;
        }

        traced = Coastlines.traceContinentCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveContinentCoastRules());

        if (settings.showContinentBridges) {

            bridges = ContinentBridges.findAnchoredBridges(
                traced,
                settings.parameters,
                settings.resolveContinentBridgeRules());
        }
    }

    /**
     * Draws each continent's coast, over the top of everything.
     *
     * @param g2 what to draw with
     */
    void paintCoasts(Graphics2D g2) {

        if (traced == null) {
            return;
        }

        paintBridges(g2);

        if (settings.showContinentCoasts) {

            MapPainting.paintLineRings(
                g2,
                Coastlines.collectCoastRings(traced),
                settings.continentCoastColour);
        }

        if (!settings.showDroppedStretches || !settings.showContinentCoasts) {
            return;
        }

        // In the same colour the settled coast marks its own drops with. The two
        // constructions are told apart by the line each drop sits beside, and a second
        // colour would imply the drops themselves differ in kind, which they do not.
        MapPainting.paintLineRuns(
            g2,
            Coastlines.collectDroppedRuns(
                traced,
                settings.parameters.measureArcSegments()),
            settings.droppedStretchColour);
    }

    // The bridges that survived the coastlines, drawn end to end at their true extent.
    //
    // Under the coast rather than over it, because the coastline is what JUDGED them: where
    // the two meet, the line that did the refusing is the one worth being able to see.
    private void paintBridges(Graphics2D g2) {

        if (!settings.showContinentBridges) {
            return;
        }

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(MapPainting.applyAlpha(
            settings.continentBridgeColour,
            MapLook.OPAQUE_ALPHA));

        for (var bridge : bridges) {
            g2.draw(MapPainting.buildSpanLine(bridge));
        }
    }
}
