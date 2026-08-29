package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared-anchor span formations, thinned: no anchor point hosts more than one span where the
 * map can afford it.
 *
 * <p>A cell whose frontage collapses to a single point gives every span leaving it the same
 * anchor, and a run of such cells along a corridor chains the spans into zigzags - walls
 * subdividing the corridor into slivers a reader gains nothing from. The formations wanted
 * gone are exactly the ones where dropping a span merges two pieces of trapped void; the ones
 * that must stay are the ones holding void in.
 *
 * <p><b>Which is which is asked of the water beside the span.</b> A step off each side of a
 * span's middle lands either in void the walls hold in or in the open sea; held on both
 * sides, dropping the span merges trapped water with trapped water and what it held stays
 * held, so it may go. Open on either side, it is the only thing between that water and the
 * sea, and it stays.
 *
 * <p><b>Asked of a flood rather than of the boundary walk</b>, which cannot answer it here.
 * The walk lays each wall as a chord and gives every mouth on a cell to one chord only, so
 * where two walls meet at a point one of them is refused - and two spans meeting at a point
 * is precisely what a crowded anchor is. At every anchor this pass exists to thin, one span
 * goes unlaid, the region it would have closed never appears, and the span beside it reads
 * as though it faced the open sea. No ordering mends that: whichever wall is offered first,
 * something at that anchor still loses.
 *
 * <p>A flood has no mouths to compete for. It is stopped by any wall it cannot step over,
 * laid or not, so every span and every stretch of coast counts for exactly what it draws.
 * See {@link VoidEnclosure}, whose stride is the one claim the answer rests on.
 *
 * <p><b>The spans are walled alone, without the coast's own reaches.</b> What a span encloses
 * is a fact about the spans and the cells: a coastline is a line the smoothing DREW, not a
 * boundary void lies behind. Laid together, the two compete for the same mouths - a span
 * anchors on a coast vertex, which is exactly where a reach has its mouth - and the reach
 * wins, so most spans are refused and the pocket map goes blind to them. Blind, the
 * classification reads "refused" where it means "internal", which is how a span holding back
 * open void comes to look droppable.
 *
 * <p><b>One drop at a time, always.</b> Dropping a span FREES its neighbours: the anchors at
 * its two ends each lose a tenant, and a neighbour that was hemmed in between two crowded
 * anchors becomes a span with room. That is what makes a chain thin to every second span
 * rather than to its two ends - drop the middle of a Z and both arms are already resolved,
 * and a longer chain resolves the same way down its length. Dropped as a batch instead,
 * every interior of a chain qualifies against the same round and the whole middle goes at
 * once, leaving a corridor walled only at its mouths.
 *
 * <p><b>What cannot go is settled first.</b> A span holding water back is staying whatever
 * else happens, so it is locked at once and whatever crowds it dropped. That is what a chain
 * with a load-bearing span in the middle needs: the middle stays, and the chain falls into
 * two pairs either side of it, each already carrying the span that had to stay, so the other
 * of each pair goes. Settled by the ends rule instead, the spans EITHER SIDE of the middle
 * would be kept, and the middle - droppable by nobody - would leave both its anchors as
 * crowded as they began.
 *
 * <p><b>Then worked from the ends of a chain inward, never from its middle.</b> A span with
 * an anchor all to itself is the end of a chain: it is kept, and whatever crowds its other
 * anchor is dropped, which makes the next span along an end in its turn. Five droppable spans
 * resolve to the first, third and fifth that way, every second one going.
 *
 * <p>Dropping the middle instead is what a rule reaching for the most crowded span does, and
 * it is worse than useless: take the third span out of five and the two either side of the
 * gap are still crowded against their own neighbours, so one of each pair has to go as well
 * and three drops leave two spans standing where two drops would have left three. The middle
 * of a chain is the one span whose removal buys nothing.
 *
 * <p><b>Which end to keep, decided by the water around it.</b> Ends are taken in order of the
 * water they hold back, the roomiest first, so a fan - where every arm is an end - keeps the
 * arm between the largest pockets and shrinks to one span, and a chain resolves the same way
 * from either end. Where no span has an anchor to itself the formation closes on itself, and
 * the round falls back to dropping whichever span has the smallest flanking pockets: the
 * smallest pocket at a crowded anchor merging with its own smallest neighbour, until the ring
 * is broken and ends appear. A fan of sealing spans stays a fan: every line of it is
 * load-bearing, and trapped void outranks tidiness.
 *
 * <p>Dropping never re-traces. A drop only ever merges two pockets, so the pocket graph is
 * kept as a union of the traced holes with summed areas, and the one trace this pass makes is
 * the whole of what it costs.
 */
