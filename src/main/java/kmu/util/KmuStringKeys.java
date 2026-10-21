package kmu.util;

import kmlib.starsector.strings.StarsectorStrings;

/**
 * KMU's localisation entry point. Holds the category KMU registers in
 * {@code data/strings/strings.json}, the IDs of the strings the whole mod reads,
 * and thin {@link #get(String)} / {@link #format(String, Object...)}
 * accessors that bind that category so call sites never repeat it.
 *
 * <p>Every other string ID lives in the key class of the feature that reads it, as the settings
 * are split by reader: {@link KmuMapStringKeys}, {@link KmuOwnerMapStringKeys},
 * {@link KmuPoliticalMapStringKeys} and {@link KmuMarketConditionStringKeys}. So no package imports
 * more of the wording than the part it draws, and a tier's words stay out of reach of the tiers
 * below it.
 *
 * <p>The lookup and fallback behaviour live in KMLib's
 * {@link StarsectorStrings}; this class is a category-bound shortcut,
 * not a parallel implementation.
 */
public final class KmuStringKeys {

    /** strings.json category namespacing every KMU-owned string. */
    public static final String CATEGORY = "kmu";

    /**
     * What a session loses where a LunaLib settings listener did not register: KMU no longer
     * answering a settings change where the player makes it. "May not take effect until you restart"
     * rather than naming when each does, because the listeners differ - a feature switch is applied
     * again at the next save load, the profiling level only at the next launch - and one sentence
     * covers every one of them. No other mod is named, though one listener follows another mod's
     * settings: the sentence reaches players without that mod too.
     */
    public static final String COMPATIBILITY_LOST_LUNALIB_SETTINGS = "compatibility_lost_lunalib_settings";

    /** What a failed LunaLib binding does not cost, for the notice's optional "No effect" row. */
    public static final String COMPATIBILITY_UNAFFECTED_LUNALIB_SETTINGS =
        "compatibility_unaffected_lunalib_settings";

    private KmuStringKeys() {
    }

    /** Looks up {@code key} under the KMU category. See
     *  {@link StarsectorStrings#get(String, String)} for fallback
     *  semantics. */
    public static String get(String key) {
        return StarsectorStrings.get(CATEGORY, key);
    }

    /** Formats {@code key}'s template against {@code args}. See
     *  {@link StarsectorStrings#format(String, String, Object...)} for
     *  fallback semantics. */
    public static String format(String key, Object... args) {
        return StarsectorStrings.format(CATEGORY, key, args);
    }
}
