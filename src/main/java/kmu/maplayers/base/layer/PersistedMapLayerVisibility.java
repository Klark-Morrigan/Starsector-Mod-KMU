package kmu.maplayers.base.layer;

import kmlib.animation.ClockedTraverseFraction;
import kmlib.animation.TraverseDurations;
import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.settings.KmuMapSidebarSettings;

import java.util.function.Supplier;

/**
 * A screen's show-or-hide pick persisted in sector memory, so the pick survives reload, together with the
 * ramp the paint rides between the two states. Each screen holds its own instance under its own scope, so
 * the picks stay independent - hiding on one screen writes only its key - while both screens persist the
 * same way, for one consistent behaviour across screens. A save holding no pick yet resolves to shown,
 * which is what a player who has never touched the control is entitled to see.
 *
 * <p>The base key is held here rather than supplied, because it is this pick's identity and the same on
 * every screen; what a caller chooses is the screen, and the two compose to the slot.
 *
 * <p>The ramp is KMLib's {@link ClockedTraverseFraction}: derived from how long ago the pick changed rather
 * than advanced by a per-frame tick, off real time because the map screen runs while the campaign clock
 * does not, and reversed from where it stands rather than from an end. Its curve is the same one every
 * other movement in this chrome answers to - the sidebar fold this ramp's default pace is set to match
 * included, so the two really do agree rather than only starting and finishing together.
 *
 * <p>The ramp is read toward the stored pick at every reading rather than toward the end it was last
 * turned to, so a save loaded with the layers hidden reads hidden on its first frame rather than
 * dissolving away a picture it never drew.
 */
public final class PersistedMapLayerVisibility
        implements MapLayerVisibility {

    // Save-serialised identity of this pick, before the screen's own segment; frozen once shipped, since
    // renaming it returns every existing save to shown.
    private static final String LAYERS_SHOWN_KEY = "$kmu_political_layers_shown";

    // What a save holding no pick yet resolves to. Shown, because the layers are what the feature is for:
    // a player who has never reached the control has not asked for them to be gone.
    private static final boolean LAYERS_SHOWN_BY_DEFAULT = true;

    // The stored pick and the default an untouched save resolves to, under the slot this pick's base key
    // composes to for the screen it was built for.
    private final SectorMemoryFlag areLayersShownFlag;

    // The ramp between the two picks. Session state rather than saved: a fade is what the eye is in the
    // middle of, and no reload is in the middle of anything.
    private final ClockedTraverseFraction shownFade;

    /**
     * @param memoryScope the screen whose pick this is; its slot is this pick's base key resolved under it
     */
    public PersistedMapLayerVisibility(ScreenMemoryScope memoryScope) {

        // One knob for both ways: a show is a hide undone by the same control, not a letting go.
        this(memoryScope, () -> TraverseDurations.createSymmetric(
            KmuMapSidebarSettings.getMapLayerHideFadeSeconds()));
    }

    /**
     * @param memoryScope   the screen whose pick this is
     * @param readDurations how long the fade takes each way, read at every reading so a player moving the
     *                      knob mid-fade is answered by it; a non-positive one makes that way a cut
     */
    PersistedMapLayerVisibility(ScreenMemoryScope memoryScope, Supplier<TraverseDurations> readDurations) {

        this.areLayersShownFlag = new SectorMemoryFlag(
            memoryScope.resolveKeyFor(LAYERS_SHOWN_KEY),
            LAYERS_SHOWN_BY_DEFAULT);

        this.shownFade = new ClockedTraverseFraction(readDurations);
    }

    @Override
    public boolean areLayersShown() {
        return areLayersShownFlag.isSet();
    }

    @Override
    public void showLayers(boolean areLayersShown) {

        // Nothing to record where the save already reads this way, which is every call but the one a
        // control actually flips on. Restarting the ramp for an unchanged pick would replay a dissolve
        // the player asked for once.
        if (areLayersShown == areLayersShownFlag.isSet()) {
            return;
        }

        // A write dropped for want of a sector is not a flip: the pick still reads the old way, so
        // setting off from here would run a fade towards a state nothing stored.
        if (!areLayersShownFlag.set(areLayersShown)) {
            return;
        }

        shownFade.turnTowardEnd(areLayersShown);
    }

    @Override
    public float resolveShownFade() {
        return shownFade.resolveEasedValue(areLayersShownFlag.isSet());
    }
}
