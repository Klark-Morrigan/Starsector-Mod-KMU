package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * Drives one map layer's staleness poll, so a change the engine fires no event for is still
 * caught a few seconds after it happens rather than only on the next reload.
 *
 * <p>What this owns is the layer's share of the engine's script list: the throttle, the fault
 * guard and the call itself are a {@link StalenessPollLoop}'s. Which changes matter and which
 * refresh each one earns are the layer's answers, so nothing here has to name a layer or a signal.
 *
 * <p>A class of its own rather than a shape the substrate's poll shares, because the engine
 * registers and clears transient scripts by exact class: installed or removed under one class,
 * {@link MapSubstrateSectorWatcher} would be taken down with a layer that was merely switching
 * off.
 *
 * <p>Installed per layer that has a source to poll, and always as a transient script: pure runtime
 * logic, re-added on load, never serialized.
 */
public class MapLayerSectorWatcher implements EveryFrameScript {

    private final StalenessPollLoop pollLoop;

    /**
     * @param stalenessSource the layer whose staleness this poll drives
     */
    public MapLayerSectorWatcher(MapLayerStalenessSource stalenessSource) {
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
