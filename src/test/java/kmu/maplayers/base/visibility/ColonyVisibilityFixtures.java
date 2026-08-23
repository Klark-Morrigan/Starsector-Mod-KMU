package kmu.maplayers.base.visibility;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.markets.DecivilisedMarkets;

import java.util.Set;

/**
 * The visibility rules a suite poses a map surface with, where the rule is not one production
 * already names.
 *
 * <p>Only one such rule exists, which is why this holds a single value: the fog alone is
 * {@link ColonyVisibility#BASE_FOG}, named where the rule itself lives and read straight from
 * there by every case that wants it. Aliasing a production constant behind a test-only name
 * would leave two ways to say one thing and nothing to choose between them.
 *
 * <p>Shared rather than declared per suite because the reveal reaches every surface the map
 * draws - the cell, the band, both hover families, the pickers - so a suite writing its own
 * would be the tenth copy of one line, and a copy that drifted would pose a rule no other
 * surface is read under.
 */
public final class ColonyVisibilityFixtures {

    /**
     * The "show all factions" reveal on: the discovery arm of the fog lifted, and no gate held,
     * which is the widest rule any surface reads under.
     *
     * <p>Not what the player's live read returns with that toggle on - a reveal drops the one arm
     * it names and clears no gate beside it, so the shipped state holds every gate whatever the
     * reveals say. A case wanting that state has to say so itself, and {@code MapVisibilityRulesTest}
     * is where it is pinned.
     */
    public static final ColonyVisibility UNDER_THE_REVEAL = new ColonyVisibility(
        true,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of());

    // Constants only; never instantiated.
    private ColonyVisibilityFixtures() {
    }
}
