package kmu.settings;

/**
 * The market condition manager's own LunaLib knob.
 *
 * <p>Its reader is the condition manager rather than a map layer, so its one switch sits apart from
 * the map's knobs.
 *
 * <p>Its tab keeps the plain name "Market Condition Manager (MCM)". The map tabs carry a
 * {@code Map - } prefix to group them for a player hunting a knob, and leaving this one
 * unprefixed is what keeps that prefix meaningful.
 */
public final class KmuMarketConditionSettings {

    // Market-conditions tab: whether the condition picker offers every market
    // condition, or only the planetary ones vanilla treats as hand-placeable.
    private static final String OFFER_ALL_CONDITIONS_FIELD =
        "kmu_mcm_conditions_shouldShowAll";

    // On: the picker is a hands-on condition manager, so it lists every
    // condition unless the player narrows it to vanilla's planetary set. Used only when the
    // setting is read before LunaLib has loaded it, and mirrors the CSV row's defaultValue.
    private static final boolean DEFAULT_OFFER_ALL_CONDITIONS = true;

    private KmuMarketConditionSettings() {
    }

    /**
     * @return whether the market-condition picker offers every condition, or only
     *         the planetary ones vanilla treats as hand-placeable; every condition
     *         includes the non-planetary ones, such as decivilisation. Unread while
     *         the condition manager itself is switched off, the picker being what
     *         reads it
     */
    public static boolean shouldOfferAllConditions() {
        return KmuLunaSettings.readBoolean(
            OFFER_ALL_CONDITIONS_FIELD,
            DEFAULT_OFFER_ALL_CONDITIONS);
    }
}
