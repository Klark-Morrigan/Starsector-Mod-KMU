package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Where the spotlit bloc is present beyond the systems it holds, read off a {@link HolderPass}.
 *
 * <p>Neither read is about a mechanic. Both ask only where somebody lives, off the layer-generic
 * habitation of the sector, so no weighting rule has to be sampled to answer them. What a spotlit
 * holder resolves to is the painting layer's, and lives beside that layer's own resolve.
 *
 * <p>Both arms live here so the sector-wide scan and the per-system read take one projection: a
 * per-system read that took a different one would spare a cell the scan called empty, or the
 * reverse.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SpotlitBlocPresence {

    private SpotlitBlocPresence() {
        // utility class, no instances.
    }

    /**
     * Which of {@code candidateSystemKeys} the spotlighted bloc is present in, whether or not it
     * holds them.
     *
     * <p>Reading habitation is what makes the sparing agree with the drawing. The cells this
     * spares are the ones the map classified as empty backdrop, and that classification asks the
     * emptiness of this very set - so a bloc present here is a bloc the cell was never going to be
     * called empty for.
     *
     * <p>Answered over a caller-supplied candidate set rather than the whole sector, because the
     * only systems it can change anything for are the handful the holder map left out. Each
     * candidate is looked up by its key off the pass's own index, so the read costs the candidates
     * rather than a walk of every system in the sector.
     *
     * @param pass                the rebuild's reading of the sector, whose walk of each system
     *                            this read shares; a pass over no sector yields an empty set
     * @param selectedBlocId      the spotlighted bloc's ID; null yields an empty set (no filter)
     * @param candidateSystemKeys the systems to test - those this pass resolved no holder for
     * @return the candidates the spotlighted bloc holds a colony somebody lives on in, in the
     *         candidates' own order
     */
    public static Set<SystemKey> findPresentSystemKeys(
            HolderPass pass,
            String selectedBlocId,
            Set<SystemKey> candidateSystemKeys) {

        var presentSystemKeys = new LinkedHashSet<SystemKey>();

        // A read that can decide nothing returns before the lookup: off filter there is no pick to
        // look for, and the index would be built for nothing.
        if (!pass.canReadEconomy() || selectedBlocId == null || candidateSystemKeys.isEmpty()) {
            return presentSystemKeys;
        }

        var systemByKey = pass.sectorIndex().readSystemsByKey();

        for (var systemKey : candidateSystemKeys) {

            if (isBlocPresentIn(pass, selectedBlocId, systemByKey.get(systemKey))) {
                presentSystemKeys.add(systemKey);
            }
        }
        return presentSystemKeys;
    }

    /**
     * Whether one bloc holds a colony somebody lives on in one star system - the single-system arm
     * of {@link #findPresentSystemKeys}, for a batch re-deriving a marked system.
     *
     * @param pass   the reading of the sector the answer is taken from
     * @param blocId the bloc to look for; null yields false (no spotlight)
     * @param system the system to read; null yields false
     * @return true when the bloc holds a colony somebody lives on in the system
     */
    public static boolean isBlocPresentIn(HolderPass pass, String blocId, StarSystemAPI system) {

        if (!pass.canReadEconomy() || blocId == null || system == null) {
            return false;
        }

        return pass
            .readHabitationIn(system)
            .blocIds()
            .contains(blocId);
    }
}
