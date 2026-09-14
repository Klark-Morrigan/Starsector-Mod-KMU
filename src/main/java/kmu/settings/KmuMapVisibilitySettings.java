package kmu.settings;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

/**
 * The overrides that widen what the map may show beyond what the campaign has revealed.
 *
 * <p>Each reaches exactly the one thing it names: a knob that also cleared a neighbour's gate would
 * show a player a second thing they never asked for, with nothing on screen saying why.
 *
 * <p>Apart from every other knob here because these are the only ones that can spoil a campaign,
 * and the only ones whose fallbacks are chosen for that reason rather than for taste - a map that
 * gave a sector away while LunaLib had no stored value could not take it back. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapVisibilitySettings {

    // Each override reaches exactly the one thing it names: a knob that also cleared a neighbour's
    // gate would show a player a second thing they never asked for, with nothing on screen saying
    // why. What each does to the colony rule is RevelationGate's and ColonyVisibility's word.
    //
    // The first two are the spoiler gates, over the shapes the discovery fog alone leaks - an
    // unowned station nobody ever lived on, and a market that conceals itself. Both sit on entities
    // that were never discoverable, so the fog admits them from the first day of a campaign. The
    // last three reseed the geometry, so moving one forces a geometry rebuild rather than the
    // restyle a styling knob triggers. Two of those widen outright; the survey level runs both
    // ways, since it also decides whether a collapsed colony may be found on a neighbour's word.
    private static final String SHOW_UNSEEN_ABANDONED_STATIONS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUnseenAbandonedStations";

    private static final String SHOW_UNSEEN_HIDDEN_MARKETS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUnseenHiddenMarkets";

    private static final String SHOW_UNDISCOVERED_MARKETS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUndiscoveredMarkets";

    private static final String SHOW_DECIVILISED_WORLDS_AT_SURVEY_LEVEL_FIELD =
        "kmu_map_visibility_overrides_decivilisedWorldSurveyLevel";

    private static final String SHOW_HIDDEN_SYSTEMS_FIELD =
        "kmu_map_visibility_overrides_shouldShowHiddenSystems";

    // Off is the safe way round for the fallbacks as well as for the shipped defaults: these
    // constants answer while LunaLib has no stored value, and a map that spoiled a sector during
    // that window could not take it back.
    private static final boolean DEFAULT_SHOW_UNSEEN_ABANDONED_STATIONS = false;

    private static final boolean DEFAULT_SHOW_UNSEEN_HIDDEN_MARKETS = false;

    private static final boolean DEFAULT_SHOW_UNDISCOVERED_MARKETS = false;

    private static final boolean DEFAULT_SHOW_HIDDEN_SYSTEMS = false;

    // Not an "off" the way its neighbours are - a survey level always applies - and this one is the
    // level vanilla itself shows a condition from, so the shipped state says exactly what the game
    // does and no more.
    private static final SurveyLevelChoice DEFAULT_DECIVILISED_WORLD_SURVEY_LEVEL =
        SurveyLevelChoice.SEEN;

    private KmuMapVisibilitySettings() {
    }

    /**
     * @return whether an unowned market carrying vanilla's abandoned-station condition is drawn,
     *         counted and named as soon as its entity is found, rather than waiting until the
     *         player has been in its star system or somebody lives there who would have seen it;
     *         off by default. A station a faction keeps wears the same condition and is not one of
     *         these
     */
    public static boolean shouldShowUnseenAbandonedStations() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNSEEN_ABANDONED_STATIONS_FIELD,
            DEFAULT_SHOW_UNSEEN_ABANDONED_STATIONS);
    }

    /**
     * @return whether a market marked hidden is drawn, counted and named wherever it currently
     *         stands as soon as its entity is found, rather than waiting until somebody has seen it
     *         standing there; off by default
     */
    public static boolean shouldShowUnseenHiddenMarkets() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNSEEN_HIDDEN_MARKETS_FIELD,
            DEFAULT_SHOW_UNSEEN_HIDDEN_MARKETS);
    }

    /**
     * @return whether a colony the player has not discovered yet still counts - bypassing the
     *         known-to-player gate, so such a colony marks its system as inhabited and, on a layer
     *         that weighs colonies, folds into what that layer weighs; off by default
     */
    public static boolean shouldShowUndiscoveredMarkets() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNDISCOVERED_MARKETS_FIELD,
            DEFAULT_SHOW_UNDISCOVERED_MARKETS);
    }

    /**
     * @return how far a decivilised world must have been surveyed before the map will say its
     *         colony has collapsed; SEEN by default, which is what vanilla itself asks before
     *         showing a condition. A separate axis from discovery: a planet flown past is
     *         discovered whatever its survey level says. It is also the bar that decides whether
     *         the map will take a neighbour's word for such a world: NONE admits every one of them
     *         outright, SEEN is where a sighting is survey enough, and PRELIMINARY or FULL asks for
     *         readings nobody's presence produces, so only the player's own survey counts
     */
    public static SurveyLevel getDecivilisedWorldSurveyLevel() {
        return KmuLunaSettings
            .readChoice(
                SHOW_DECIVILISED_WORLDS_AT_SURVEY_LEVEL_FIELD,
                DEFAULT_DECIVILISED_WORLD_SURVEY_LEVEL)
            .resolveSurveyLevel();
    }

    /**
     * @return whether a star system the map would otherwise omit still seeds a cell - bypassing the
     *         map's own admission rule, so a system that is unreachable, unseen or uninhabited gets
     *         geometry like any other; off by default
     */
    public static boolean shouldShowHiddenSystems() {
        return KmuLunaSettings.readBoolean(
            SHOW_HIDDEN_SYSTEMS_FIELD,
            DEFAULT_SHOW_HIDDEN_SYSTEMS);
    }
}
