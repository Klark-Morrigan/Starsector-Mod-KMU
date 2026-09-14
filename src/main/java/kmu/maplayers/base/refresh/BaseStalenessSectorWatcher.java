package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * What every staleness watcher is apart from its own identity: a {@link StalenessPollLoop} and the
 * engine's per-frame contract handed to it.
 *
 * <p>Abstract because the identity is the whole of what a watcher adds, and it has to be a class
 * rather than a flag. The engine registers and clears transient scripts by exact class -
 * {@code getClass() != clazz}, no subtype - and KMLib's {@code SectorScripts.installTransientScript}
 * clears by the built script's own class, which for a subclass is the subclass. So two subclasses
 * here still install and clear on their own timetables, exactly as two unrelated classes did, and
 * what they stop sharing is only the three lines each was spelling for itself.
 *
 * <p>Never installed itself. A watcher is always one of the concrete subclasses, since registering
 * this one would be registering a lifetime nothing owns.
 */
abstract class BaseStalenessSectorWatcher implements EveryFrameScript {

    private final StalenessPollLoop pollLoop;

    /**
     * @param stalenessSource the source whose staleness this watcher's poll drives
     */
    protected BaseStalenessSectorWatcher(MapLayerStalenessSource stalenessSource) {
        this.pollLoop = new StalenessPollLoop(stalenessSource);
    }

    @Override
    public final void advance(float amount) {
        pollLoop.advancePoll(amount);
    }

    /**
     * @return false always - a staleness poll runs for as long as the sector does, so there is no
     *         state in which it has finished
     */
    @Override
    public final boolean isDone() {
        return false;
    }

    /**
     * @return false always - what a poll catches is campaign change, and a paused campaign produces
     *         none, so polling through a pause would be a sector read per few seconds that can only
     *         find what the last one did
     */
    @Override
    public final boolean runWhilePaused() {
        return false;
    }
}
