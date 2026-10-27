package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

/**
 * The production draw lists a cache stands on, for the suites whose subject is what a build or a
 * batch drew there.
 */
public final class OwnerMapCacheReads {

    // Fixtures only; never instantiated.
    private OwnerMapCacheReads() {
    }

    /**
     * The production draw lists the cache's built view holds, failing the case where it holds
     * another view: every suite asking has driven a production build first.
     *
     * @param cache the cache to read
     * @return the production draw lists
     */
    public static OwnerMapClusters readProductionClustersOf(OwnerMapCache cache) {

        return cache
            .getBuiltView()
            .selectByCase(
                clusters -> clusters,
                borderStageOverlay -> {

                    throw new AssertionError("The cache stands on the debug overlay, not the production view");
                },
                () -> {

                    throw new AssertionError("The cache stands on the placeholder, not the production view");
                });
    }
}
