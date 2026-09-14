package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

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
 * <p><b>Which is which is asked of the water beside the span</b>, at stations along its whole
 * length. Held on both sides, dropping it merges trapped water with trapped water and what it
 * held stays held. Open sea on both sides, it holds nothing and there is nothing to release.
 * Sea on one side and water on the other, it IS the wall between them, and it stays.
 *
 * <p><b>Asked of a flood rather than of the boundary walk</b>, which cannot answer it here.
 * The walk lays each wall as a chord and gives every mouth on a cell to one chord only, so
 * where two walls meet at a point one of them is refused - and two spans meeting at a point is
 * precisely what a crowded anchor is. At every anchor this pass exists to thin, one span goes
 * unlaid, the region it would have closed never appears, and the span beside it reads as
 * though it faced the open sea. No ordering mends that: whichever wall is offered first,
 * something at that anchor still loses. A flood has no mouths to compete for, and is stopped
 * by any wall it cannot step over - the coast's own lines as much as the spans. See
 * {@link VoidEnclosure}, whose stride is the one claim the answer rests on.
 *
 * <p><b>One drop at a time.</b> Dropping a span FREES its neighbours: the anchors at its two
 * ends each lose a tenant, and a neighbour hemmed in between two crowded anchors becomes a
 * span with room. That is what thins a chain to every second span rather than to its two ends.
 *
 * <p><b>What cannot go is settled first.</b> A span holding water back is staying whatever else
 * happens, so it is locked at once and whatever crowds it dropped. That is what a chain with a
 * load-bearing span in the middle needs: the middle stays, the chain falls into two pairs
 * either side of it, and since each pair already carries the span that had to stay, the other
 * of each pair goes.
 *
 * <p><b>Then from the ends of a chain inward.</b> A span with an anchor all to itself is the
 * end of a chain: it is kept, and whatever crowds its other anchor dropped, which makes the
 * next span along an end in its turn. Five droppable spans resolve to the first, third and
 * fifth that way. Ends are taken roomiest first, so a fan - where every arm is an end - keeps
 * the arm beside the largest water and shrinks to one span.
 *
 * <p>Where nothing has an anchor to itself the formation has closed into a ring, and the round
 * falls back to dropping whichever span lies beside the least water, which breaks the ring and
 * lets the ends rule take over again. A fan of load-bearing spans stays a fan: every line of
 * it holds water back, and trapped void outranks tidiness.
 */
public final class SpanFormations {

    // No span to return, for the searches that come up empty.
    private static final int NO_SPAN = -1;

    // What a look along one side of a span found. Water it holds settles the side outright;
    // the open sea settles it only where no station found water; and a look that never left a
    // cell or a wall settles nothing at all.
    //
    // Held apart from the region numbers a look reports, which are never negative, so a
    // verdict can never be mistaken for a piece of water.
    private static final int HOLDS_WATER = -1;
    private static final int OPEN_SEA = -2;
    private static final int UNREADABLE = -3;

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

        // Settled before the void is mapped: most refreshes lay no two spans on one point,
        // and mapping the void is the whole cost of this pass.
        if (!anchors.isAnyAnchorShared()) {
            return spans;
        }

