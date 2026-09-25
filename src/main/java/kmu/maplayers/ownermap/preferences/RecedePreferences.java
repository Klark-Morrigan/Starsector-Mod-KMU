package kmu.maplayers.ownermap.preferences;

import kmlib.starsector.memory.AddressedMemoryFlag;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.settings.KmuOwnerMapStyleSettings;

/**
 * One receding context's per-save, per-screen state: whether its backdrop is muted (dimmed) and whether
 * it is desaturated (recoloured to the desaturation profile), plus how those two toggles resolve into
 * a receded bloc's {@link ElementStyleAdjustment}. It is instantiable so each context that recedes
 * owns its own toggle set rather than sharing one - a layer's filter recede (the "rest of the sector"
 * behind a spotlight) and a view's own backdrop are separate backdrops a player tunes independently, so
 * each holds its own instance over its own memory keys, which whoever owns the backdrop names. Within
 * one instance the two toggles still resolve through a single rule, so a receded bloc reads the same everywhere that
 * instance is applied.
 *
 * <p>The set names the context and the screen names the panel, so one instance backs both screens
 * without either seeing the other's flips.
 *
 * <p>Sidebar-only, driven by the overlay's tab-panel checkboxes, for the reason
 * {@link OwnerMapBodyPreferences} gives; how far Mute dims stays one LunaLib knob shared across every
 * set.
 *
 * <p>The two toggles carry different defaults for a save that holds no choice yet: Desaturate starts
 * on, Mute starts off. A spotlight only reads as a spotlight if the rest of the sector visibly gives
 * way, so the recolour is what a pick needs out of the box; dimming is a separate ask a player opts
 * into. A stored choice always outranks the default - clearing a box writes a real {@code false} -
 * so the defaults describe only an untouched save.
 */
public final class RecedePreferences {

    // What each toggle reads as while its key is absent, one constant per toggle rather than a shared
    // literal: the two answer different questions, so they are free to differ, and every instance takes
    // the same pair. Desaturate on is what makes a spotlight legible from the first click.
    private static final boolean MUTE_DEFAULT = false;
    private static final boolean DESATURATE_DEFAULT = true;

    // This set's two toggles. Instance fields, not statics, so each set backs a distinct backdrop.
    private final AddressedMemoryFlag muteFlag;
    private final AddressedMemoryFlag desaturateFlag;

    /**
     * @param muteKey       the sector-memory key the Mute toggle is stored under, before each screen's
     *                      own segment
     * @param desaturateKey the key the Desaturate toggle is stored under; both save-serialised, so the
     *                      owner freezes them once shipped - a rename resets every save's choice
     */
    public RecedePreferences(String muteKey, String desaturateKey) {
        this.muteFlag = new AddressedMemoryFlag(muteKey, MUTE_DEFAULT);
        this.desaturateFlag = new AddressedMemoryFlag(desaturateKey, DESATURATE_DEFAULT);
    }

    /**
     * @param memoryScope the screen whose toggle is read
     * @return whether this set's receded backdrop dims by the muted-opacity modifier on that screen;
     *         false before a save exists or while the toggle is untouched, since Mute defaults off
     */
    public boolean isMuted(ScreenMemoryScope memoryScope) {
        return muteFlag.isSet(memoryScope);
    }

    /**
     * @param memoryScope the screen whose toggle is read
     * @return whether this set's receded backdrop recolours to the desaturation profile on that screen;
     *         true before a save exists or while the toggle is untouched, since Desaturate defaults on
     */
    public boolean isDesaturated(ScreenMemoryScope memoryScope) {
        return desaturateFlag.isSet(memoryScope);
    }

    /**
     * Sets whether this set's receded backdrop dims, persisting the choice in this save against the
     * screen the box was flipped on and repainting the overlay so the flip shows at once.
     *
     * @param memoryScope the screen whose panel was flipped
     * @param isMuted     the new Mute state, as the sidebar checkbox reads it
     * @param board       the refresh board of the sector whose checkbox was flipped, raised on so that
     *                    sector's backdrop repaints
     */
    public void setMuted(ScreenMemoryScope memoryScope, boolean isMuted, MapLayerRefreshBoard board) {
        // Repaint only on a real write (see OwnerMapBodyPreferences). The signal is one coarse
        // revision every set shares, so a consumer only draws the backdrop it owns even though any
        // set's flip advances it.
        if (muteFlag.set(memoryScope, isMuted)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);
        }
    }

    /**
     * Sets whether this set's receded backdrop recolours to the desaturation profile, persisting the
     * choice in this save against the screen the box was flipped on and repainting the overlay so the
     * flip shows at once.
     *
     * @param memoryScope      the screen whose panel was flipped
     * @param shouldDesaturate the new Desaturate state, as the sidebar checkbox reads it
     * @param board            the refresh board of the sector whose checkbox was flipped, raised on
     *                         so that sector's backdrop repaints
     */
    public void setDesaturated(
            ScreenMemoryScope memoryScope,
            boolean shouldDesaturate,
            MapLayerRefreshBoard board) {

        if (desaturateFlag.set(memoryScope, shouldDesaturate)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);
        }
    }

    /**
     * Resolves how a bloc this set recedes draws under one screen's toggles: Mute scales its opacity by
     * the modifier (0 hides it, 1 leaves it), Desaturate recolours it, and the two combine. Both off is
     * {@link ElementStyleAdjustment#NONE}, so a bloc this set does not recede draws untouched.
     *
     * @param memoryScope the screen being painted for, whose panel holds the two toggles
     * @return the styling every bloc this set recedes takes this pass, resolved in one place so the
     *         recede reads the same in every context that applies this set
     */
    public ElementStyleAdjustment resolveRecedeAdjustment(ScreenMemoryScope memoryScope) {
        // Mute scales by the Luna modifier reading, not a constant, so the screen knob tunes how far a
        // receded bloc dims; unread while Mute is off, which leaves opacity untouched at 1. One knob
        // for every set: its LunaLib field ID is a frozen shipped key, and its spelling says nothing
        // about which set reads it.
        double opacityMultiplier = isMuted(memoryScope)
            ? KmuOwnerMapStyleSettings.getOwnerMapMutedOpacityModifier()
            : 1.0;
        return new ElementStyleAdjustment(opacityMultiplier, isDesaturated(memoryScope));
    }
}
