package kmu.settings;

import java.util.List;

/**
 * The ids of settings KMU has shipped and since withdrawn, and the one-time sweep that takes their
 * orphaned values out of the player's stored settings.
 *
 * <p>LunaLib only ever adds. On load it seeds a default for every row the shipped table declares
 * and prunes nothing, so a field dropped from {@code LunaSettings.csv} keeps its stored value
 * indefinitely - and would hand it straight back to any later field that reused the id. The sweep
 * is the settings-side counterpart of unsetting a dead save key, and its save-side twin is
 * {@code PoliticalMapSaveMigrations}.
 *
 * <p>A class of its own rather than a list inside {@link KmuLunaSettings}, because a retired id is
 * the opposite of what that class and its three siblings hold. Those are live field ids paired with
 * the fallback and the accessor that read them; a retired id has no reader at all, and keeping one
 * among them would be a dead constant sitting where every neighbour is load-bearing. Nor does it
 * belong with the feature that used to read it, for the same reason: the feature is gone, and a
 * per-feature list could not promise the sweep is exhaustive across the mod.
 *
 * <p>An id is listed from the moment its row leaves the table and stays listed. Dropped too early
 * it leaves the value behind for good on every install that has not launched since; kept forever
 * it costs one lookup per launch, which is what makes erring towards keeping it the cheap mistake.
 */
public final class KmuRetiredSettings {

    // The uncontested-bands switch, withdrawn when an uncontested cell was made to band
    // unconditionally: the only question left about one is how long its runs are, which the
    // shortening beside it already answers.
    private static final List<String> RETIRED_FIELD_IDS = List.of(
        "kmu_map_politics_visuals_presenceRibbons_uncontestedEnabled");

    private KmuRetiredSettings() {
    }

    /**
     * Takes every withdrawn field's value out of the player's stored settings.
     *
     * <p>Call once per launch, from {@code KMU_ModPlugin.onApplicationLoad}. Each removal is a
     * no-op where the key is absent - a fresh install, or an install this has already swept - so
     * every launch after the first pays a lookup per retired id and no disk write.
     */
    public static void clearRetiredSettings() {
        for (var fieldId : RETIRED_FIELD_IDS) {
            KmuLunaSettings.clearSetting(fieldId);
        }
    }
}
