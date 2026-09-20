package kmu.maplayers.politicalmap.base.dominance;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures;

/**
 * The colony rules a pass is opened under where the case is about something else.
 *
 * <p>Every reading here counts a decivilised world as populated, which is what the map ships
 * doing. Stated once in this class rather than at each pass so a case reads as the visibility it
 * poses, and so a case that really is about habitation is the only kind that names the position
 * inline - where naming it looks deliberate rather than like the boilerplate beside it.
 */
public final class ColonyReadRulesFixtures {

    /** Nothing revealed and no gate lifted - the shipped reading, which most cases pose. */
    public static final ColonyReadRules UNDER_THE_FOG =
        buildRulesUnder(ColonyVisibility.BASE_FOG);

    /** The dev reveal, for a case reading what the fog would otherwise have withheld. */
    public static final ColonyReadRules UNDER_THE_DEV_REVEAL =
        buildRulesUnder(ColonyVisibilityFixtures.UNDER_THE_REVEAL);

    private ColonyReadRulesFixtures() {
    }

    /**
     * Pairs a stated visibility rule with the populated reading of a decivilised world, for a case
     * posing a visibility this class holds no constant for.
     *
     * @param colonyVisibility what the player may be shown of a colony
     * @return those rules, with a decivilised world inhabiting its system
     */
    public static ColonyReadRules buildRulesUnder(ColonyVisibility colonyVisibility) {

        return new ColonyReadRules(
            colonyVisibility,
            DecivilisedColonyHabitation.COUNTS_AS_POPULATED);
    }
}
