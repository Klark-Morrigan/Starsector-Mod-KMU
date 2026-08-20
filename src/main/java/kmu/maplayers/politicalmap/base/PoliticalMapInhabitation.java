package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.MapVisibility;
import kmu.maplayers.base.visibility.MapVisibilityRules;

import java.util.Set;

/**
 * The political map's inhabitation read: which star systems something stands in, under the
 * player's live visibility rules.
 *
 * <p>Two passes classify the same factionless cells - the production build and the debug
 * border-tracing overlay - and both must agree on which of them are settled, or the overlay stops
 * showing what the map shows. Binding the rules to the scan once here is what makes that true by
 * construction, rather than by two call sites remembering to read the same settings.
 *
 * <p>It reads the whole of {@link MapVisibilityRules} rather than one term of it, which is what
 * keeps the two arms below in step as the rules grow: a gate added to the colony rule reaches
 * both without either arm being remembered.
 *
 * <p>It sits in the political layer rather than beside the scan itself because these are this
 * feature's settings. The framework's {@link MapVisibility} answers inhabitation for any layer and
 * reads only the map-layer settings; folding a political-map toggle into it would put one feature's
 * knob under the framework every layer shares.
 */
public final class PoliticalMapInhabitation {

    // Reads only; never instantiated.
    private PoliticalMapInhabitation() {
    }

    /**
     * Scans the sector for every inhabited system under the player's current rules.
     *
     * <p>The rules are read per call rather than held, so a pass picks up a toggle the player
     * flipped since the last rebuild - and, being one call, cannot pair this scan with rules
     * some other read resolved.
     *
     * @param sector the sector to scan; null yields an empty set
     * @return the ids of every system holding a live colony or a known ruin
     */
    public static Set<String> readInhabitedSystemIds(SectorAPI sector) {
        return MapVisibility.findInhabitedSystemIds(
            sector,
            MapVisibilityRules.readFromLunaSettings());
    }

    /**
     * Whether anything stands in one star system, under the player's current rules.
     *
     * <p>The single-system arm of the scan above, for the incremental refresh: a colony event
     * marks the systems it moved, and re-deriving those is what keeps the standing inhabited set
     * in step with the sector between rebuilds. Answered here rather than at that caller so both
     * arms bind to the same rules - a per-system read taken under different ones from the scan it
     * edits would leave one system in the set answering under rules none of its neighbours did.
     *
     * @param sector the sector the system belongs to; null yields false
     * @param system the system to read; null yields false
     * @return true when the system holds a live colony or a known ruin
     */
    public static boolean isSystemInhabited(SectorAPI sector, StarSystemAPI system) {
        return MapVisibility.isInhabited(
            sector,
            system,
            MapVisibilityRules.readFromLunaSettings());
    }
}
