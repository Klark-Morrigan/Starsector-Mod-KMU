package kmu.maplayers.base.layer;

/**
 * A screen's show-or-hide pick as the rest of the mod reads it: honoured only while that screen has a
 * control able to take a hide back, and read as shown until it has one.
 *
 * <p>What makes that rule necessary is where the control comes from: it is a decoration on chrome this
 * mod does not own, so whether a screen gets one is settled afresh each session and can be settled no.
 * Without the rule, a player who hid the layers in a session that had a control would be left, in one
 * that does not, with the feature switched off and nothing on screen to switch it back on -
 * recoverable only by editing a save.
 *
 * <p>The stored pick itself is never touched by the rule. Hiding is still the player's choice, kept as
 * they left it and honoured again the moment a control exists to reverse it - so a session that could
 * not put the box up costs them nothing beyond that session.
 *
 * <p>Session state rather than saved state: whether the reach works is a fact about this run of the
 * game, decided afresh by whatever the running game's widgets turn out to be, and no answer carried in
 * from a previous run could describe them.
 *
 * <p>Both readings stand down together, since a screen the mod is showing in full cannot also be
 * part-way through dissolving off - a fade left following the stored pick would thin a picture the
 * crisp reading says is wholly on screen.
 */
public final class ControlBackedMapLayerVisibility implements MapLayerVisibility {

    // What a screen with no control of its own reads, whatever it has stored: the whole of its layers
    // on it, which is the state a player can always get out of by other means.
    private static final boolean LAYERS_SHOWN_WITHOUT_A_CONTROL = true;
    private static final float FULLY_SHOWN_WITHOUT_A_CONTROL = 1f;

    // The player's own choice, and where a control's clicks land. Read only once this screen has one.
    private final MapLayerVisibility storedVisibility;

    // Whether a control able to reverse a hide has stood on this screen at all this session. A latch
    // rather than a count of what is standing now: rows are rebuilt on every open of a screen, and a
    // reading that fell back to shown between one row and the next would flash the layers on.
    private boolean hasControlBeenAttached;

    /**
     * @param storedVisibility the screen's own pick, which this reads through and writes straight to
     */
    public ControlBackedMapLayerVisibility(MapLayerVisibility storedVisibility) {
        this.storedVisibility = storedVisibility;
    }

    @Override
    public boolean areLayersShown() {

        if (!hasControlBeenAttached) {
            return LAYERS_SHOWN_WITHOUT_A_CONTROL;
        }
        return storedVisibility.areLayersShown();
    }

    @Override
    public void showLayers(boolean areLayersShown) {
        // Written through whatever the rule reads, because the rule is about what the mod acts on and
        // not about what the player picked: a choice dropped here would be a choice made twice.
        storedVisibility.showLayers(areLayersShown);
    }

    @Override
    public float resolveShownFade() {

        if (!hasControlBeenAttached) {
            return FULLY_SHOWN_WITHOUT_A_CONTROL;
        }
        return storedVisibility.resolveShownFade();
    }

    /**
     * Records that a control able to reverse a hide now stands on this screen, from which point its
     * stored pick is what the mod acts on. Idempotent, since a screen reopened stands a fresh control
     * on its rebuilt row and says so again.
     */
    public void recordControlAttached() {
        hasControlBeenAttached = true;
    }

    /**
     * @return the pick underneath, for a control to show and to move. A control is what lifts the rule
     *         above rather than something subject to it, so it is bound to the player's own choice: one
     *         seeded from the reading would report the layers as shown over a save that holds them
     *         hidden, and stay at odds with them until it was used twice
     */
    public MapLayerVisibility getStoredVisibility() {
        return storedVisibility;
    }

    /**
     * Returns this screen to the reading a run that has never put a control up gives, whatever it has
     * stored.
     *
     * <p>The latch is the one thing here a run accumulates, and on its own it only ever goes one way.
     * That is wrong wherever a control can be taken away again, and it can be taken away by two
     * different things: the arrangement holding the picks being torn down under it - the picks belong
     * to a sector while the screens holding them are the process's - and the player closing the switch
     * that permits the reach at all. Both leave a screen with no way to reverse a hide, which is
     * exactly what the rule above exists to keep from happening, so both owe this.
     *
     * <p>The stored pick is untouched, this being about whether the rule acts on it rather than about
     * what it holds.
     */
    public void forgetControlAttached() {
        hasControlBeenAttached = false;
    }
}
