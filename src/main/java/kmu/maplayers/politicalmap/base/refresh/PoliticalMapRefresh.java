package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The refresh signals the political-map overlay watches to know what part of its
 * cache has gone stale, so it rebuilds only what actually changed.
 *
 * <p>The cell geometry (the Voronoi partition) is expensive but changes rarely -
 * only when the set of reachable systems does (a gate activating, a jump point
 * established). A single {@code geometryRevision} counter flags that: a bump tells
 * the plugin to reconcile the geometry cache.
 *
 * <p>Ownership changes are finer-grained: rather than a whole-economy rescan, the
 * producers name exactly which systems went stale (a colony resized past its
 * neighbour, a market discovered, a colony a silent AI change handed to another
 * faction), so the plugin re-derives and re-shapes only those and their
 * neighbours. A set because several colonies can resize in one economy tick, and
 * identity is all that matters (a system is stale or not, once per refresh). Both
 * the event listeners and the sector watcher feed this same set, so a change a
 * listener already marked and one the watcher's owner diff re-discovers collapse
 * to a single reshape. The whole-map restyle a settings change needs is a separate
 * signal the plugin reads straight from {@code KmuLunaSettings}, not this class.
 *
 * <p>Alliance membership is a third coarse signal, tracked like the geometry with a
 * single {@code allianceRevision} counter. Which factions are allied changes only the
 * alliances view (it fuses allied factions into one bloc), so a bump here is folded into
 * the drawables' content token by that view alone; the faction view ignores it. The
 * sector watcher fingerprints the live alliance set each poll and bumps this counter when
 * that fingerprint moves, so the per-frame path stays an int compare.
 *
 * <p>A counter and a set rather than direct calls because the producers (a
 * listener, a watcher) and the consumer (the engine-instantiated terrain plugin)
 * are created independently, with no shared owner to wire together. The set is
 * concurrent because a colony-size or discovery event fires on the campaign thread
 * while the plugin drains it on the render thread.
 */
public final class PoliticalMapRefresh {
    private static final Logger LOG = Global.getLogger(PoliticalMapRefresh.class);

    private static final AtomicInteger geometryRevision = new AtomicInteger();
    private static final AtomicInteger allianceRevision = new AtomicInteger();
    private static final Set<String> politicsStaleSystemIds = ConcurrentHashMap.newKeySet();

    private PoliticalMapRefresh() {
    }

    /**
     * @return a counter that advances when the reachable-system set changes, so
     *         the cell geometry must be rebuilt
     */
    public static int getGeometryRevision() {
        return geometryRevision.get();
    }

    /**
     * Marks the cell geometry stale: the set of reachable systems changed.
     */
    public static void requestGeometryRefresh() {
        // The central seam every reachable-set change funnels through. Logged
        // with the resulting counter so a stale or missing province can be
        // traced to whether the request was even issued.
        var revision = geometryRevision.incrementAndGet();
        LOG.debug("Political map geometry refresh requested; geometryRevision=" + revision);
    }

    /**
     * @return a counter that advances when the live alliance set changes, so the
     *         alliances view rebuilds its drawables; the faction view does not read it
     */
    public static int getAllianceRevision() {
        return allianceRevision.get();
    }

    /**
     * Marks the alliance grouping stale: an alliance formed, dissolved, or gained or
     * lost a member. Only the alliances view folds this into its content token, so the
     * faction view is never rebuilt for a change it does not render.
     */
    public static void requestAllianceRefresh() {
        // The seam the sector watcher's alliance-fingerprint diff funnels through, mirroring
        // requestGeometryRefresh. Logged with the resulting counter so a stale or missing
        // alliance region can be traced to whether the fingerprint diff even fired.
        var revision = allianceRevision.incrementAndGet();
        LOG.debug("Political map alliance refresh requested; allianceRevision=" + revision);
    }

    /**
     * Marks one system's ownership stale so the overlay re-derives just it (and
     * its neighbours) rather than rescanning the whole economy - used when a single
     * colony resized or a market was discovered.
     *
     * @param systemId the system whose dominant owner may have changed; null is
     *                 ignored
     */
    public static void markSystemPoliticsStale(String systemId) {
        if (systemId == null) {
            return;
        }
        politicsStaleSystemIds.add(systemId);
        LOG.debug("Political map system politics marked stale; systemId=" + systemId);
    }

    /**
     * Removes and returns the systems marked politics-stale since the last drain,
     * so the plugin processes each staleness once. A full rebuild (a settings
     * change or a geometry change) drains and discards them, since it already
     * re-derives every system.
     *
     * @return the drained stale system ids; empty when none are pending
     */
    public static Set<String> drainStalePoliticsSystemIds() {
        if (politicsStaleSystemIds.isEmpty()) {
            return Set.of();
        }
        // Snapshot then remove exactly what was snapshotted, so an id added by the
        // campaign thread between the copy and the removal survives to the next
        // drain rather than being silently dropped.
        var drained = new LinkedHashSet<>(politicsStaleSystemIds);
        politicsStaleSystemIds.removeAll(drained);
        return drained;
    }
}
