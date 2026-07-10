package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.settings.KmuLunaSettings;

/**
 * The shared per-save recede state: whether background ground is muted (dimmed) and whether it is
 * desaturated (recoloured to the desaturation profile), plus how those two toggles resolve into a
 * receded bloc's {@link BlocStyleAdjustment}. One toggle set backs every context that recedes
 * ground, so a flip anywhere moves it everywhere and a receded bloc reads identically wherever the
 * recede is applied - the recede is consistent by construction rather than by each context copying
 * the same rule.
 *
 * <p>Sidebar-only: the toggles are driven solely by the overlay's tab-panel checkboxes, never a
 * settings-screen control, so they persist in sector memory (each save keeps its own choice and it
 * survives reload) rather than as LunaLib settings fields - every LunaLib field would render on a
 * settings tab. The supplementary tuning - how far Mute dims - stays a LunaLib knob, since that is a
 * screen control.
 *
 * <p>Both default off when a save holds no choice yet, preserving the un-receded look: full-colour
 * blocs over an un-dimmed, un-recoloured background.
 */
public final class RecedePreferences {
    // Save-serialised keys for the two toggles; frozen once shipped, since renaming one silently
    // resets every existing save's choice to off. Absent until the player first flips the matching
    // sidebar checkbox, which the read then reports as off.
    private static final String MUTE_KEY = "$kmu_political_recede_mute";
    private static final String DESATURATE_KEY = "$kmu_political_recede_desaturate";

    // The keys these two shipped under while the recede lived only in the alliances view. A
    // pre-rename save still holds these, so migrateLegacyKeys carries each stored choice into the
    // current key once on load and sheds the dead key rather than letting the toggle silently reset.
    private static final String LEGACY_MUTE_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String LEGACY_DESATURATE_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    private RecedePreferences() {
    }

    /**
     * @return whether receded ground dims by the muted-opacity modifier; false before a save exists
     *         or when the toggle was never set
     */
    public static boolean isMuted() {
        return readToggle(MUTE_KEY);
    }

    /**
     * @return whether receded ground recolours to the desaturation profile; false before a save
     *         exists or when the toggle was never set
     */
    public static boolean isDesaturated() {
        return readToggle(DESATURATE_KEY);
    }

    /**
     * Sets whether receded ground dims, persisting the choice in this save and repainting the
     * overlay so the flip shows at once.
     *
     * @param isMuted the new Mute state, as the sidebar checkbox reads it
     */
    public static void setMuted(boolean isMuted) {
        writeToggle(MUTE_KEY, isMuted);
    }

    /**
     * Sets whether receded ground recolours to the desaturation profile, persisting the choice in
     * this save and repainting the overlay so the flip shows at once.
     *
     * @param shouldDesaturate the new Desaturate state, as the sidebar checkbox reads it
     */
    public static void setDesaturated(boolean shouldDesaturate) {
        writeToggle(DESATURATE_KEY, shouldDesaturate);
    }

    /**
     * Resolves how a receded bloc draws under the current toggles: Mute scales its opacity by the
     * modifier (0 hides it, 1 leaves it), Desaturate recolours it, and the two combine. Both off is
     * {@link BlocStyleAdjustment#NONE}, so a bloc no context recedes draws untouched.
     *
     * @return the styling every receded bloc takes this pass, resolved in one place so the recede
     *         reads the same in every context that applies it
     */
    public static BlocStyleAdjustment resolveRecedeAdjustment() {
        // Mute scales by the Luna modifier reading, not a constant, so the screen knob tunes how far
        // a receded bloc dims; unread while Mute is off, which leaves opacity untouched at 1. The
        // getter keeps its shipped "alliance" spelling - a frozen LunaLib field id, not a claim
        // about who reads it.
        double opacityMultiplier = isMuted()
                ? KmuLunaSettings.getPoliticalMapAllianceMutedOpacityModifier()
                : 1.0;
        return new BlocStyleAdjustment(opacityMultiplier, isDesaturated());
    }

    /**
     * Carries each toggle's pre-rename stored choice into its current key and sheds the dead key -
     * the self-heal for saves written while the recede lived only in the alliances view, under the
     * old key spelling. A no-op before the sector exists, and per key a no-op once the current key
     * holds a value (already migrated, or the player has since flipped the toggle) or when no legacy
     * key is stored. Call once on game load.
     */
    public static void migrateLegacyKeys() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        migrateLegacyKey(memory, LEGACY_MUTE_KEY, MUTE_KEY);
        migrateLegacyKey(memory, LEGACY_DESATURATE_KEY, DESATURATE_KEY);
    }

    // Reads a toggle from sector memory, false before the sector exists (no save to read) or when
    // the key was never written - the original un-receded look either way. MemoryAPI.getBoolean
    // already returns false for an absent key, so the null-sector guard is the only extra check.
    private static boolean readToggle(String key) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        return memory != null && memory.getBoolean(key);
    }

    // Persists a toggle to sector memory and requests a style refresh so every view that recedes
    // ground rebuilds its drawables with the new choice. A no-op before the sector exists (no save
    // to write into, nothing painting to repaint). The value is written permanently - it is per-save
    // state that must survive reload - and the refresh stands in for settingsRevision, which these
    // sidebar-only toggles never bump since they are not LunaLib fields.
    private static void writeToggle(String key, boolean value) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(key, value);
        PoliticalMapRefresh.requestRecedeStyleRefresh();
    }

    // Carries one toggle's stored boolean from its legacy key to its current key, then unsets the
    // legacy key so the migration runs once and nothing stale lingers. Skips when the current key
    // already holds a value (already migrated, or a fresh choice not to overwrite) or when the
    // legacy key was never written. contains is the presence gate, not getBoolean, so a stored
    // false migrates as faithfully as a stored true.
    private static void migrateLegacyKey(MemoryAPI memory, String legacyKey, String currentKey) {
        if (memory.contains(currentKey) || !memory.contains(legacyKey)) {
            return;
        }
        memory.set(currentKey, memory.getBoolean(legacyKey));
        memory.unset(legacyKey);
    }
}
