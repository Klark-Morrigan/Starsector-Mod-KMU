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
 * <p>How a view recedes its background ground - the shared per-save Mute/Desaturate toggles -
 * is a fourth coarse signal, tracked with a single {@code recedeStyleRevision} counter. Those
 * toggles are sidebar-only sector-memory state, not LunaLib fields, so a flip does not bump
 * {@code settingsRevision}; instead the toggle's setter bumps this counter and every view that
 * recedes ground folds it into its content token, so a flip repaints the overlay live. A view
 * that draws no receded ground ignores it, exactly as it ignores a change it does not render.
 *
 * <p>Which bloc the filter spotlights is a fifth coarse signal, tracked with a single
 * {@code filterRevision} counter. The selected bloc is sidebar-only sector-memory state like the
 * recede toggles, so picking or clearing it never bumps {@code settingsRevision}; instead the
 * selection's setter bumps this counter. Unlike the alliance and recede signals this one is folded
 * into the content token at the pipeline level rather than by any single view, since the filter is a
 * mode orthogonal to the active view (either view can be filtered), so a bump repaints the overlay
 * under whichever view is up without each view naming the filter.
 *
 * <p>How the map draws the sidebar's two shared appearance toggles - whether uninhabited
 * systems draw their outline, and whether a cluster label spells its owner's full or short
 * name - is a sixth coarse signal, tracked with a single {@code mapStyleRevision} counter.
 * Both toggles are sidebar-only sector-memory state rather than LunaLib fields, so flipping
 * one never bumps {@code settingsRevision}; each setter bumps this counter instead. Folded in
 * at the pipeline level like the filter revision, since both toggles restyle the whole map
 * under whichever view is up rather than belonging to any single view.
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
    private static final AtomicInteger recedeStyleRevision = new AtomicInteger();
    private static final AtomicInteger filterRevision = new AtomicInteger();
    private static final AtomicInteger mapStyleRevision = new AtomicInteger();
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
     * @return a counter that advances when a recede toggle (Mute or Desaturate) flips, so every
     *         view that recedes ground rebuilds its drawables; a view drawing no receded ground
     *         does not read it
     */
    public static int getRecedeStyleRevision() {
        return recedeStyleRevision.get();
    }

    /**
     * Marks the recede styling stale: the player flipped a view's Mute or Desaturate toggle. Only a
     * view that recedes ground folds this into its content token, so a view drawing no receded
     * ground is never rebuilt for a toggle it does not honour. This is the live-invalidation seam
     * the toggles use in place of {@code settingsRevision}, since they are sidebar-only
     * sector-memory state rather than LunaLib fields.
     */
    public static void requestRecedeStyleRefresh() {
        // The seam the Mute/Desaturate setters funnel through, mirroring requestAllianceRefresh.
        // Logged with the resulting counter so a toggle that failed to repaint can be traced to
        // whether the request was even issued.
        var revision = recedeStyleRevision.incrementAndGet();
        LOG.debug("Political map recede style refresh requested; recedeStyleRevision="
                + revision);
    }

    /**
     * @return a counter that advances when the filter's selected bloc changes or is cleared, so the
     *         overlay rebuilds its drawables; folded into the content token at the pipeline level,
     *         since the filter is a mode either view can be under rather than a single view's concern
     */
    public static int getFilterRevision() {
        return filterRevision.get();
    }

    /**
     * Marks the filter selection stale: the player picked a different bloc to spotlight or cleared
     * the filter. Folded into the content token at the pipeline level - the filter is orthogonal to
     * the active view, so the bump repaints under whichever view is up without any view naming it.
     * This is the live-invalidation seam the selection uses in place of {@code settingsRevision},
     * since the selected bloc is sidebar-only sector-memory state rather than a LunaLib field.
     */
    public static void requestFilterRefresh() {
        // The seam the filter selection's setter funnels through, mirroring requestRecedeStyleRefresh.
        // Logged with the resulting counter so a selection that failed to repaint can be traced to
        // whether the request was even issued.
        var revision = filterRevision.incrementAndGet();
        LOG.debug("Political map filter refresh requested; filterRevision=" + revision);
    }

    /**
     * @return a counter that advances when a shared appearance toggle (the uninhabited-systems
     *         outline, the full/short name format) flips, so the overlay restyles; folded into the
     *         content token at the pipeline level, since either toggle changes how the map draws
     *         under whichever view is up
     */
    public static int getMapStyleRevision() {
        return mapStyleRevision.get();
    }

    /**
     * Marks the map's shared styling stale: the player flipped the uninhabited-systems outline or the
     * full/short name format on the sidebar. This is the live-invalidation seam both toggles use in
     * place of {@code settingsRevision}, since each is sidebar-only sector-memory state rather than a
     * LunaLib field.
     */
    public static void requestMapStyleRefresh() {
        // The seam the two shared appearance toggles' setters funnel through, mirroring
        // requestRecedeStyleRefresh. Logged with the resulting counter so a toggle that failed to
        // repaint can be traced to whether the request was even issued.
        var revision = mapStyleRevision.incrementAndGet();
        LOG.debug("Political map style refresh requested; mapStyleRevision=" + revision);
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
