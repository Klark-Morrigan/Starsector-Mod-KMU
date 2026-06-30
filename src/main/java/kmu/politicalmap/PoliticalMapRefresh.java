package kmu.politicalmap;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two revision counters the political-map overlay watches to know what part of
 * its cache has gone stale, so it rebuilds only what actually changed.
 *
 * <p>The cell geometry (the Voronoi partition) is expensive but changes rarely -
 * only when the set of reachable systems does (a gate activating, a jump point
 * established). The drawables (fills and outline colors) are cheap and change
 * often - on ownership shifts, a discovered market, or the uninhabited setting -
 * but always over the same fixed cells. Splitting the two means a routine
 * restyle never triggers a Voronoi rebuild.
 *
 * <p>Counters rather than direct calls because the producers (a listener, a
 * watcher) and the consumer (the engine-instantiated terrain plugin) are created
 * independently, with no shared owner to wire together.
 */
public final class PoliticalMapRefresh {
    private static final Logger LOG = Global.getLogger(PoliticalMapRefresh.class);

    private static final AtomicInteger geometryRevision = new AtomicInteger();
    private static final AtomicInteger contentRevision = new AtomicInteger();

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
     * @return a counter that advances when the overlay's colors/fills change
     *         over unchanged geometry (ownership, a discovered market)
     */
    public static int getContentRevision() {
        return contentRevision.get();
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
     * Marks the overlay's drawables stale: ownership or a discovered market
     * changed, but the geometry did not.
     */
    public static void requestContentRefresh() {
        // Every drawables-only restyle (discovery, ownership) funnels through
        // here. Logged with the resulting counter so a colour that did or did
        // not update can be traced to the request.
        var revision = contentRevision.incrementAndGet();
        LOG.debug("Political map content refresh requested; contentRevision=" + revision);
    }
}
