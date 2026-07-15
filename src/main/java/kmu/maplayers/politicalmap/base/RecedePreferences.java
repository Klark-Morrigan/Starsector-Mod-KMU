package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;
import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.settings.KmuLunaSettings;

/**
 * One receding context's per-save state: whether its background ground is muted (dimmed) and whether
 * it is desaturated (recoloured to the desaturation profile), plus how those two toggles resolve into
 * a receded bloc's {@link BlocStyleAdjustment}. It is instantiable so each context that recedes ground
 * owns its own toggle set rather than sharing one - the filter recede (the "rest of the sector" behind
 * a spotlight) and the alliances view's non-allied recede are separate grounds a player tunes
 * independently, so each holds its own instance over its own memory keys. Within one instance the two
 * toggles still resolve through a single rule, so a receded bloc reads the same everywhere that
 * instance is applied.
 *
 * <p>Sidebar-only: the toggles are driven solely by the overlay's tab-panel checkboxes, never a
 * settings-screen control, so they persist in sector memory (each save keeps its own choice and it
 * survives reload) rather than as LunaLib settings fields - every LunaLib field would render on a
 * settings tab. The supplementary tuning - how far Mute dims - stays a LunaLib knob shared across
 * every set, since that is a screen control.
 *
 * <p>Both toggles default off when a save holds no choice yet, preserving the un-receded look: full-
 * colour blocs over an un-dimmed, un-recoloured background.
 */
public final class RecedePreferences {

    // The filter recede: the "rest of the sector" that fades behind a spotlighted bloc, one set shared
    // across both views while a filter is active since the receded ground is the same concept under
    // either. The pre-split save's un-prefixed shared choice carries into these keys once on load.
    public static final RecedePreferences FILTER = new RecedePreferences(
            "$kmu_political_filter_recede_mute",
            "$kmu_political_filter_recede_desaturate");

    // The alliances view's non-allied recede: every faction outside an alliance, receded so the
    // alliances read as the figure. Independent of the filter recede, so it owns its own keys and
    // starts fresh - no pre-split choice migrates into it.
    public static final RecedePreferences ALLIANCE_NON_ALLIED = new RecedePreferences(
            "$kmu_political_alliance_recede_mute",
            "$kmu_political_alliance_recede_desaturate");

    // The keys the recede shipped under while it was one shared set spanning every context. No
    // instance reads them now: migrateSharedKeysIntoFilterSet carries each stored choice into the
    // FILTER set once and sheds it. Frozen so an existing save's carry stays faithful.
    private static final String SHARED_MUTE_KEY = "$kmu_political_recede_mute";
    private static final String SHARED_DESATURATE_KEY = "$kmu_political_recede_desaturate";

    // The keys the toggles shipped under while the recede lived only in the alliances view, the
    // oldest spelling. migrateLegacyKeys carries each into the shared keys, which the split migration
    // then carries on into the FILTER set, so even the oldest save reaches the current key on load.
    private static final String LEGACY_MUTE_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String LEGACY_DESATURATE_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    // This set's two frozen memory keys and the flags over them, defaulting off so an untouched save
    // reads un-receded. Instance fields, not statics, so each set backs a distinct ground; renaming a
    // key silently resets every existing save's choice for that set, so they stay stable once shipped.
    private final String muteKey;
    private final String desaturateKey;
    private final SectorMemoryFlag muteFlag;
    private final SectorMemoryFlag desaturateFlag;

    // Package-private: the only sets are the two constants above, each naming its own frozen keys, so
    // no other code composes a recede set with keys of its own.
    RecedePreferences(String muteKey, String desaturateKey) {
        this.muteKey = muteKey;
        this.desaturateKey = desaturateKey;
        this.muteFlag = new SectorMemoryFlag(muteKey, false);
        this.desaturateFlag = new SectorMemoryFlag(desaturateKey, false);
    }

    /**
     * @return whether this set's receded ground dims by the muted-opacity modifier; false before a
     *         save exists or when the toggle was never set
     */
    public boolean isMuted() {
        return muteFlag.isSet();
    }

    /**
     * @return whether this set's receded ground recolours to the desaturation profile; false before a
     *         save exists or when the toggle was never set
     */
    public boolean isDesaturated() {
        return desaturateFlag.isSet();
    }

