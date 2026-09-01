package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.settings.KmuPoliticalMapTerritorySettings;

/**
 * One receding context's per-save state: whether its backdrop is muted (dimmed) and whether
 * it is desaturated (recoloured to the desaturation profile), plus how those two toggles resolve into
 * a receded bloc's {@link ElementStyleAdjustment}. It is instantiable so each context that recedes
 * owns its own toggle set rather than sharing one - the filter recede (the "rest of the sector" behind
 * a spotlight) and the alliances view's non-allied recede are separate backdrops a player tunes
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
 * <p>The two toggles carry different defaults for a save that holds no choice yet: Desaturate starts
 * on, Mute starts off. A spotlight only reads as a spotlight if the rest of the sector visibly gives
 * way, so the recolour is what a pick needs out of the box; dimming is a separate ask a player opts
 * into. A stored choice always outranks the default - clearing a box writes a real {@code false} -
 * so the defaults describe only an untouched save.
 */
public final class RecedePreferences {

    // The filter recede: the "rest of the sector" that fades behind a spotlighted bloc, one set shared
    // across both views while a filter is active since the receded backdrop is the same concept under
    // either.
    public static final RecedePreferences FILTER = new RecedePreferences(
        "$kmu_political_filter_recede_mute",
        "$kmu_political_filter_recede_desaturate");

    // The alliances view's non-allied recede: every faction outside an alliance, receded so the
    // alliances read as the figure. Independent of the filter recede, so it owns its own keys.
    public static final RecedePreferences ALLIANCE_NON_ALLIED = new RecedePreferences(
        "$kmu_political_alliance_recede_mute",
        "$kmu_political_alliance_recede_desaturate");

    // What each toggle reads as while its key is absent, one constant per toggle rather than a shared
    // literal: the two answer different questions, so they are free to differ and every set declared
    // here takes the same pair. Desaturate on is what makes a spotlight legible from the first click.
    private static final boolean MUTE_DEFAULT = false;
    private static final boolean DESATURATE_DEFAULT = true;

    // The flags over this set's two frozen memory keys, each carrying its toggle's own default.
    // Instance fields, not statics, so each set backs a distinct backdrop; renaming a key silently
    // resets every existing save's choice for that set, so they stay stable once shipped.
    private final SectorMemoryFlag muteFlag;
    private final SectorMemoryFlag desaturateFlag;

    // Package-private: the only sets are the two constants above, each naming its own frozen keys, so
    // no other code composes a recede set with keys of its own.
    RecedePreferences(String muteKey, String desaturateKey) {
        this.muteFlag = new SectorMemoryFlag(muteKey, MUTE_DEFAULT);
        this.desaturateFlag = new SectorMemoryFlag(desaturateKey, DESATURATE_DEFAULT);
    }

    /**
     * @return whether this set's receded backdrop dims by the muted-opacity modifier; false before a
     *         save exists or while the toggle is untouched, since Mute defaults off
     */
    public boolean isMuted() {
        return muteFlag.isSet();
    }

    /**
     * @return whether this set's receded backdrop recolours to the desaturation profile; true before a
     *         save exists or while the toggle is untouched, since Desaturate defaults on
     */
    public boolean isDesaturated() {
        return desaturateFlag.isSet();
    }

    /**
     * Sets whether this set's receded backdrop dims, persisting the choice in this save and repainting
     * the overlay so the flip shows at once.
     *
     * @param isMuted the new Mute state, as the sidebar checkbox reads it
     * @param board   the refresh board of the sector whose checkbox was flipped, raised on so that
     *                sector's backdrop repaints
     */
    public void setMuted(boolean isMuted, MapLayerRefreshBoard board) {
        // Repaint only on a real write: before the sector exists the flag no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which these sidebar-only toggles never move since they are not LunaLib
        // fields. The revision is one coarse signal every set shares, so a consumer only draws the
        // backdrop it owns even though any set's flip advances it.
        if (muteFlag.set(isMuted)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);
        }
    }

    /**
     * Sets whether this set's receded backdrop recolours to the desaturation profile, persisting the
     * choice in this save and repainting the overlay so the flip shows at once.
     *
     * @param shouldDesaturate the new Desaturate state, as the sidebar checkbox reads it
     * @param board            the refresh board of the sector whose checkbox was flipped, raised on
     *                         so that sector's backdrop repaints
     */
    public void setDesaturated(boolean shouldDesaturate, MapLayerRefreshBoard board) {
        if (desaturateFlag.set(shouldDesaturate)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);
        }
    }

    /**
     * Resolves how a bloc this set recedes draws under the current toggles: Mute scales its opacity by
     * the modifier (0 hides it, 1 leaves it), Desaturate recolours it, and the two combine. Both off is
     * {@link ElementStyleAdjustment#NONE}, so a bloc this set does not recede draws untouched.
     *
     * @return the styling every bloc this set recedes takes this pass, resolved in one place so the
     *         recede reads the same in every context that applies this set
     */
    public ElementStyleAdjustment resolveRecedeAdjustment() {
        // Mute scales by the Luna modifier reading, not a constant, so the screen knob tunes how far a
        // receded bloc dims; unread while Mute is off, which leaves opacity untouched at 1. The getter
        // keeps its shipped "alliance" spelling - a frozen LunaLib field id shared by every set, not a
        // claim about which set reads it.
        double opacityMultiplier = isMuted()
            ? KmuPoliticalMapTerritorySettings.getPoliticalMapAllianceMutedOpacityModifier()
            : 1.0;
        return new ElementStyleAdjustment(opacityMultiplier, isDesaturated());
    }
}
