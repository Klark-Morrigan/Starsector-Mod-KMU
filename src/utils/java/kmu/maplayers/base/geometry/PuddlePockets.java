package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * Puddles filled the way the settled construction fills captured void: bridges laid across
 * the water, and the water drawn as taken.
 *
 * <p>A puddle is a hole too small to deserve a shoreline, but its water is still water, and
 * leaving it blank draws it as open void - which it is not, being ringed by land the whole
 * way round. The settled map already has the answer for water in that position: the bridges
 * say "this much is held between these cells", and the fill is what they hold. So the same
 * machinery is reused rather than a third reading invented - {@link VoidBridges} finds the
 * spans, and what a puddle adds is only WHICH spans are its.
 *
 * <p><b>A span is a puddle's when both its cells ring the puddle and it crosses the water.</b>
 * Ring membership alone is not enough: two cells that ring a puddle can also pinch some other
 * corridor, and the narrowest clear gap between them - which is what a bridge is - may be that
 * corridor rather than this water. The midpoint settles it, the way a span's own clearance is
 * settled by its middle: the ends sit on the cells' rims, which is the one place "inside the
 * water" has no answer floating point can be trusted to give twice.
 */
public final class PuddlePockets {

    private PuddlePockets() {
    }

    /**
     * Which of the settled bridges are laid across a puddle.
     *
     * <p>Claimed from a search already made rather than searched for here, and that is the
     * whole of this pass. The settled search is sector-wide and its crossing rule holds over
     * everything it keeps, so per-puddle runs would each keep spans a sector-wide pass
     * refuses - and asking for the same search twice is the one answer paid for twice.
     *
     * @param traced  the coasts whose puddles are claiming
     * @param bridges the settled bridges, as the search reports them for the whole sector
     * @return every span whose void is a puddle's water, in the order they were offered
     */
    public static List<CellGap> claimPuddleBridges(
            Coastlines.TracedCoasts traced,
            List<CellGap> bridges) {

        if (traced.puddles().isEmpty()) {
            return List.of();
        }

        var laid = new ArrayList<CellGap>();

        for (var bridge : bridges) {

            if (isAcrossAnyPuddle(bridge, traced.puddles())) {
                laid.add(bridge);
            }
        }
        return List.copyOf(laid);
    }

    private static boolean isAcrossAnyPuddle(
            CellGap bridge,
            List<Coastlines.Puddle> puddles) {

        for (var puddle : puddles) {

            if (puddle.ringCells().contains(bridge.fromSite())
                    && puddle.ringCells().contains(bridge.toSite())
                    && PolygonRegions.isPointInsideRing(
                        puddle.waterEdge(),
                        (bridge.start()[0] + bridge.end()[0]) / 2,
                        (bridge.start()[1] + bridge.end()[1]) / 2)) {

                return true;
            }
        }
        return false;
    }
}
