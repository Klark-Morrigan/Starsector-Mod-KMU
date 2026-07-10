package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;

/**
 * The two per-save toggles that decide how the alliances view recedes the factions outside
 * every alliance: whether a non-allied faction is muted (dimmed) and whether it is
 * desaturated (recoloured to the desaturation profile). {@link AlliancesView} reads them
 * per pass to resolve a non-allied bloc's {@link BlocStyleAdjustment}.
 *
 * <p>Sidebar-only: the toggles are driven solely by the overlay's tab-panel checkboxes, never
 * a settings-screen control, so they persist in sector memory (each save keeps its own choice
 * and it survives reload) rather than as LunaLib settings fields - every LunaLib field would
 * render on a settings tab. Their supplementary tuning - how far Mute dims, which profile
 * Desaturate targets - are LunaLib settings, since those are screen knobs.
 *
 * <p>Both default off when a save holds no choice yet, preserving the alliances view's original
 * look: full-colour alliances over an un-dimmed, un-recoloured non-allied ground.
 */
public final class AllianceStylePreferences {
    // Save-serialised keys for the two toggles; frozen once shipped, since renaming one silently
    // resets every existing save's choice to off. Absent until the player first flips the
    // matching sidebar checkbox, which the read then reports as off.
    private static final String MUTE_NON_ALLIED_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String DESATURATE_NON_ALLIED_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    private AllianceStylePreferences() {
    }

    /**
     * @return whether the alliances view dims every non-allied faction by the muted-opacity
     *         modifier; false before a save exists or when the toggle was never set
     */
    public static boolean isNonAlliedMuted() {
        return readToggle(MUTE_NON_ALLIED_KEY);
    }

    /**
     * @return whether the alliances view recolours every non-allied faction to the desaturation
     *         profile; false before a save exists or when the toggle was never set
     */
    public static boolean isNonAlliedDesaturated() {
        return readToggle(DESATURATE_NON_ALLIED_KEY);
    }

    /**
     * Sets whether the alliances view dims every non-allied faction, persisting the choice in this
     * save and repainting the overlay so the flip shows at once.
     *
     * @param isMuted the new Mute state, as the sidebar checkbox reads it
     */
    public static void setNonAlliedMuted(boolean isMuted) {
        writeToggle(MUTE_NON_ALLIED_KEY, isMuted);
    }

    /**
     * Sets whether the alliances view recolours every non-allied faction to the desaturation
     * profile, persisting the choice in this save and repainting the overlay so the flip shows at
     * once.
     *
     * @param shouldDesaturate the new Desaturate state, as the sidebar checkbox reads it
     */
    public static void setNonAlliedDesaturated(boolean shouldDesaturate) {
        writeToggle(DESATURATE_NON_ALLIED_KEY, shouldDesaturate);
    }

    // Reads a toggle from sector memory, false before the sector exists (no save to read) or when
    // the key was never written - the original un-receded look either way. MemoryAPI.getBoolean
    // already returns false for an absent key, so the null-sector guard is the only extra check.
    private static boolean readToggle(String key) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        return memory != null && memory.getBoolean(key);
    }

    // Persists a toggle to sector memory and requests a style refresh so the alliances view rebuilds
    // its drawables with the new choice. A no-op before the sector exists (no save to write into, and
    // nothing painting to repaint). The value is written permanently - it is per-save state that must
    // survive reload - and the refresh stands in for settingsRevision, which these sidebar-only
    // toggles never bump since they are not LunaLib fields.
    private static void writeToggle(String key, boolean value) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(key, value);
        PoliticalMapRefresh.requestAllianceStyleRefresh();
    }
}
