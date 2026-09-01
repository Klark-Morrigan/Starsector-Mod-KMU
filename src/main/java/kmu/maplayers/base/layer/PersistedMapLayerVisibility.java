package kmu.maplayers.base.layer;

import kmlib.math.ranges.Ranges;
import kmlib.profiling.Timings;
import kmlib.starsector.memory.SectorMemoryFlag;

import kmu.settings.KmuMapLayerSettings;

import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * A screen's show-or-hide pick persisted in sector memory under one key, so the pick survives reload,
 * together with the ramp the paint rides between the two states. Each screen holds its own instance under
 * its own key, so the picks stay independent - hiding on one screen writes only its key - while both
 * screens persist the same way, for one consistent behaviour across screens. A save holding no pick yet
 * resolves to shown, which is what a player who has never touched the control is entitled to see.
 *
 * <p>The ramp is derived from how long ago the pick changed rather than advanced by a per-frame tick.
 * Nothing then has to be stepped in the right pass, or at all: two readings taken in different passes of
 * one frame agree because they evaluate the same function of the same clock, a settled pick costs nothing
 * between flips, and a frame the layers do not draw on cannot strand the fade halfway.
 *
 * <p>Real time rather than campaign time, because this is chrome: the map screen runs while the campaign
 * clock does not, so a fade paced off simulation time would freeze on the very screen it plays on.
 *
 * <p>A flip is measured from the fade the previous one had reached rather than from an end of the ramp,
 * so a pick reversed part-way turns around from where the eye currently sees it instead of jumping to the
 * far end and travelling back over ground it has already covered.
 */
public final class PersistedMapLayerVisibility implements MapLayerVisibility {

    // What a save holding no pick yet resolves to. Shown, because the layers are what the feature is for:
    // a player who has never reached the control has not asked for them to be gone.
    private static final boolean LAYERS_SHOWN_BY_DEFAULT = true;

    // The two ends of the ramp: none of the layers on the screen, and all of them.
    private static final float FULLY_HIDDEN = 0f;
    private static final float FULLY_SHOWN = 1f;

    // The stored pick and the default an untouched save resolves to. The key is the save-serialised
    // identity, so it must stay stable once shipped - renaming it returns every existing save to shown.
    private final SectorMemoryFlag areLayersShownFlag;

    // The monotonic clock the ramp is measured against. Only differences are read, so a wall clock
    // stepping backwards cannot send a fade the wrong way.
    private final LongSupplier readElapsedNanos;

    // How long a whole ramp takes, read at each reading rather than captured at the flip, so a player
    // moving the knob mid-fade is answered by it rather than on the next one.
    private final DoubleSupplier readRampSeconds;

    // Where the ramp set off from and when, recorded at each flip. Session state rather than saved: a
    // fade is what the eye is in the middle of, and no reload is in the middle of anything.
    private boolean hasRecordedFlip;
    private float fadeAtLastFlip;
    private long flippedAtNanos;

    /**
     * @param memoryKey the sector-memory key this pick persists under; stable once shipped, since a
     *                  rename returns every existing save to shown
     */
    public PersistedMapLayerVisibility(String memoryKey) {
        this(memoryKey, System::nanoTime, KmuMapLayerSettings::getMapLayerHideFadeSeconds);
    }

    /**
     * @param memoryKey         the sector-memory key this pick persists under
     * @param readElapsedNanos  the monotonic clock the ramp is measured against
     * @param readRampSeconds   how long a whole ramp takes, in seconds; zero or less makes it a cut
     */
    PersistedMapLayerVisibility(
            String memoryKey,
            LongSupplier readElapsedNanos,
            DoubleSupplier readRampSeconds) {

        this.areLayersShownFlag = new SectorMemoryFlag(memoryKey, LAYERS_SHOWN_BY_DEFAULT);
        this.readElapsedNanos = readElapsedNanos;
        this.readRampSeconds = readRampSeconds;
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
        var fadeBeforeFlip = resolveShownFade();

        // A write dropped for want of a sector is not a flip: the pick still reads the old way, so
        // setting off from here would run a fade towards a state nothing stored.
        if (!areLayersShownFlag.set(areLayersShown)) {
            return;
        }
        fadeAtLastFlip = fadeBeforeFlip;
        flippedAtNanos = readElapsedNanos.getAsLong();
        hasRecordedFlip = true;
    }

    @Override
    public float resolveShownFade() {

        var areLayersShown = areLayersShownFlag.isSet();

        // Nothing has flipped this session, so there is no ramp to be part-way through. This is also
        // what a save loaded with the layers hidden reads on its first frame, rather than dissolving
        // away a picture it never drew.
        if (!hasRecordedFlip) {
            return resolveSettledFade(areLayersShown);
        }
        var rampSeconds = readRampSeconds.getAsDouble();

        // A ramp over no time at all is a cut, and is what the knob wound to nothing asks for. Answered
        // ahead of the division rather than by it.
        if (rampSeconds <= 0) {
            return resolveSettledFade(areLayersShown);
        }
        var elapsedNanos = readElapsedNanos.getAsLong() - flippedAtNanos;
        var travelled = (float) (Timings.convertNanosToSeconds(elapsedNanos) / rampSeconds);

        return Ranges.clampToUnit(areLayersShown
            ? fadeAtLastFlip + travelled
            : fadeAtLastFlip - travelled);
    }

    // Where the ramp rests for a pick travelling nowhere: the whole of the layers on the screen, or none
    // of them.
    private static float resolveSettledFade(boolean areLayersShown) {
        return areLayersShown ? FULLY_SHOWN : FULLY_HIDDEN;
    }
}
