package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;

import kmlib.starsector.systems.SystemKey;

import org.apache.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One sector's refresh signals: what a map layer's overlay watches to know which part of its cache
 * has gone stale, so it rebuilds only what actually changed.
 *
 * <p>One board per sector, however many layers raise signals on it. Every signal here is a fact
 * about one sector - a counter says that sector's alliances moved, and the stale set names that
 * sector's systems by {@link SystemKey} - so two sectors sharing a board would have one sector's
 * colony change mark the other's system stale, under a key whose engine-minted arms each sector
 * mints without regard to the other. A layer still declares its own signals and raises them on
 * whichever sector's board it was handed, so a second layer's arrival does not split the mechanism
 * in two.
 *
 * <p>A coarse change is one counter, held under the {@link MapLayerRefreshSignal} its producer and
 * its consumer both name: the producer raises the signal, and the consumer folds the current count
 * into the token it compares each frame, so the per-frame path stays an int compare. Keyed on the
 * open signal type rather than on a fixed set of accessors, so a layer watching something only it
 * can raise declares that signal beside itself and still reaches this one board for it. The counters
 * materialise on first use, since a board that cannot enumerate the layers cannot pre-seed their
 * signals; an unraised signal reads zero, so a consumer folding one no producer ever bumps folds a
 * constant. A counter is never cleared, so composing several cannot clear one out from under a
 * second reader.
 *
 * <p>A change to what a system groups by - the opaque key {@code CellGrouping} resolves a cell's
 * cluster from, whose meaning belongs to the layer and not to this board - is finer than any counter
 * can express: rather than rescanning every system, the producers name exactly which ones went
 * stale, so the plugin re-derives and re-shapes only those and their neighbours. A set because
 * several systems can go stale in one tick, and identity is all that matters (a system is stale or
 * not, once per refresh). Several producers can feed the same set - a per-event signal and a
 * periodic diff, say - so a system one already marked and another re-discovers collapses to a single
 * reshape. Named by key rather than by the vanilla ID because every producer holds the system when
 * it marks it, and an ID names every system sharing it: a mark by ID would either fan out to systems
 * nothing happened in or resolve to whichever of them a lookup answers first. The whole-map restyle
 * a settings change needs is a separate signal the plugin reads straight from
 * {@code KmuLunaSettings.getSettingsRevision}, not this class.
 *
 * <p>Counters and a set rather than direct calls because the producers (a listener, a watcher) and
 * the consumer (the engine-instantiated terrain plugin) never hold one another: each is built by a
 * different seam, and the consumer is reconstructed from the save with nothing to wire it to. Both
 * are concurrent because a producer's event fires on the campaign thread while the plugin reads or
 * drains on the render thread.
 */
public final class MapLayerRefreshBoard {

    private static final Logger LOG = Global.getLogger(MapLayerRefreshBoard.class);

    private final Set<SystemKey> groupingStaleSystemKeys = ConcurrentHashMap.newKeySet();
    private final Map<MapLayerRefreshSignal, AtomicInteger> revisionsBySignal =
        new ConcurrentHashMap<>();

    /**
     * @param signal the coarse change to read
     * @return how many times {@code signal} has been raised on this board, which advances only when
     *         it is raised and never when another signal is
     */
    public int getRevision(MapLayerRefreshSignal signal) {

        return resolveCounter(signal).get();
    }

    /**
     * Raises {@code signal}: whatever it names went stale, so every consumer folding it in
     * rebuilds. A consumer that does not fold this signal in is left alone, which is what lets a
     * layer ignore a change it does not render.
     *
     * @param signal the coarse change that occurred
     */
    public void requestRefresh(MapLayerRefreshSignal signal) {
        // The one seam every coarse change funnels through. Logged with the signal and the
        // resulting counter so an overlay that failed to repaint - a stale cluster, a toggle that
        // did nothing - can be traced to whether the request was even issued.
        var revision = resolveCounter(signal).incrementAndGet();

        LOG.debug("Map layer refresh requested; signal="
            + signal.getId()
            + " revision=" + revision);
    }

    /**
     * Marks one system's owner stale so the overlay re-derives just it (and its neighbours) rather
     * than rescanning every system - used when a producer can name the one system whose key may
     * have moved.
     *
     * @param systemKey the system whose owner may have changed; null is ignored
     */
    public void markSystemGroupingStale(SystemKey systemKey) {

        if (systemKey == null) {
            return;
        }
        groupingStaleSystemKeys.add(systemKey);

        // Named by the vanilla ID, which is what a reader of the log calls the system.
        LOG.debug("Map layer system grouping marked stale; systemId="
            + systemKey.systemId());
    }

    /**
     * Removes and returns the systems marked grouping-stale since the last drain, so the plugin
     * processes each staleness once. A full rebuild (a settings change or a geometry change) drains
     * and discards them, since it already re-derives every system.
     *
     * @return the drained stale system keys; empty when none are pending
     */
    public Set<SystemKey> drainStaleGroupingSystemKeys() {

        if (groupingStaleSystemKeys.isEmpty()) {
            return Set.of();
        }
        // Snapshot then remove exactly what was snapshotted, so a key added by the
        // campaign thread between the copy and the removal survives to the next
        // drain rather than being silently dropped.
        var drained = new LinkedHashSet<>(groupingStaleSystemKeys);

        groupingStaleSystemKeys.removeAll(drained);

        return drained;
    }

    // The single point a signal's counter is reached through, so a read of a never-raised signal
    // answers zero by the same path a raise starts from rather than by a null check of its own.
    private AtomicInteger resolveCounter(MapLayerRefreshSignal signal) {

        return revisionsBySignal.computeIfAbsent(signal, key -> new AtomicInteger());
    }
}
