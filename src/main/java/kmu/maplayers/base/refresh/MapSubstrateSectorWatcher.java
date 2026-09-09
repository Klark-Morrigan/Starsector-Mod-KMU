package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * Drives the poll the substrate owns - the one that runs whatever layers are standing, so what it
 * keeps up to date is never gapped by a preference about which map a player is looking at.
 *
 * <p>The same loop the layers' watcher runs, on the same cadence, and a class of its own for the
 * one reason that matters here: the engine registers and clears transient scripts by exact class,
 * so a poll installed and taken back on its own timetable needs an identity of its own.
 * {@link MapLayerSectorWatcher} states the other half of that.
 *
 * <p>Installed once per sector the map layers are stood up on, and as a transient script: pure
 * runtime logic, re-added on load, never serialized.
 */
public class MapSubstrateSectorWatcher implements EveryFrameScript {

    private final StalenessPollLoop pollLoop;

    /**
     * @param stalenessSource the substrate reading this poll drives
     */
    public MapSubstrateSectorWatcher(MapLayerStalenessSource stalenessSource) {
        this.pollLoop = new StalenessPollLoop(stalenessSource);
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        pollLoop.advancePoll(amount);
    }
}
