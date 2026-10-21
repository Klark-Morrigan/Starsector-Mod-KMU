package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.visibility.systems.MapVisibility;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The holders' inhabitation reads: which star systems somebody lives in, and which of them the
 * spotlit bloc lives in, off a rebuild's own reading of the sector.
 *
 * <p>It takes the whole pass rather than a sector and a rule, and that is what keeps its readers in
 * step. Three of them classify the same unowned cells - the production build, its incremental
 * refresh, and the debug border-tracing overlay - and a read given the sector could resolve a rule
 * of its own. The one that drifted would draw a cell as backdrop in a system the box over it names
 * somebody in. Answering off the pass makes that unstateable rather than merely unlikely, and shares
 * the walk each system was already read by.
 *
 * <p>Each sector-wide scan has a single-system arm for the incremental refresh, which re-derives the
 * systems a colony event marked. Both arms of each pair read one projection, the habitation of the
 * system, which is why they live together: an arm that took a different one would take a system
 * off the map, or spare a cell the scan called empty, the moment an event happened to mark it.
 *
 * <p>It sits with the holders rather than beside the framework's own visibility because these
 * are the holder layers' cells. What inhabitation <em>is</em> belongs to the colony set, which the
 * framework's {@link MapVisibility} spends without restating; which reading of the sector answers it
 * is the layer's own business, and a scan folded into the framework would put one layer's pass under
 * every layer that shares it.
 */
public final class HolderInhabitation {

    // Reads only; never instantiated.
    private HolderInhabitation() {
    }

    /**
     * Scans the pass's sector for every inhabited system.
     *
     * <p>The systems walked are the pass's own, so a pass over an unreachable sector iterates
     * nothing and reports nothing rather than guarding a sector it was never given.
     *
     * @param pass the rebuild's reading of the sector, whose walk of each system this scan shares
     * @return the keys of every system holding a colony somebody lives on or a known collapsed one
     */
    public static Set<SystemKey> readInhabitedSystemKeys(HolderPass pass) {

        var systemKeys = new HashSet<SystemKey>();

        for (var system : pass.readSystems()) {

            if (isSystemInhabited(pass, system)) {
                systemKeys.add(SystemKey.readKeyOf(system));
            }
        }
        return systemKeys;
    }

    /**
     * Whether anybody lives in one star system, under the pass's rule - the single-system arm of
     * {@link #readInhabitedSystemKeys}.
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

    /**
     * Which of {@code candidateSystemKeys} the spotlit bloc lives in, whether or not it holds them.
     *
     * <p>Reading habitation is what makes the sparing agree with the drawing. The cells this
     * spares are the ones the map classified as empty backdrop, and that classification asks the
     * habitation of this very set - so a bloc present here is a bloc the cell was never going to be
     * called empty for.
     *
     * <p>Answered over a caller-supplied candidate set rather than the whole sector, because the
     * only systems it can change anything for are the handful the holder map left out. Each
     * candidate is looked up by its key off the pass's own index, so the read costs the candidates
     * rather than a walk of every system in the sector.
     *
     * @param pass                the rebuild's reading of the sector, whose walk of each system
     *                            this read shares; a pass over no sector yields an empty set
     * @param spotlitBlocId       the spotlit bloc's ID; null yields an empty set (no spotlight)
     * @param candidateSystemKeys the systems to test - those this pass resolved no holder for
     * @return the candidates the spotlit bloc holds a colony somebody lives on in, in the
     *         candidates' own order
     */
    public static Set<SystemKey> findSpotlitPresenceSystemKeys(
            HolderPass pass,
            String spotlitBlocId,
            Set<SystemKey> candidateSystemKeys) {

        var presentSystemKeys = new LinkedHashSet<SystemKey>();

        // Returns before the lookup when nothing can be decided, since the index would be built for
        // nothing.
        if (!canFindBlocIn(pass, spotlitBlocId) || candidateSystemKeys.isEmpty()) {
            return presentSystemKeys;
        }

        var systemByKey = pass.sectorIndex().readSystemsByKey();

        for (var systemKey : candidateSystemKeys) {

            if (isBlocPresentIn(pass, spotlitBlocId, systemByKey.get(systemKey))) {
                presentSystemKeys.add(systemKey);
            }
        }
        return presentSystemKeys;
    }

    /**
     * Whether one bloc holds a colony somebody lives on in one star system - the single-system arm
     * of {@link #findSpotlitPresenceSystemKeys}.
     *
     * @param pass   the reading of the sector the answer is taken from
     * @param blocId the bloc to look for; null yields false (no spotlight)
     * @param system the system to read; null yields false
     * @return true when the bloc holds a colony somebody lives on in the system
     */
    public static boolean isBlocPresentIn(HolderPass pass, String blocId, StarSystemAPI system) {

        return canFindBlocIn(pass, blocId)
            && system != null
            && pass.readHabitationIn(system).blocIds().contains(blocId);
    }

    // The one guard both presence arms share, so neither can answer for a sector the other refuses.
    // Mid-load the systems are walkable while the economy is not, so no colony can be read and
    // nobody is reported present; with no bloc spotlit there is nobody to look for.
    private static boolean canFindBlocIn(HolderPass pass, String blocId) {
        return pass.canReadEconomy() && blocId != null;
    }
}
