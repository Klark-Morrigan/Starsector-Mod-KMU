package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.visibility.MapVisibility;

import java.util.Set;

/**
 * The political map's inhabitation read: which star systems something stands in, under the
 * player's live dev reveal.
 *
 * <p>Two passes classify the same factionless cells - the production build and the debug
 * border-tracing overlay - and both must agree on which of them are settled, or the overlay stops
 * showing what the map shows. Binding the reveal to the scan once here is what makes that true by
 * construction, rather than by two call sites remembering to read the same toggle.
 *
 * <p>It sits in the political layer rather than beside the scan itself because the reveal is this
 * feature's setting. The framework's {@link MapVisibility} answers inhabitation for any layer and
 * reads only the map-layer settings; folding a political-map toggle into it would put one feature's
 * knob under the framework every layer shares.
 */
public final class PoliticalMapInhabitation {

    // Reads only; never instantiated.
    private PoliticalMapInhabitation() {
    }

    /**
     * Scans the sector for every inhabited system under the player's current reveal.
     *
     * <p>The reveal is read per call rather than held, so a pass picks up a toggle the player
     * flipped since the last rebuild - and, being one call, cannot pair this scan with a reveal
     * some other read resolved.
     *
     * @param sector the sector to scan; null yields an empty set
     * @return the ids of every system holding a live colony or a known ruin
     */
    public static Set<String> readInhabitedSystemIds(SectorAPI sector) {
        return MapVisibility.findInhabitedSystemIds(
            sector,
            PoliticalMapDevToggles.readFromLunaSettings().convertToVisibilityOverrides());
    }
}
