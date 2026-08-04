package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The refresh signals a map layer's overlay watches to know what part of its
 * cache has gone stale, so it rebuilds only what actually changed.
 *
 * <p>A coarse change is one counter, held under the {@link MapLayerRefreshSignal} its producer and its
 * consumer both name: the producer raises the signal, and the consumer folds the current count
 * into the token it compares each frame, so the per-frame path stays an int compare. Keyed on the
 * open signal type rather than on a fixed set of accessors, so a layer watching something only it
 * can raise declares that signal beside itself and still reaches this one board for it - which is
 * what keeps a second layer's arrival from splitting the mechanism in two. The counters
 * materialise on first use, since a board that cannot enumerate the layers cannot pre-seed their
 * signals; an unraised signal reads zero, so a consumer folding one no producer ever bumps folds
 * a constant. A counter is never cleared, so composing several cannot clear one out from under a
 * second reader.
 *
 * <p>A change to what a system groups by - the opaque key {@code CellGrouping} resolves a
 * cell's cluster from, whose meaning belongs to the layer and not to this board - is finer than
 * any counter can express: rather than rescanning every system, the producers name exactly which
 * ones went stale, so the plugin re-derives and re-shapes only those and their neighbours. A set
 * because several systems can go stale in one tick, and identity is all that matters (a system is
 * stale or not, once per refresh). Several producers can feed the same set - a per-event signal
 * and a periodic diff, say - so a system one already marked and another re-discovers collapses to
 * a single reshape. The whole-map restyle a settings change needs is a separate signal the plugin
 * reads straight from {@code KmuLunaSettings.getSettingsRevision}, not this class.
 *
 * <p>Counters and a set rather than direct calls because the producers (a
 * listener, a watcher) and the consumer (the engine-instantiated terrain plugin)
 * are created independently, with no shared owner to wire together. Both are
 * concurrent because a producer's event fires on the campaign thread while the
 * plugin reads or drains on the render thread.
 */
public final class MapLayerRefresh {
    private static final Logger LOG = Global.getLogger(MapLayerRefresh.class);

    private static final Map<MapLayerRefreshSignal, AtomicInteger> revisionsBySignal =
            new ConcurrentHashMap<>();
    private static final Set<String> groupingStaleSystemIds = ConcurrentHashMap.newKeySet();

    private MapLayerRefresh() {
    }

    /**
     * @param signal the coarse change to read
     * @return how many times {@code signal} has been raised, which advances only when it is
     *         raised and never when another signal is
     */
    public static int getRevision(MapLayerRefreshSignal signal) {
        return resolveCounter(signal).get();
    }

    /**
     * Raises {@code signal}: whatever it names went stale, so every consumer folding it in
     * rebuilds. A consumer that does not fold this signal in is left alone, which is what lets a
     * layer ignore a change it does not render.
     *
     * @param signal the coarse change that occurred
     */
    public static void requestRefresh(MapLayerRefreshSignal signal) {
        // The one seam every coarse change funnels through. Logged with the signal and the
        // resulting counter so an overlay that failed to repaint - a stale cluster, a toggle that
        // did nothing - can be traced to whether the request was even issued.
        var revision = resolveCounter(signal).incrementAndGet();
        LOG.debug("Map layer refresh requested; signal="
            + signal.getId()
            + " revision=" + revision);
    }

    /**
     * Marks one system's owner stale so the overlay re-derives just it (and
     * its neighbours) rather than rescanning every system - used when a producer can
     * name the one system whose key may have moved.
     *
     * @param systemId the system whose owner may have changed; null is
     *                 ignored
     */
    public static void markSystemGroupingStale(String systemId) {
        if (systemId == null) {
            return;
        }
        groupingStaleSystemIds.add(systemId);
        LOG.debug("Map layer system grouping marked stale; systemId="
            + systemId);
    }

    /**
     * Removes and returns the systems marked grouping-stale since the last drain,
     * so the plugin processes each staleness once. A full rebuild (a settings
     * change or a geometry change) drains and discards them, since it already
     * re-derives every system.
     *
     * @return the drained stale system ids; empty when none are pending
     */
    public static Set<String> drainStaleGroupingSystemIds() {
        if (groupingStaleSystemIds.isEmpty()) {
            return Set.of();
        }
        // Snapshot then remove exactly what was snapshotted, so an id added by the
        // campaign thread between the copy and the removal survives to the next
        // drain rather than being silently dropped.
        var drained = new LinkedHashSet<>(groupingStaleSystemIds);
        groupingStaleSystemIds.removeAll(drained);
        return drained;
    }

    // The single point a signal's counter is reached through, so a read of a never-raised signal
    // answers zero by the same path a raise starts from rather than by a null check of its own.
    private static AtomicInteger resolveCounter(MapLayerRefreshSignal signal) {
        return revisionsBySignal.computeIfAbsent(signal, key -> new AtomicInteger());
    }
}
