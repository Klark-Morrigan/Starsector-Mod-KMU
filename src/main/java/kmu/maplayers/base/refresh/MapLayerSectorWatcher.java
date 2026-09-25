package kmu.maplayers.base.refresh;

/**
 * Drives one map layer's staleness poll, so a change the engine fires no event for is still
 * caught a few seconds after it happens rather than only on the next reload.
 *
 * <p>What this owns is the layer's share of the engine's script list, and nothing else: the
 * throttle, the fault guard and the call itself are {@link BaseStalenessSectorWatcher}'s. Which
 * changes matter and which refresh each one earns are the layer's answers, so nothing here has to
 * name a layer or a signal.
 *
 * <p>Abstract, and subclassed once per layer, because the engine registers and clears transient
 * scripts by exact class. Two layers installing one class would each clear the other's poll on
 * install, and a layer switching off would take every other layer's poll down with its own - the
 * same reason {@link MapSubstrateSectorWatcher} is a class apart. A layer's own subclass is its
 * poll's identity, which is all it has to add.
 *
 * <p>Installed per layer that has a source to poll, and always as a transient script: pure runtime
 * logic, re-added on load, never serialized.
 */
public abstract class MapLayerSectorWatcher extends BaseStalenessSectorWatcher {

    /**
     * @param stalenessSource the layer whose staleness this poll drives
     */
    protected MapLayerSectorWatcher(MapLayerStalenessSource stalenessSource) {
        super(stalenessSource);
    }
}
