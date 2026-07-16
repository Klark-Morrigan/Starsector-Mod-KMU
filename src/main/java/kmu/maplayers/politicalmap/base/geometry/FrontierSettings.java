package kmu.maplayers.politicalmap.base.geometry;

import kmu.settings.KmuLunaSettings;

import java.util.Map;

/**
 * The per-pass inputs that decide how far an open-frontier edge recedes toward an unheld
 * dead or decivilised star: the site coordinates its spacing is measured from, the keep-out
 * radius the pocket around the star is sized by, and the master toggle.
 *
 * <p>Bundled and read once per rebuild so the whole pass offsets under one snapshot rather
 * than re-reading the toggle and radius per edge. The site coordinates feed the shared
 * {@link FrontierSetback}; the radius and toggle come from Luna settings. With the toggle
 * off the snapshot yields no setback, leaving every open frontier on the normal channel -
 * the plain midline border.
 *
 * @param siteBySystemId each system's {x, y} site, measuring the owned-to-empty spacing
 * @param keepOutRadius  how close owned colour may reach an unheld dead star, in world units
 * @param isEnabled      the master toggle; false leaves open frontiers on the normal inset
 */
public record FrontierSettings(
        Map<String, double[]> siteBySystemId,
        double keepOutRadius,
        boolean isEnabled) {

    // Reads the live toggle and keep-out radius from Luna settings, pairing them with the
    // pass's site coordinates so every consumer in the rebuild offsets under one snapshot.
    public static FrontierSettings readFromLunaSettings(Map<String, double[]> siteBySystemId) {
        return new FrontierSettings(
                siteBySystemId,
                KmuLunaSettings.getPoliticalMapFrontierKeepOut(),
                KmuLunaSettings.isAsymmetricFrontierEnabled());
    }
}