public final class SpanFormations {

    // No pocket on that side of the span, and no span to return: the two share a spelling
    // because both mean "nothing here", and only ever one of them is being asked for.
    private static final int NO_POCKET = -1;

    // What a look along one side of a span found. Water it holds settles the side outright;
    // the open sea settles it only where no station found water; and a look that never left
    // a cell or a wall settles nothing at all.
    private static final int HOLDS_WATER = 1;
    private static final int OPEN_SEA = -1;
    private static final int UNREADABLE = -2;

    private SpanFormations() {
    }

    /**
     * Thins the spans until no anchor hosts more than one, wherever void allows it.
     *
     * @param spans      the spans as laid, in the order they were kept
     * @param traced     the coasts they were anchored on
     * @param parameters the knobs the cells are built under
     * @return the surviving spans, in their original order
     */
    public static List<CellGap> resolveSharedAnchors(
            List<CellGap> spans,
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters) {

        var anchors = new SpanAnchors(spans);

        // Settled before anything is traced: most refreshes lay no two spans on one point,
        // and the trace below is the whole cost of this pass.
        if (!anchors.isAnyAnchorShared()) {
            return spans;
        }

        var pockets = TracedPocketGraph.tracePocketGraph(spans, traced, parameters);
        var dropped = new boolean[spans.size()];

        dropUntilAnchorsAreFree(anchors, pockets, dropped);

        var kept = new ArrayList<CellGap>(spans.size());

        for (var span = 0; span < spans.size(); span++) {

            if (!dropped[span]) {
                kept.add(spans.get(span));
            }
        }
        return List.copyOf(kept);
    }

    // Spans that cannot go are settled first, then the ends of what remains.
    //
    // A span holding water back is staying whatever else happens, so locking it and clearing
    // whatever crowds it costs nothing and settles its anchors outright. Left to the ends
    // rule instead, it is never a candidate to keep - it sits in the middle of its chain,
    // both anchors crowded - so the rule keeps the spans EITHER SIDE of it and then cannot
    // drop it, and the chain survives entire. Locked first, the same chain falls into two
    // pairs, each already carrying the one span that had to stay, and the other of each pair
    // goes.
    //
    // Then ends: taking the end of a chain and clearing whatever crowds it hands the next
    // span along an anchor of its own, so a chain of droppable spans unzips into every second
    // one. Only where nothing has an anchor to itself - a formation closed into a ring - does
    // a span get dropped on the pocket rule alone, which breaks the ring and lets the ends
    // rule take over again.
    //
    // Terminates because every round either keeps a span that was not kept before or drops
    // one that was standing, and neither can happen more times than there are spans.
    private static void dropUntilAnchorsAreFree(
            SpanAnchors anchors,
            TracedPocketGraph pockets,
            boolean[] dropped) {

        var kept = new boolean[dropped.length];

        while (true) {

            var settled = findStuckSpanCrowdingOthers(anchors, pockets, dropped, kept);

            if (settled < 0) {
                settled = findRoomiestChainEnd(anchors, pockets, dropped, kept);
            }

            if (settled >= 0) {

                kept[settled] = true;
                dropSpansCrowding(anchors, pockets, dropped, kept, settled);
                continue;
            }

            var span = findSmallestMerge(anchors, pockets, dropped, kept);

            if (span < 0) {
                return;
            }
            dropped[span] = true;
            pockets.mergeAcross(span);
        }
    }

    // A standing span that may not be dropped and still shares an anchor with something.
    // Whichever is found first: they are all staying, so there is nothing to choose between
    // them, and each one settled clears its own anchors.
    private static int findStuckSpanCrowdingOthers(
            SpanAnchors anchors,
            TracedPocketGraph pockets,
            boolean[] dropped,
            boolean[] kept) {

        for (var span = 0; span < dropped.length; span++) {

            if (!dropped[span]
                    && !kept[span]
                    && !pockets.isDroppable(span)
                    && (anchors.isSharedAt(anchors.findFromAnchor(span), dropped)
                        || anchors.isSharedAt(anchors.findToAnchor(span), dropped))) {

                return span;
            }
        }
        return NO_POCKET;
    }

