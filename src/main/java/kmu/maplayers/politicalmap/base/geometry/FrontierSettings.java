package kmu.maplayers.politicalmap.base.geometry;

import kmu.settings.KmuLunaSettings;

import java.util.Map;

/**
 * The per-pass inputs that decide how a faction's territory meets an unheld dead or
 * decivilised star: the site coordinates the spacing between two systems is measured from,
 * the keep-out radius the pocket around the star is sized by, the cell radius bounding how
 * far a system's colour carries from its star, and the master toggle.
 *
 * <p>Bundled and read once per rebuild so a whole pass works from one snapshot rather than
 * re-reading the toggle and distances per edge. The site coordinates feed the shared
 * {@link FrontierSetback}; the distances and toggle come from Luna settings. With the toggle
 * off the snapshot describes no frontier treatment at all, leaving every open frontier on
 * the normal channel - the plain midline border.
 *
 * <p>The cell radius bounds the flow rather than a frontier-specific twin of it: it already
 * means "how far a system's colour reaches from its star", and a colour flowing toward a dead
 * star is that same reach, so one number governs both. A colour beyond one radius of its own
 * star is therefore impossible however it got there, and the space past it stays neutral with
 * no second knob to reconcile. It is the radius the cells are seeded at, and a change to it
 * reseeds them and rebuilds the territories, so a snapshot read here matches the geometry it
 * bounds.
 *
 * @param siteBySystemId each system's {x, y} site, measuring the owned-to-empty spacing
 * @param keepOutRadius  how close owned colour may reach an unheld dead star, in world units
 * @param cellRadius     how far owned colour reaches from its own star, in world units,
 *                       bounding the flow toward a dead star as it bounds the cell itself
 * @param isEnabled      the master toggle; false leaves open frontiers on the normal inset
 */
public record FrontierSettings(
        Map<String, double[]> siteBySystemId,
        double keepOutRadius,
        double cellRadius,
        boolean isEnabled) {

    // Reads the live toggle and the two radii from Luna settings, pairing them with the
    // pass's site coordinates so every consumer in the rebuild works from one snapshot.
    public static FrontierSettings readFromLunaSettings(Map<String, double[]> siteBySystemId) {
        return new FrontierSettings(
                siteBySystemId,
                KmuLunaSettings.getPoliticalMapFrontierKeepOut(),
                KmuLunaSettings.getPoliticalMapCellRadius(),
                KmuLunaSettings.isAsymmetricFrontierEnabled());
    }
}