    /**
     * Sets whether this set's receded ground dims, persisting the choice in this save and repainting
     * the overlay so the flip shows at once.
     *
     * @param isMuted the new Mute state, as the sidebar checkbox reads it
     */
    public void setMuted(boolean isMuted) {
        // Repaint only on a real write: before the sector exists the flag no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which these sidebar-only toggles never move since they are not LunaLib
        // fields. The revision is one coarse signal every set shares, so a consumer only draws the
        // ground it owns even though any set's flip advances it.
        if (muteFlag.set(isMuted)) {
            PoliticalMapRefresh.requestRecedeStyleRefresh();
        }
    }

    /**
     * Sets whether this set's receded ground recolours to the desaturation profile, persisting the
     * choice in this save and repainting the overlay so the flip shows at once.
     *
     * @param shouldDesaturate the new Desaturate state, as the sidebar checkbox reads it
     */
    public void setDesaturated(boolean shouldDesaturate) {
        if (desaturateFlag.set(shouldDesaturate)) {
            PoliticalMapRefresh.requestRecedeStyleRefresh();
        }
    }

    /**
     * Resolves how a bloc this set recedes draws under the current toggles: Mute scales its opacity by
     * the modifier (0 hides it, 1 leaves it), Desaturate recolours it, and the two combine. Both off is
     * {@link BlocStyleAdjustment#NONE}, so a bloc this set does not recede draws untouched.
     *
     * @return the styling every bloc this set recedes takes this pass, resolved in one place so the
     *         recede reads the same in every context that applies this set
     */
    public BlocStyleAdjustment resolveRecedeAdjustment() {
        // Mute scales by the Luna modifier reading, not a constant, so the screen knob tunes how far a
        // receded bloc dims; unread while Mute is off, which leaves opacity untouched at 1. The getter
        // keeps its shipped "alliance" spelling - a frozen LunaLib field id shared by every set, not a
        // claim about which set reads it.
        double opacityMultiplier = isMuted()
                ? KmuLunaSettings.getPoliticalMapAllianceMutedOpacityModifier()
                : 1.0;
        return new BlocStyleAdjustment(opacityMultiplier, isDesaturated());
    }

    /**
     * Carries each toggle's pre-rename stored choice from the oldest alliance-view keys into the shared
     * keys and sheds the dead key - the self-heal for saves written while the recede lived only in the
     * alliances view. A no-op before the sector exists, and per key a no-op once the shared key holds a
     * value or when no legacy key is stored. The shared keys are themselves migrated on into the FILTER
     * set by {@link #migrateSharedKeysIntoFilterSet}, which must run after this. Call once on game load.
     */
    public static void migrateLegacyKeys() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        migrateStoredChoice(memory, LEGACY_MUTE_KEY, SHARED_MUTE_KEY);
        migrateStoredChoice(memory, LEGACY_DESATURATE_KEY, SHARED_DESATURATE_KEY);
    }

    /**
     * Carries the pre-split shared recede choice into the FILTER set's keys and sheds the shared keys -
     * the self-heal for saves written while the recede was one set spanning every context. The filter
     * is where the recede was most visible, so its choice lands there and the alliance set starts
     * fresh. A no-op before the sector exists, and per key a no-op once the filter key holds a value or
     * when no shared key is stored. Runs after {@link #migrateLegacyKeys}, so an oldest-spelling save
     * whose choice that heal has just carried into the shared keys is carried on into the filter keys
     * here in the same load. Call once on game load.
     */
    public static void migrateSharedKeysIntoFilterSet() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        migrateStoredChoice(memory, SHARED_MUTE_KEY, FILTER.muteKey);
        migrateStoredChoice(memory, SHARED_DESATURATE_KEY, FILTER.desaturateKey);
    }

    // Carries one toggle's stored boolean from a source key to a target key, then unsets the source so
    // the migration runs once and nothing stale lingers. Skips when the target already holds a value
    // (already migrated, or a fresh choice not to overwrite) or when the source was never written.
    // contains is the presence gate, not getBoolean, so a stored false migrates as faithfully as a
    // stored true.
    private static void migrateStoredChoice(MemoryAPI memory, String sourceKey, String targetKey) {
        if (memory.contains(targetKey) || !memory.contains(sourceKey)) {
            return;
        }
        memory.set(targetKey, memory.getBoolean(sourceKey));
        memory.unset(sourceKey);
    }
}
