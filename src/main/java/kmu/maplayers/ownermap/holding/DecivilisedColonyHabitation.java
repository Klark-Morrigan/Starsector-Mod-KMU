package kmu.maplayers.ownermap.holding;

import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.settings.KmuOwnerMapStyleSettings;

/**
 * Whether a decivilised world amounts to somebody living in its system, for the habitation
 * projection an owner-painted layer reads.
 *
 * <p>The world is populated and nobody there speaks for it, which is the whole of
 * {@link ColonyKind#UNGOVERNED_COLONY}. A map of polities can honestly be read either way from
 * that, and which way is the player's to choose: an owner's fill drawn where people live, or drawn
 * only where a polity stands.
 *
 * <p>The scope is that one projection and nothing wider. Counting such a world as unpopulated is
 * not a statement that nobody is there - the fog goes on finding it, the known listing goes on naming
 * it, and the box over its cell goes on stating the world and its owner. What moves with this is
 * everything projected from habitation: the cell's category, the blocs a picker offers for it, the
 * band a ribbon draws in it, and the colony size the stats fold.
 *
 * <p>An enum rather than a boolean, so a pass is opened by a site that names the position it takes
 * rather than by one passing a bare true.
 */
public enum DecivilisedColonyHabitation {

    /** Such a world inhabits its system, so its cell is a place somebody lives in. */
    COUNTS_AS_POPULATED(true),

    /** Such a world inhabits nothing, so a system holding only these is empty space. */
    COUNTS_AS_UNPOPULATED(false);

    private final boolean isCountedAsPopulated;

    DecivilisedColonyHabitation(boolean isCountedAsPopulated) {
        this.isCountedAsPopulated = isCountedAsPopulated;
    }

    /**
     * The position the player's settings currently take.
     *
     * <p>Called where a rebuild opens its pass and nowhere below it, so one rebuild resolves the
     * whole sector under the setting that was in force when it began. A read taken per system
     * could be answered one way for half the sector and the other way for the rest, leaving a map
     * nothing on screen explains.
     *
     * <p>No refresh work rides on it: every KMU settings change advances the revision the owner
     * map's rebuild decider folds into its content and holding revisions, so a flip already forces
     * the rebuild that re-reads this.
     *
     * @return the rule the player's live settings ask for
     */
    public static DecivilisedColonyHabitation readFromLunaSettings() {

        return KmuOwnerMapStyleSettings.shouldCountDecivilisedSystemsAsPopulated()
            ? COUNTS_AS_POPULATED
            : COUNTS_AS_UNPOPULATED;
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
