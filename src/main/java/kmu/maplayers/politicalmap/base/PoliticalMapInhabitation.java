package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.visibility.MapVisibility;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.HashSet;
import java.util.Set;

/**
 * The political map's inhabitation read: which star systems somebody lives in, off a rebuild's own
 * reading of the sector.
 *
 * <p>Two passes classify the same factionless cells - the production build and the debug
 * border-tracing overlay - and both must agree on which of them are settled, or the overlay stops
 * showing what the map shows. Answering both from the pass they were opened with is what makes that
 * true by construction, rather than by two call sites remembering to read the same settings.
 *
 * <p>It takes the whole pass rather than a sector and a rule, which is what binds this read to the
 * one every other surface of the rebuild answers through: the colony rule was sampled where the
 * rebuild began, and each system is walked once for the whole of it. A read given the sector could
 * still resolve a rule of its own, and the one that drifted would have a cell drawn as backdrop in a
 * system the box over it names somebody in.
 *
 * <p>Inhabitation is the pass's habitation value, and the value matters as much as the projection.
 * The spotlight's presence read asks the same value for its blocs, so a cell this calls empty space
 * is never one the filter has meanwhile kept a bloc's fill over - see
 * {@link kmu.maplayers.politicalmap.base.dominance.SystemHabitation}.
 *
 * <p>It sits in the political layer rather than beside the framework's own visibility because these
 * are this feature's cells. The framework's {@link MapVisibility} states what inhabitation
 * <em>is</em> for any layer; which reading of the sector answers it is the layer's own business, and
 * a scan folded into the framework would put one layer's pass under every layer that shares it.
 */
public final class PoliticalMapInhabitation {

    // Reads only; never instantiated.
    private PoliticalMapInhabitation() {
    }

    /**
     * Scans the pass's sector for every inhabited system.
     *
     * <p>Walked here rather than through a framework roll-up because the habitation each system is
     * judged by is the pass's, and only a caller holding the pass can ask it. The systems are the
     * pass's own, so a pass over an unreachable sector iterates nothing and reports nothing.
     *
     * @param pass the rebuild's reading of the sector, whose walk of each system this scan shares
     * @return the ids of every system holding a colony somebody lives on or a known ruin
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
     * the systems it moved, and re-deriving those is what keeps the standing inhabited set in step
     * with the sector between rebuilds. Answered here rather than at that caller so both arms
     * compose the same two facts - a per-system read that dropped the ruin arm would take a
     * decivilised system off the map the moment an event happened to mark it.
     *
     * @param pass   the reading of the sector the answer is taken from
     * @param system the system to read; null yields false
     * @return true when the system holds a colony somebody lives on or a known ruin
     */
    public static boolean isSystemInhabited(HolderPass pass, StarSystemAPI system) {

        return MapVisibility.isInhabited(
            pass.readHabitationIn(system).hasInhabitingColony(),
            DecivilisedMarkets.hasRevealedDecivilisedPlanet(system));
    }
}
