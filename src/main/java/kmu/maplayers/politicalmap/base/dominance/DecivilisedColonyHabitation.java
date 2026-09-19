package kmu.maplayers.politicalmap.base.dominance;

import kmu.maplayers.base.visibility.colonies.ColonyKind;

/**
 * Whether a decivilised world amounts to somebody living in its system, for the habitation
 * projection an owner-painted layer reads.
 *
 * <p>The world is populated and nobody there speaks for it, which is the whole of
 * {@link ColonyKind#UNGOVERNED_COLONY}. A map of polities can honestly be read either way from
 * that, and which way is the player's to choose: territory drawn where people live, or territory
 * drawn where a polity stands.
 *
 * <p>The scope is that one projection and nothing wider. Counting such a world as unpopulated is
 * not a claim that nobody is there - the fog goes on finding it, the known listing goes on naming
 * it, and the box over its cell goes on stating the world and its owner. What moves with this is
 * everything projected from habitation: the cell's category, the blocs a picker offers for it, the
 * band a ribbon draws in it, and the colony size the stats fold.
 *
 * <p>An enum rather than a boolean, so a pass is opened by a site that names the position it takes
 * rather than by one passing a bare true.
 */
public enum DecivilisedColonyHabitation {

    /** Such a world inhabits its system, so its cell is territory somebody lives in. */
    COUNTS_AS_POPULATED(true),

    /** Such a world inhabits nothing, so a system holding only these is empty space. */
    COUNTS_AS_UNPOPULATED(false);

    private final boolean isCountedAsPopulated;

    DecivilisedColonyHabitation(boolean isCountedAsPopulated) {
        this.isCountedAsPopulated = isCountedAsPopulated;
    }

    /**
     * Whether a decivilised world joins the habitation projection under this rule.
     *
     * @return true where such a world counts as people living in its system
     */
    boolean isCountedAsPopulated() {
        return isCountedAsPopulated;
    }
}