        return new Thinning(anchors, SpanWaters.mapSpanWaters(spans, traced, parameters), spans)
            .collectSurvivingSpans();
    }

    /**
     * One run of the thinning over one set of spans.
     *
     * <p>A class rather than a run of static methods because the four things it works on -
     * the anchors, the water beside each span, and which spans have been dropped or settled -
     * are one state that every step reads and two of them write. Passed along instead, the
     * same four arguments travel through every method and a step that took only three of them
     * could be handed three that belong to different runs.
     */
    private static final class Thinning {

        private final SpanAnchors anchors;
        private final SpanWaters waters;
        private final List<CellGap> spans;
        private final boolean[] isDropped;
        private final boolean[] isSettled;

        Thinning(SpanAnchors anchors, SpanWaters waters, List<CellGap> spans) {

            this.anchors = anchors;
            this.waters = waters;
            this.spans = spans;
            this.isDropped = new boolean[spans.size()];
            this.isSettled = new boolean[spans.size()];
        }

        /**
         * Runs the thinning and reports what is left standing.
         *
         * <p>Terminates because every round either settles a span that was not settled before
         * or drops one that was standing, and neither can happen more times than there are
         * spans.
         *
         * @return the survivors, in the order they were laid
         */
        List<CellGap> collectSurvivingSpans() {

            while (true) {

                var settling = findStuckSpanCrowdingOthers();

                if (settling < 0) {
                    settling = findRoomiestChainEnd();
                }

                if (settling >= 0) {

                    isSettled[settling] = true;
                    dropSpansCrowding(settling);
                    continue;
                }

                var breaking = findLeastWateredSpan();

                if (breaking < 0) {
                    break;
                }
                isDropped[breaking] = true;
            }

            var kept = new ArrayList<CellGap>(spans.size());

            for (var span = 0; span < spans.size(); span++) {

                if (!isDropped[span]) {
                    kept.add(spans.get(span));
                }
            }
            return List.copyOf(kept);
        }

        // A standing span that may not be dropped and still shares an anchor with something.
        // Whichever is found first: they are all staying, so there is nothing to choose
        // between them, and each one settled clears its own anchors.
        private int findStuckSpanCrowdingOthers() {

            for (var span = 0; span < spans.size(); span++) {

                if (isStanding(span) && !waters.isDroppable(span) && isAtCrowdedAnchor(span)) {
                    return span;
                }
            }
            return NO_SPAN;
        }

        // The end of a chain worth keeping: a standing span with an anchor no other span
        // shares, and among those the one beside the most water, so a fan keeps its roomiest
        // arm.
        //
        // Reached only once every span that may not be dropped has been settled, so everything
        // offered here is a span that could go either way.
        private int findRoomiestChainEnd() {

            var best = NO_SPAN;
            var bestWater = -1.0;

            for (var span = 0; span < spans.size(); span++) {

                if (!isStanding(span) || !anchors.isAloneAtEitherAnchor(span, isDropped)) {
                    continue;
                }

                var water = Math.max(waters.measureSideArea(span, 0),
                    waters.measureSideArea(span, 1));

                if (water > bestWater) {

                    best = span;
                    bestWater = water;
                }
            }
            return best;
        }

        // Everything still crowding a settled span's anchors, cleared away - which is what
        // turns its neighbour into the next end. A span that may not be dropped simply stays,
        // and the anchor stays crowded with it.
        private void dropSpansCrowding(int settled) {

            for (var span = 0; span < spans.size(); span++) {

                if (span != settled
                        && isStanding(span)
                        && waters.isDroppable(span)
                        && anchors.isSharingAnAnchor(span, settled)) {

                    isDropped[span] = true;
                }
            }
        }

        // The droppable span at a crowded anchor lying beside the least water, or NO_SPAN
        // where there is none to drop. Compared by the smaller side first and the larger
        // second; ties fall to the LATER span in the laying order, which is width order, so
        // of two equal claims the wider span gives way - the same way every other contest
        // between spans is settled.
        //
        // Reached only where no span has an anchor to itself, which is a formation closed
        // into a ring, so this breaks rings and the ends rule does the chains. Neither
        // fixture holds one, which is why it is written to need nothing the other rules do
        // not already keep.
        private int findLeastWateredSpan() {

            var best = NO_SPAN;
            var bestSmall = Double.MAX_VALUE;
            var bestLarge = Double.MAX_VALUE;

            for (var span = 0; span < spans.size(); span++) {

                if (!isStanding(span)
                        || !waters.isDroppable(span)
                        || !isAtCrowdedAnchor(span)) {

                    continue;
                }

                var one = waters.measureSideArea(span, 0);
                var other = waters.measureSideArea(span, 1);
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

        // Still on the map and not yet spoken for, which is what every search here is over.
        private boolean isStanding(int span) {
            return !isDropped[span] && !isSettled[span];
        }

        private boolean isAtCrowdedAnchor(int span) {

            return anchors.isSharedAt(anchors.findFromAnchor(span), isDropped)
                || anchors.isSharedAt(anchors.findToAnchor(span), isDropped);
        }
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
        boolean isSharedAt(int anchor, boolean[] isDropped) {
            return countHostedAt(anchor, isDropped) > 1;
        }

        // Whether either of a span's anchors is its alone, which is what makes it the end of
        // a chain: the span can be kept without costing anything at that end.
        boolean isAloneAtEitherAnchor(int span, boolean[] isDropped) {

            return countHostedAt(fromAnchor[span], isDropped) == 1
                || countHostedAt(toAnchor[span], isDropped) == 1;
        }

        boolean isSharingAnAnchor(int span, int other) {

            return fromAnchor[span] == fromAnchor[other]
                || fromAnchor[span] == toAnchor[other]
                || toAnchor[span] == fromAnchor[other]
                || toAnchor[span] == toAnchor[other];
        }

        private int countHostedAt(int anchor, boolean[] isDropped) {

            var hosted = 0;

            for (var span = 0; span < fromAnchor.length; span++) {

                if (!isDropped[span]
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
     * The water lying either side of every span, and whether that lets the span go.
     *
     * <p>Read once from one map of the void, because the map is what this pass costs and
     * every span's answer comes off the same one.
     *
     * <p>The areas are the water as first mapped, and stay that way while spans are dropped.
     * A drop does merge two pieces into one, but nothing here needs to know: the areas only
     * order one span against another, and an ordering settled on the water as it stood is as
     * good an ordering as one settled on the water as it ends up.
     */
    private static final class SpanWaters {

        // How coarsely the void is walked. Fine enough that no gap a reader would call open
        // is stepped over - the narrowest of them are a channel or two across - and coarse
        // enough that a sector is a couple of million squares rather than tens of millions.
        private static final double FLOOD_STRIDE = 100;

        // How far off a span to look for the water beside it, in strides, nearest first. Two
        // is the closest that clears the span's own stamped square; the rest are for a look
        // that lands in another wall and has to reach past it.
        //
        // NEAREST first, and the first readable answer taken, because looking further is how
        // a probe strays over the next wall and reports water belonging to some other stretch
        // of the map. What is wanted is whatever lies immediately beside the span.
        private static final int[] STRIDES_ASIDE = {2, 3, 4};

        // Where along a span its sides are read. Several stations rather than the middle
        // alone, because a span can face open sea over most of its length and still be the
        // one wall holding water at one end - and that end is the whole of what makes it
        // unsafe. Kept off the very ends, where a station would sit in the corner the span
        // meets its cell at.
        private static final double[] STATIONS = {0.15, 0.3, 0.5, 0.7, 0.85};

        // The station the side areas are read at, which only has to name ONE piece of water
        // per side rather than settle whether the span may go.
        private static final double MIDWAY = 0.5;

        private final int[][] watersBySpan;
        private final boolean[] isDroppableBySpan;
        private final VoidEnclosure enclosure;

        private SpanWaters(
                int[][] watersBySpan,
                boolean[] isDroppableBySpan,
                VoidEnclosure enclosure) {

            this.watersBySpan = watersBySpan;
            this.isDroppableBySpan = isDroppableBySpan;
            this.enclosure = enclosure;
        }

        static SpanWaters mapSpanWaters(
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

            var watersBySpan = new int[spans.size()][];
            var isDroppable = new boolean[spans.size()];

            for (var span = 0; span < spans.size(); span++) {

                watersBySpan[span] = new int[] {
                    findWaterBeside(enclosure, spans.get(span), 1, MIDWAY),
                    findWaterBeside(enclosure, spans.get(span), -1, MIDWAY)};

                isDroppable[span] = judgeDroppable(enclosure, spans.get(span));
            }
            return new SpanWaters(watersBySpan, isDroppable, enclosure);
        }

        boolean isDroppable(int span) {
            return isDroppableBySpan[span];
        }

        // How much water lies on one side of a span, or none where that side gives onto the
        // open sea. Only ever read to order one span against another.
        double measureSideArea(int span, int side) {

            var water = watersBySpan[span][side];

            return water < 0 ? 0 : enclosure.measureRegionArea(water);
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

                var water = findWaterBeside(enclosure, span, side, station);

                if (water >= 0) {
                    return HOLDS_WATER;
                }
                isOpen |= water == OPEN_SEA;
            }
            return isOpen ? OPEN_SEA : UNREADABLE;
        }

        // Both shores of the construction as the borders they are, which is what a flood has to
        // be stopped by: what closes water off is where settled space ends, and a flood run
        // against the rounded line instead would leak through every corner the rounding pulled
        // in.
        private static List<List<double[]>> collectShoreRings(
                Coastlines.TracedCoasts traced) {

            var rings = new ArrayList<>(Coastlines.collectCoastOutlines(traced));

            rings.addAll(Coastlines.collectLakeOutlines(traced));

            return rings;
        }

        // Which piece of held water lies a step off one side of the span, at the given share
        // of the way along it - or OPEN_SEA where that step reaches the sea, or UNREADABLE
        // where every step it tried stayed inside a wall.
        private static int findWaterBeside(
                VoidEnclosure enclosure,
                CellGap span,
                int side,
                double along) {

            var alongX = span.end()[0] - span.start()[0];
            var alongY = span.end()[1] - span.start()[1];
            var length = Math.hypot(alongX, alongY);

            if (length < Limits.MIN_EDGE_LENGTH) {
                return UNREADABLE;
            }

            // The perpendicular, which is the along direction turned a quarter turn, signed
            // by the side asked for.
            for (var strides : STRIDES_ASIDE) {

                var step = side * strides * enclosure.getStride();
                var water = enclosure.findRegionAt(
                    span.start()[0] + alongX * along - alongY / length * step,
                    span.start()[1] + alongY * along + alongX / length * step);

                if (water != VoidEnclosure.INSIDE_WALL) {
                    return water == VoidEnclosure.OPEN_VOID ? OPEN_SEA : water;
                }
            }
            return UNREADABLE;
        }
    }
}
