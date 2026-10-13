package kmu.maplayers.ownermap.owners;

import kmlib.colour.ColourPair;

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
 * <p>The encoding stays in this one class: a resolver stamping a footprint asks for the spotlit
 * owner rather than spelling its key, and the render side asks {@link #isSpotlitOwner} rather than
 * matching a raw string, so the two can never disagree about what a spotlit key looks like. Every
 * layer that spotlights builds the owner here, so a held footprint and a claimed one fuse alike.
 */
public final class SpotlitOwners {

    // The spotlighted owner's single synthetic group key, carried by every system it is present
    // in - held or not - so the geometry fuses its whole footprint into one bordered cluster
    // group. The "$" sentinel prefix cannot occur in a real owner ID, so the key never collides
    // with a rival's.
    private static final String SPOTLIT_KEY = "$kmu_filter_spotlit";

    private SpotlitOwners() {
    }

    /**
     * Whether a group key is the spotlighted owner's synthetic key - carried by every system the
     * owner is present in, which the render layer draws at full strength while every other key
     * recedes. The single question the filter branch asks to decide recede.
     *
     * @param ownerId the key a resolved owner carries
     * @return true when the key is the spotlighted owner's synthetic key
     */
    public static boolean isSpotlitOwner(String ownerId) {
        return SPOTLIT_KEY.equals(ownerId);
    }

    /**
     * The owner that draws a system as part of the spotlighted owner's single footprint: its
     * palette carried on the one synthetic key, so a cell holding it paints at full strength and
     * clusters into that one bordered frontier rather than a cluster of its own.
     *
     * <p>Keying any system this way fuses it into the spotlight, however the spotlighted owner
     * reaches it. A caller holds the returned owner opaquely and never has to name the key.
     *
     * @param palette the spotlighted owner's shades, as the rebuild that stamps them resolves them;
     *                null where they did not resolve
     * @return the spotlit owner, or null for a null palette, so a caller drops the system rather
     *         than painting it colourless
     */
    public static SystemOwner createSpotlitOwner(ColourPair palette) {

        if (palette == null) {
            return null;
        }

        return new SystemOwner(SPOTLIT_KEY, palette);
    }
}
