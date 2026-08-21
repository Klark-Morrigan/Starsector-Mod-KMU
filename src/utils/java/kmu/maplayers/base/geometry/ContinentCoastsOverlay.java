package kmu.maplayers.base.geometry;

import java.awt.Graphics2D;
import java.util.List;

/**
 * Preview of per-continent coastlines: each touching-connected run of cells traced as its
 * own closed coast, with no bridges laid.
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
    private List<List<double[]>> rings = List.of();

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

        if (!settings.showContinentCoasts) {

            rings = List.of();
            return;
        }

        rings = Coastlines.collectCoastRings(Coastlines.traceContinentCoasts(
            fixture.getSites(),
            settings.parameters,
            settings.resolveContinentCoastRules()));
    }

    /**
     * Draws each continent's coast, over the top of everything.
     *
     * @param g2 what to draw with
     */
    void paintCoasts(Graphics2D g2) {

        if (rings.isEmpty()) {
            return;
        }
        MapPainting.paintLineRings(g2, rings, settings.continentCoastColour);
    }
}
