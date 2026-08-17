package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.MapVisibility;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;

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
            MapVisibilityOverrides.readFromLunaSettings());
    }

    /**
     * Whether anything stands in one star system, under the player's current reveal.
     *
     * <p>The single-system arm of the scan above, for the incremental refresh: a colony event
     * marks the systems it moved, and re-deriving those is what keeps the standing inhabited set
     * in step with the sector between rebuilds. Answered here rather than at that caller so both
     * arms bind the reveal to the same rule - a per-system read taken under a different toggle
     * from the scan it edits would leave one system in the set answering under a reveal none of
     * its neighbours did.
     *
     * @param sector the sector the system belongs to; null yields false
     * @param system the system to read; null yields false
     * @return true when the system holds a live colony or a known ruin
     */
    public static boolean isSystemInhabited(SectorAPI sector, StarSystemAPI system) {
        return MapVisibility.isInhabited(
            sector,
            system,
            MapVisibilityOverrides.readFromLunaSettings());
    }
}
