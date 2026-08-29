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
     * The bridges laid across every puddle of a trace.
     *
     * <p>Found by the settled search over the whole sector and claimed by the puddles, rather
     * than searched per puddle: the search's crossing rule holds over everything it keeps,
     * and per-puddle runs would each keep spans a sector-wide pass refuses.
     *
     * @param traced        the coasts whose puddles want bridging
     * @param parameters    the knobs the cells are built under
     * @param reachMultiple how far apart two cells may sit and still be bridged, in cell
     *                      radii - the settled bridges' own knob, because these are the
     *                      settled bridges asked about smaller water
     * @return every span whose void is a puddle's water, narrowest first
     */
    public static List<CellGap> findPuddleBridges(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            double reachMultiple) {

        if (traced.puddles().isEmpty()) {
            return List.of();
        }

        var laid = new ArrayList<CellGap>();

        for (var bridge : VoidBridges.findVoidBridges(
                traced.union().sites(),
                parameters.cellRadius(),
                parameters.cellRadius() * reachMultiple)) {

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
