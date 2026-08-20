package kmu.maplayers.base.visibility;

import kmlib.starsector.colonies.ColonyVisibility;

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
     * The "show all factions" reveal on: the fog lifted outright, and no gate held, which is the
     * widest rule any surface reads under.
     *
     * <p>Not what the player's live read returns with the toggle on - that holds every gate,
     * since a gate has no setting of its own yet. A case wanting the shipped state has to say so
     * itself, and {@code MapVisibilityOverridesTest} is where that state is pinned.
     */
    public static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

    // Constants only; never instantiated.
    private ColonyVisibilityFixtures() {
    }
}
