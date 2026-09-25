package kmu.maplayers.ownermap.preferences;

/**
 * Body preferences over test keys, for a suite that needs a layer's preferences without naming a
 * layer.
 *
 * <p>Real preferences rather than doubles: with no sector in reach each reads as a save that has never
 * been touched - full names, no uninhabited outline, the recede defaults - which is what a suite about
 * something else wants beneath it. A suite whose subject is one of the picks states it through the
 * sector memory the keys below compose into.
 */
public final class OwnerMapBodyPreferencesFixtures {

    /** The name-format key the fixture stores under, before each screen's own segment. */
    public static final String NAME_FORMAT_KEY = "$test_layer_name_format";

    /** The uninhabited-outline key the fixture stores under. */
    public static final String UNINHABITED_OUTLINE_KEY = "$test_layer_uninhabited_outline";

    /** The filter recede's Mute key. */
    public static final String FILTER_RECEDE_MUTE_KEY = "$test_layer_filter_recede_mute";

    /** The filter recede's Desaturate key. */
    public static final String FILTER_RECEDE_DESATURATE_KEY = "$test_layer_filter_recede_desaturate";

    private OwnerMapBodyPreferencesFixtures() {
    }

    /**
     * @return one layer's body preferences, stored under the test keys above
     */
    public static OwnerMapBodyPreferences createUnderTestKeys() {
        return new OwnerMapBodyPreferences(
            new NameFormatPreference(NAME_FORMAT_KEY),
            new UninhabitedOutlinePreference(UNINHABITED_OUTLINE_KEY),
            new RecedePreferences(FILTER_RECEDE_MUTE_KEY, FILTER_RECEDE_DESATURATE_KEY));
    }
}
