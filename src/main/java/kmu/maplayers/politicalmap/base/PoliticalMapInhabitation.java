package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.visibility.systems.MapVisibility;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.HashSet;
import java.util.Set;

/**
 * The political map's inhabitation read: which star systems somebody lives in, off a rebuild's own
 * reading of the sector.
 *
 * <p>It takes the whole pass rather than a sector and a rule, and that is what keeps its readers in
 * step. Three of them classify the same factionless cells - the production build, its incremental
 * refresh, and the debug border-tracing overlay - and a read given the sector could resolve a rule
 * of its own. The one that drifted would draw a cell as backdrop in a system the box over it names
 * somebody in. Answering off the pass makes that unstateable rather than merely unlikely, and shares
 * the walk each system was already read by.
 *
 * <p>It sits in the political layer rather than beside the framework's own visibility because these
 * are this feature's cells. What inhabitation <em>is</em> belongs to the colony set, which the
 * framework's {@link MapVisibility} spends without restating; which reading of the sector answers it
 * is the layer's own business, and a scan folded into the framework would put one layer's pass under
 * every layer that shares it.
 */
public final class PoliticalMapInhabitation {

    // Reads only; never instantiated.
    private PoliticalMapInhabitation() {
    }

    /**
     * Scans the pass's sector for every inhabited system.
     *
     * <p>The systems walked are the pass's own, so a pass over an unreachable sector iterates
     * nothing and reports nothing rather than guarding a sector it was never given.
     *
     * @param pass the rebuild's reading of the sector, whose walk of each system this scan shares
     * @return the ids of every system holding a colony somebody lives on or a known collapsed one
     */
    public static Set<String> readInhabitedSystemIds(HolderPass pass) {

        var systemIds = new HashSet<String>();

        for (var system : pass.readSystems()) {

            if (isSystemInhabited(pass, system)) {
                systemIds.add(system.getId());
            }
        }
        return systemIds;
    }

    /**
     * Whether anybody lives in one star system, under the pass's rule.
     *
     * <p>The single-system arm of the scan above, for the incremental refresh: a colony event marks
     * the systems it moved, and re-deriving those keeps the standing inhabited set in step with the
     * sector between rebuilds. Answered here rather than at that caller so both arms read the same
     * projection - a per-system read that took a different one would take a system off the map the
     * moment an event happened to mark it.
     *
     * <p>The habitation projection is the whole of it, a collapsed colony being one of the colonies it
     * admits. There is no second reading of the system to compose, which is what keeps the cell
     * and the box over it from ever parting on who is present.
     *
     * @param pass   the reading of the sector the answer is taken from
     * @param system the system to read; null yields false
     * @return true when the system holds a colony somebody lives on or a known collapsed one
     */
    public static boolean isSystemInhabited(HolderPass pass, StarSystemAPI system) {
        return pass.readHabitationIn(system).hasInhabitingColony();
    }
}