    // The end of a chain worth keeping: a standing span with an anchor no other span shares,
    // and among those the one holding back the most water, so a fan keeps its roomiest arm.
    //
    // Reached only once every span that may not be dropped has been settled, so everything
    // offered here is a span that could go either way.
    private static int findRoomiestChainEnd(
            SpanAnchors anchors,
            TracedPocketGraph pockets,
            boolean[] dropped,
            boolean[] kept) {

        var best = NO_POCKET;
        var bestWater = -1.0;

        for (var span = 0; span < dropped.length; span++) {

            if (dropped[span]
                    || kept[span]
                    || !anchors.isAloneAtEitherAnchor(span, dropped)) {

                continue;
            }

            var water = Math.max(
                pockets.measureSideArea(span, 0), pockets.measureSideArea(span, 1));

            if (water > bestWater) {

                best = span;
                bestWater = water;
            }
        }
        return best;
    }

    // Everything still crowding a kept span's anchors, cleared away - which is what turns its
    // neighbour into the next end. A span that may not be dropped simply stays, and the
    // anchor stays crowded with it.
    private static void dropSpansCrowding(
            SpanAnchors anchors,
            TracedPocketGraph pockets,
            boolean[] dropped,
            boolean[] kept,
            int keptSpan) {

        for (var span = 0; span < dropped.length; span++) {

            if (span == keptSpan
                    || dropped[span]
                    || kept[span]
                    || !pockets.isDroppable(span)
                    || !anchors.isSharingAnAnchor(span, keptSpan)) {

                continue;
            }
            dropped[span] = true;
            pockets.mergeAcross(span);
        }
    }

    // The droppable span at a crowded anchor whose two flanking pockets are jointly smallest,
    // or NO_POCKET where there is none to drop. Compared by the smaller flank first and the
    // larger second. Ties fall to the LATER span in the laying order, which is width order:
    // of two equal merges the wider span gives way, the same way every other contest between
    // spans is settled.
    //
    // Reached only where no span has an anchor to itself, which is a formation closed into a
    // ring - so this breaks rings, and the ends rule does the chains.
    private static int findSmallestMerge(
            SpanAnchors anchors,
            TracedPocketGraph pockets,
            boolean[] dropped,
            boolean[] kept) {

        var best = NO_POCKET;
        var bestSmall = Double.MAX_VALUE;
        var bestLarge = Double.MAX_VALUE;

        for (var span = 0; span < dropped.length; span++) {

            if (dropped[span]
                    || kept[span]
                    || !pockets.isDroppable(span)
                    || (!anchors.isSharedAt(anchors.findFromAnchor(span), dropped)
                        && !anchors.isSharedAt(anchors.findToAnchor(span), dropped))) {

                continue;
            }

            var one = pockets.measureSideArea(span, 0);
            var other = pockets.measureSideArea(span, 1);
            var small = Math.min(one, other);
            var large = Math.max(one, other);

            if (small < bestSmall || (small == bestSmall && large <= bestLarge)) {

                best = span;
                bestSmall = small;
                bestLarge = large;
            }
        }
        return best;
    }

    /**
     * Which spans share an anchor point, asked by span and by anchor.
     *
     * <p>Anchors are matched exactly the way the crossing rule matches them: two ends within
     * touching tolerance are one place. Grouped once up front, so the drop loops ask about
     * groups by number instead of comparing coordinates again on every round.
     */
    private static final class SpanAnchors {

        private final int[] fromAnchor;
        private final int[] toAnchor;
        private final List<double[]> anchorPoints = new ArrayList<>();

        SpanAnchors(List<CellGap> spans) {

            fromAnchor = new int[spans.size()];
            toAnchor = new int[spans.size()];

            for (var span = 0; span < spans.size(); span++) {

                fromAnchor[span] = findOrAddAnchor(spans.get(span).start());
                toAnchor[span] = findOrAddAnchor(spans.get(span).end());
            }
        }

        int findFromAnchor(int span) {
            return fromAnchor[span];
        }

        int findToAnchor(int span) {
            return toAnchor[span];
        }

        boolean isAnyAnchorShared() {

            var noneDropped = new boolean[fromAnchor.length];

            for (var anchor = 0; anchor < anchorPoints.size(); anchor++) {

                if (isSharedAt(anchor, noneDropped)) {
                    return true;
                }
            }
            return false;
        }

        // Whether an anchor still hosts more than one surviving span.
        boolean isSharedAt(int anchor, boolean[] dropped) {
            return countHostedAt(anchor, dropped) > 1;
        }

        // Whether either of a span's anchors is its alone, which is what makes it the end of
        // a chain: the span can be kept without costing anything at that end.
        boolean isAloneAtEitherAnchor(int span, boolean[] dropped) {

            return countHostedAt(fromAnchor[span], dropped) == 1
                || countHostedAt(toAnchor[span], dropped) == 1;
        }

