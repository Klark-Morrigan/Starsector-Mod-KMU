package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * The settled bridge search, found once and handed to everything that wants it.
 *
 * <p>Two constructions ask the same question of one sector: the settled map draws the void its
 * bridges hold, and the continent map fills its puddles by bridging them. Both want
 * {@link VoidBridges#findVoidBridges} over every site at the same reach, so a second search is
 * the same answer paid for twice - tens of milliseconds on a sector, on every rebuild.
 *
 * <p>Kept rather than recomputed only while the question is unchanged. The arguments ARE the
 * key: a different site list or a moved reach is a different search, and a cache that answered
 * from a stale one would hand a construction bridges laid across a map it is not drawing. The
 * sites are compared by identity because a fixture hands back the one list it holds - two
 * lists of equal contents would be two sectors, and comparing them element-wise every call
 * would cost more than the search saved.
 *
 * <p>Held by whatever owns both constructions rather than made static. A static cache would be
 * one answer shared by every sector a process ever opens, which is exactly wrong for a window
 * that switches fixtures, and would make a headless dump's answer depend on what ran before it.
 */
public final class VoidBridgeCache {

    private List<double[]> forSites;
    private double forCellRadius;
    private double forMaxSeparation;
    private List<CellGap> bridges = List.of();

    /**
     * The bridges, searched for only where this is not the search just made.
     *
     * @param sites         the sites
     * @param cellRadius    how far a cell reaches from its site
     * @param maxSeparation how far apart two sites may be and still hold the void between
     *                      them, centre to centre
     * @return the bridges, narrowest first, exactly as the search itself reports them
     */
    public List<CellGap> findVoidBridges(
            List<double[]> sites,
            double cellRadius,
            double maxSeparation) {

        if (sites != forSites
                || cellRadius != forCellRadius
                || maxSeparation != forMaxSeparation) {

            bridges = VoidBridges.findVoidBridges(sites, cellRadius, maxSeparation);
            forSites = sites;
            forCellRadius = cellRadius;
            forMaxSeparation = maxSeparation;
        }
        return bridges;
    }
}
