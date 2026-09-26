package kmu.maplayers.base.render;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileOrigin;
import kmlib.profiling.ProfileScope;
import kmlib.profiling.ProfileSection;

/**
 * One layer's frame, opened for measurement: a beat, and that layer's own row inside it.
 *
 * <p>The two are opened separately rather than together because the stand-down reads sit between
 * them - a beat that found nothing to draw has a cost worth reporting and no layer to report one
 * for. What this holds is the pair of answers both opens need, which do not change while a sector
 * is loaded: which game the rows are grouped under, and which row this layer's work sits on.
 *
 * <p>Held by {@link SequencedMapLayerRenderer}, one composed from each layer's own ID, so the
 * sequence names a beat and nothing else.
 *
 * <p>The profiler is resolved per open rather than held, since the binding can change mid-session -
 * a readout switched on, or the level knob rebinding - and a held one would go on recording into a
 * capture nothing reads.
 */
public final class MapFrameBeats {

    private final ProfileOrigin origin;
    private final ProfileSection layerSection;

    /**
     * @param origin       the game these rows are grouped under, so a capture taken across two says
     *                     which one each row was measured in
     * @param layerSection the row this layer's work sits on inside every beat
     */
    public MapFrameBeats(ProfileOrigin origin, ProfileSection layerSection) {
        this.origin = origin;
        this.layerSection = layerSection;
    }

    /**
     * Opens {@code beat} as a root of this sector, for the caller to close when the beat ends.
     *
     * @param beat which beat of the frame is running, from {@link MapFrameSections}
     * @return the open scope, closed to record what the beat cost
     */
    public ProfileScope openBeat(ProfileSection beat) {
        return ActiveProfiler.resolveProfiler().openRoot(origin, beat);
    }

    /**
     * Opens this layer's row inside the beat already open, for the caller to close when the layer's
     * work ends.
     *
     * @return the open scope, closed to record what the layer cost in that beat
     */
    public ProfileScope openLayerRow() {
        return openStep(layerSection);
    }

    /**
     * Opens {@code step} inside whatever is already open, for a part of a beat worth a row of its
     * own - the cache refresh inside the preparation being the one such part today.
     *
     * @param step which part of the beat is running, from {@link MapFrameSections}
     * @return the open scope, closed to record what the step cost
     */
    public ProfileScope openStep(ProfileSection step) {
        return ActiveProfiler.resolveProfiler().open(step);
    }
}
