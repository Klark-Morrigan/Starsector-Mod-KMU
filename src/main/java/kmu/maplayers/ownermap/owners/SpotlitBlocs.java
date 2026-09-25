package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderPass;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The spotlight as the render pipeline sees it: the synthetic key a spotlit bloc's whole
 * footprint carries, and where that bloc is present beyond the systems it holds.
 *
 * <p>Neither question is about a mechanic. The key is an encoding - one group ID standing for a
 * footprint so the geometry traces a single frontier over it - and the presence read asks only
 * where somebody lives, off the layer-generic reading of the sector. What a *spotlit* holder
 * then resolves to is the painting layer's, and lives beside that layer's own resolve.
 *
 * <p>Held apart from that resolve so the style, cluster and label sides can ask the two
 * questions they actually ask without naming a mechanic they do not use. Every one of them
 * wants "is this the spotlight" or "is the bloc here", and none of them weighs anything.
 *
 * <p>The encoding stays in this one class either way: a resolver stamping a footprint asks for
 * the key rather than spelling it, and the render side asks {@link #isSpotlitBloc} rather than
 * matching a raw string, so the two can never disagree about what a spotlit key looks like.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SpotlitBlocs {

    // The spotlighted bloc's single synthetic group key, carried by every system it is present
    // in - held or not - so the geometry fuses its whole footprint into one bordered cluster
    // group. The "$" sentinel prefix cannot occur in a real faction or group ID, so the key never
    // collides with a rival's.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    private SpotlitBlocs() {
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
     * <p>It takes the layer-generic reading of the sector rather than a weighted pass. Nothing
     * here weighs a market, which means no weighting rule has to be sampled to answer it.
     *
     * <p>Answered over a caller-supplied candidate set rather than the whole sector, because the
     * only systems it can change anything for are the handful the holder map left out. Walking
     * every system would re-read the economy the holding resolve just walked, for an answer
     * discarded at all but a few of them.
     *
     * @param pass                the rebuild's reading of the sector, whose walk of each system
     *                            this read shares; a pass over no sector yields an empty set
     * @param selectedBlocId      the spotlighted bloc's ID; null yields an empty set (no filter)
     * @param candidateSystemKeys the systems to test - those this pass resolved no holder for
     * @return the candidates the spotlighted bloc holds a colony somebody lives on in
     */
    public static Set<SystemKey> findPresentSystemKeys(
            HolderPass pass,
            String selectedBlocId,
            Set<SystemKey> candidateSystemKeys) {

        var presentSystemKeys = new LinkedHashSet<SystemKey>();

        // A read that can decide nothing returns before the walk: off filter there is no pick to
        // look for, and with no candidates every system the walk reached would be discarded.
        if (!pass.canReadEconomy() || selectedBlocId == null || candidateSystemKeys.isEmpty()) {
            return presentSystemKeys;
        }
        for (StarSystemAPI system : pass.readSystems()) {

            // Membership is tested before the colony read, so a system outside the candidate
            // set costs a set probe rather than a read of its colonies.
            var systemKey = SystemKey.readKeyOf(system);
            if (!candidateSystemKeys.contains(systemKey)) {
                continue;
            }
            if (pass.readHabitationIn(system).blocIds().contains(selectedBlocId)) {
                presentSystemKeys.add(systemKey);
            }
        }
        return presentSystemKeys;
    }

    /**
     * Whether a group key is the spotlighted bloc's synthetic key - carried by every system the
     * bloc is present in, which the render layer draws at full strength while every other key
     * recedes. The single question the filter branch asks to decide recede.
     *
     * @param blocId the group key a resolved holder carries
     * @return true when the key is the spotlighted bloc's synthetic key
     */
    public static boolean isSpotlitBloc(String blocId) {
        return SPOTLIT_KEY.equals(blocId);
    }

    /**
     * The key a resolve stamps onto every system of the spotlighted bloc's footprint, so the
     * geometry fuses the whole of it into one bordered cluster.
     *
     * <p>Asked for rather than spelled, so the one class that decides what a spotlit key looks
     * like is also the one that recognises it.
     *
     * @return the synthetic group key standing for the spotlighted bloc
     */
    public static String readSpotlitBlocKey() {
        return SPOTLIT_KEY;
    }
}
