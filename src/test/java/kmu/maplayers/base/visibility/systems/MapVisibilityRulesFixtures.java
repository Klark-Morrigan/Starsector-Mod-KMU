package kmu.maplayers.base.visibility.systems;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures;

/**
 * The visibility rules a suite poses when one of the two halves is what it is about, each named for
 * what it widens.
 *
 * <p>Named here so a suite above the substrate can pose a rule without spelling the colony half it
 * is built from - what that half reads is the substrate's to say.
 *
 * <p>Final class with a private constructor: fixture of static values, no instances.
 */
public final class MapVisibilityRulesFixtures {

    /** The bare fog, with every star system forced onto the map. */
    public static final MapVisibilityRules FORCED_UNDER_THE_FOG =
        new MapVisibilityRules(ColonyVisibility.BASE_FOG, true);

    /** The fog lifted by the dev reveal, with nothing forced onto the map. */
    public static final MapVisibilityRules UNFORCED_UNDER_THE_REVEAL =
        new MapVisibilityRules(ColonyVisibilityFixtures.UNDER_THE_REVEAL, false);

    private MapVisibilityRulesFixtures() {
        // fixture of static values, no instances.
    }
}
