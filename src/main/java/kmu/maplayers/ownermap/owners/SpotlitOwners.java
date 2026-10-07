package kmu.maplayers.ownermap.owners;

/**
 * The spotlight as the render pipeline sees it: the synthetic key a spotlit owner's whole
 * footprint carries.
 *
 * <p>The key is an encoding - one group ID standing for a footprint so the geometry traces a single
 * frontier over it - and not a mechanic. Where the spotlit owner is present is the painting layer's
 * own read, answered by its source.
 *
 * <p>Held apart so the style, cluster and label sides can ask the one question they actually ask -
 * "is this the spotlight" - without naming a mechanic they do not use.
 *
 * <p>The encoding stays in this one class: a resolver stamping a footprint asks for the key rather
 * than spelling it, and the render side asks {@link #isSpotlitOwner} rather than matching a raw
 * string, so the two can never disagree about what a spotlit key looks like.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SpotlitOwners {

    // The spotlighted owner's single synthetic group key, carried by every system it is present
    // in - held or not - so the geometry fuses its whole footprint into one bordered cluster
    // group. The "$" sentinel prefix cannot occur in a real owner ID, so the key never collides
    // with a rival's.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    private SpotlitOwners() {
        // utility class, no instances.
    }

    /**
     * Whether a group key is the spotlighted owner's synthetic key - carried by every system the
     * owner is present in, which the render layer draws at full strength while every other key
     * recedes. The single question the filter branch asks to decide recede.
     *
     * @param ownerId the group key a resolved holder carries
     * @return true when the key is the spotlighted owner's synthetic key
     */
    public static boolean isSpotlitOwner(String ownerId) {
        return SPOTLIT_KEY.equals(ownerId);
    }

    /**
     * The key a resolve stamps onto every system of the spotlighted owner's footprint, so the
     * geometry fuses the whole of it into one bordered cluster.
     *
     * <p>Asked for rather than spelled, so the one class that decides what a spotlit key looks
     * like is also the one that recognises it.
     *
     * @return the synthetic group key standing for the spotlighted owner
     */
    public static String readSpotlitOwnerKey() {
        return SPOTLIT_KEY;
    }
}
