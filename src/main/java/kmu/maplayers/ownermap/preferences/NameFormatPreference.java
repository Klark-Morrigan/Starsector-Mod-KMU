package kmu.maplayers.ownermap.preferences;

import kmlib.persistence.PersistedChoices;
import kmlib.starsector.memory.AddressedMemoryString;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * How cluster labels spell their holders' names - full, short, or not at all - persisted per save and per
 * screen. View-agnostic: every view of a layer labels its clusters the same way, so one stored choice
 * backs them all rather than each view holding its own.
 *
 * <p>One per layer, over a key the layer names, so two owner-painted layers offering the radio store
 * their choices apart. The layer also names the radio's caption, since what its owners are called is
 * the layer's word.
 *
 * <p>Per screen because the two panels frame the sector at different sizes, so the form that fits one is
 * not the form that fits the other.
 *
 * <p>Sidebar-only, driven by the overlay's Full/Short/No radio, for the reason
 * {@link OwnerMapBodyPreferences} gives.
 *
 * <p>Unlike the sidebar's list controls this fires a refresh: the choice decides both whether the
 * labels build at all and the text each is fitted and wrapped against, which is baked into the
 * drawables at rebuild, so a pick has to invalidate them to show.
 */
public final class NameFormatPreference {

    // Full names by default: the long form is the authored name a player recognises, and the short
    // form is the deliberate trade for fitting a tighter cluster at a larger font.
    private static final OwnerNameFormatChoice DEFAULT_NAME_FORMAT = OwnerNameFormatChoice.FULL;

    // Absent until the player first picks a format on that screen, which the read resolves to the
    // default. Held once per screen under the layer's key.
    private final AddressedMemoryString selectedNameFormat;

    // The localisation key of the radio's caption, the layer's own.
    private final String captionKey;

    /**
     * @param nameFormatKey the sector-memory key the choice is stored under, before each screen's own
     *                      segment; save-serialised, so a layer freezes it once shipped
     * @param captionKey    the {@code KmuStringKeys} key of the radio's caption, naming the layer's
     *                      owners in its own word
     */
    public NameFormatPreference(String nameFormatKey, String captionKey) {
        this.selectedNameFormat = new AddressedMemoryString(nameFormatKey);
        this.captionKey = captionKey;
    }

    /**
     * @return the {@code KmuStringKeys} key of the radio's caption
     */
    public String getCaptionKey() {
        return captionKey;
    }

    /**
     * @param memoryScope the screen whose choice is read
     * @return how that screen's cluster labels spell their holders' names, including whether they draw
     *         at all; full names before a save exists or when no choice was ever picked there
     */
    public OwnerNameFormatChoice getSelectedNameFormat(ScreenMemoryScope memoryScope) {

        return PersistedChoices.fromKey(
            OwnerNameFormatChoice.values(),
            selectedNameFormat.get(memoryScope),
            DEFAULT_NAME_FORMAT);
    }

    /**
     * Persists the chosen name format in this save, against the screen it was picked on, and restyles the
     * overlay so the labels re-fit (or disappear) at once. A no-op before the sector exists, since there
     * is no save to write into yet.
     *
     * @param memoryScope the screen whose panel made the pick
     * @param choice      how cluster labels should spell their holders' names, or that they draw none
     * @param board       the refresh board of the sector whose radio made the pick, raised on so that
     *                    sector's labels re-fit
     */
    public void selectNameFormat(
            ScreenMemoryScope memoryScope,
            OwnerNameFormatChoice choice,
            MapLayerRefreshBoard board) {

        // Repaint only on a real write (see OwnerMapBodyPreferences).
        if (selectedNameFormat.set(memoryScope, choice.persistenceKey())) {
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
        }
    }
}
