package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;

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

    // Reads a toggle from sector memory, false before the sector exists (no save to read) or when
    // the key was never written - the original un-receded look either way. MemoryAPI.getBoolean
    // already returns false for an absent key, so the null-sector guard is the only extra check.
    private static boolean readToggle(String key) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        return memory != null && memory.getBoolean(key);
    }
}