        boolean isSharingAnAnchor(int span, int other) {

            return fromAnchor[span] == fromAnchor[other]
                || fromAnchor[span] == toAnchor[other]
                || toAnchor[span] == fromAnchor[other]
                || toAnchor[span] == toAnchor[other];
        }

        private int countHostedAt(int anchor, boolean[] dropped) {

            var hosted = 0;

            for (var span = 0; span < fromAnchor.length; span++) {

                if (!dropped[span]
                        && (fromAnchor[span] == anchor || toAnchor[span] == anchor)) {

                    hosted++;
                }
            }
            return hosted;
        }

        private int findOrAddAnchor(double[] point) {

            for (var anchor = 0; anchor < anchorPoints.size(); anchor++) {

                if (Points.computeDistance(anchorPoints.get(anchor), point)
                        <= DiscUnion.TOUCHING_TOLERANCE) {

                    return anchor;
                }
            }
            anchorPoints.add(point);
            return anchorPoints.size() - 1;
        }
    }

    /**
     * The water either side of every span, held as a graph the drops can merge without
     * mapping the void again.
     *
     * <p>Afterwards a drop is a union of two nodes with summed areas. Merging enclosed with
     * enclosed is enclosed, so no span's sides can become open that were not open before, and
     * the map never has to be built twice.
     */
    private static final class TracedPocketGraph {

        // How coarsely the void is walked. Fine enough that no gap a reader would call open
        // is stepped over - the narrowest of them are a channel or two across - and coarse
        // enough that a sector is a couple of million squares rather than tens of millions.
        private static final double FLOOD_STRIDE = 100;

        // How far off a span to look for the water beside it, in strides, nearest first. Two
        // is the closest that clears the span's own stamped square; the rest are for a look
        // that lands in another wall and has to reach past it.
        //
        // NEAREST first, and the first readable answer taken, because looking further is how
        // a probe strays over the next wall and reports water that belongs to some other
        // stretch of the map. What is wanted is whatever lies immediately beside the span.
        private static final int[] STRIDES_ASIDE = {2, 3, 4};

        // Where along a span its sides are read. Several stations rather than the middle
        // alone, because a span can face open sea over most of its length and still be the
        // one wall holding water at one end - and that end is the whole of what makes it
        // unsafe. Kept off the very ends, where a station would sit in the corner the span
        // meets its cell at.
        private static final double[] STATIONS = {0.15, 0.3, 0.5, 0.7, 0.85};

        // The station the merge bookkeeping reads its two regions at, which only has to name
        // ONE region per side rather than settle whether the span may go.
        private static final double MIDWAY = 0.5;

        private final int[][] pocketsBySpan;
        private final boolean[] isDroppableBySpan;
        private final int[] mergedInto;
        private final double[] areas;

        private TracedPocketGraph(
                int[][] pocketsBySpan,
                int pockets,
                double[] areas,
                boolean[] isDroppableBySpan) {

            this.pocketsBySpan = pocketsBySpan;
            this.isDroppableBySpan = isDroppableBySpan;
            this.areas = areas;
            this.mergedInto = new int[pockets];

            for (var pocket = 0; pocket < pockets; pocket++) {
                mergedInto[pocket] = pocket;
            }
        }

        static TracedPocketGraph tracePocketGraph(
                List<CellGap> spans,
                Coastlines.TracedCoasts traced,
                SectorGeometryParameters parameters) {

            // Every wall the map draws, as plain segments: the coast's own lines and the
            // spans alike. A flood is stopped by whichever of them it meets, so unlike the
            // boundary walk there is no contest between two walls for one cell's mouth - and
            // that contest is exactly what a crowded anchor is.
            var barriers = new ArrayList<>(
                WallCoverage.collectRingWalls(collectShoreRings(traced)));

            for (var span : spans) {
                barriers.add(new double[][] {span.start(), span.end()});
            }

            var enclosure = VoidEnclosure.mapEnclosedVoid(
                new DiscUnion(traced.union().sites(), parameters.cellRadius()),
                barriers,
                FLOOD_STRIDE);

            var areas = new double[enclosure.countRegions()];

            for (var region = 0; region < areas.length; region++) {
                areas[region] = enclosure.measureRegionArea(region);
            }

            var pocketsBySpan = new int[spans.size()][];
            var isDroppable = new boolean[spans.size()];

            for (var span = 0; span < spans.size(); span++) {

                pocketsBySpan[span] = new int[] {
                    findPocketBeside(enclosure, spans.get(span), 1, MIDWAY),
                    findPocketBeside(enclosure, spans.get(span), -1, MIDWAY)};

                isDroppable[span] = judgeDroppable(enclosure, spans.get(span));
            }
            return new TracedPocketGraph(pocketsBySpan, areas.length, areas, isDroppable);
        }

        // Whether letting the span go can be shown to release nothing.
        //
        // Two answers are safe and they are opposite ones. Water on both sides: the drop
        // merges trapped void with trapped void, and what was held stays held. Open sea on
        // both sides: the span holds nothing at all, so there is nothing to release. What is
        // refused is the span with sea on one side and water on the other - that one IS the
        // wall between them - and the span neither side could be read for, since a side that
        // could not be looked at is not a side that was found open.
        private static boolean judgeDroppable(VoidEnclosure enclosure, CellGap span) {

            var one = judgeSide(enclosure, span, 1);
            var other = judgeSide(enclosure, span, -1);

            return one == other && one != UNREADABLE;
        }

        // What lies along one side of a span: water it holds, the open sea, or nothing that
        // could be read.
        //
        // Several stations along its length, because a span can face the sea over most of it
        // and still hold water at one end - and holding water anywhere is what makes it a
        // wall. So ANY station finding water settles the side; only where none does and some
        // reached the sea is the side open.
        //
        // A station buried in a cell or in a wall votes for neither. It is not evidence of
        // open sea, and counting it as such is exactly how a span with water on both sides
        // comes to look as though it faced the open.
        private static int judgeSide(VoidEnclosure enclosure, CellGap span, int side) {

            var isOpen = false;

            for (var station : STATIONS) {

                var region = findPocketBeside(enclosure, span, side, station);

                if (region >= 0) {
                    return HOLDS_WATER;
                }
                isOpen |= region == OPEN_SEA;
            }
            return isOpen ? OPEN_SEA : UNREADABLE;
        }

        // Both shores of the construction as the lines they are drawn as, which is what a
        // flood has to be stopped by: what closes water off is the line on screen.
        private static List<List<double[]>> collectShoreRings(
                Coastlines.TracedCoasts traced) {

            var rings = new ArrayList<>(Coastlines.collectCoastRings(traced));

            rings.addAll(Coastlines.collectLakeRings(traced));

            return rings;
        }

        // Which held piece of void lies a step off one side of the span, at the given share
        // of the way along it, or NO_POCKET where that step reaches open sea.
        private static int findPocketBeside(
                VoidEnclosure enclosure,
                CellGap span,
                int side,
                double along) {

            var alongX = span.end()[0] - span.start()[0];
            var alongY = span.end()[1] - span.start()[1];
            var length = Math.hypot(alongX, alongY);

            if (length < Limits.MIN_EDGE_LENGTH) {
                return NO_POCKET;
            }

            // The perpendicular, which is the along direction turned a quarter turn, signed
            // by the side asked for.
            for (var strides : STRIDES_ASIDE) {

                var step = side * strides * enclosure.getStride();
                var region = enclosure.findRegionAt(
                    span.start()[0] + alongX * along - alongY / length * step,
                    span.start()[1] + alongY * along + alongX / length * step);

                if (region != VoidEnclosure.INSIDE_WALL) {
                    return region == VoidEnclosure.OPEN_VOID ? OPEN_SEA : region;
                }
            }
            return UNREADABLE;
        }

        boolean isDroppable(int span) {
            return isDroppableBySpan[span];
        }

        // Zero on a side that holds no pocket - open void, or a chord the walk could not lay.
        // Only ever read to order one span against another, never to decide whether one may
        // go, which {@link #isDroppable} answers on its own.
        double measureSideArea(int span, int side) {

            var pocket = pocketsBySpan[span][side];

            return pocket < 0 ? 0 : areas[findMerged(pocket)];
        }

        void mergeAcross(int span) {

            if (pocketsBySpan[span][0] < 0 || pocketsBySpan[span][1] < 0) {
                return;
            }

            var one = findMerged(pocketsBySpan[span][0]);
            var other = findMerged(pocketsBySpan[span][1]);

            if (one == other) {
                return;
            }
            mergedInto[other] = one;
            areas[one] += areas[other];
        }

        private int findMerged(int pocket) {

            while (mergedInto[pocket] != pocket) {

                mergedInto[pocket] = mergedInto[mergedInto[pocket]];
                pocket = mergedInto[pocket];
            }
            return pocket;
        }

    }
}
