package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * The pockets of void between cells, as the holes in the union of the cells' reach discs.
 *
 * <p>A point is void when its nearest site is further than {@code cellRadius}, so the void is
 * exactly the complement of the union of one disc of that radius per site. The boundary of
 * that union is a set of closed cycles of circular arcs, and each cycle is either the outer
 * silhouette of a run of overlapping cells or a hole enclosed by them. The holes are the void
 * the cells bind; everything outside the outer cycles is the void they do not.
 *
 * <p>Every disc has the same radius, which is what makes this cheap and exact rather than a
 * general shape union. Equal radii mean no disc can contain another, so two discs either miss
 * each other or cross at exactly two points, and the part of one circle lying inside another
 * is a single angular interval computed in closed form. Each circle's boundary arcs are then
 * the gaps left when its neighbours' intervals are merged.
 *
 * <p>The arcs link without matching any coordinates. An arc ends where its circle enters some
 * neighbour's disc, and the boundary continues on that neighbour from the point where the
 * neighbour LEAVES the first disc - a point named by the pair of circles rather than by its
 * position, so two circles computing it separately and landing a rounding apart still agree.
 *
 * <p>A pocket is handed back already shaped, the same way {@link CellShaper} hands back a
 * fill polygon rather than a raw cell, and under the same rule: a border keeps the channel,
 * a same-owner seam does not. A pocket ringed entirely by one owner is interior to that
 * owner's cluster and so has no border anywhere on it - it is pushed OUT to meet the
 * surrounding fills instead, closing a gap that would otherwise sit inside a single owner's
 * area for no reason a player could read. A pocket with more than one owner around it, or
 * any unowned cell, keeps the channel and is pulled in.
 *
 * <p>Both the pulling in and the pushing out are taken by moving the REACH, not by
 * offsetting the outline. A pocket is the set of points further than {@code cellRadius} from
 * every site, and distance to the nearest site changes no faster than the distance walked, so
 * the set further than {@code cellRadius + channel} is exactly the pocket inset by the
 * channel, and the set further than {@code cellRadius - channel} is exactly the pocket grown
 * by it. Recomputing the same ring of arcs at the moved radius is therefore an exact offset,
 * and it is also cheaper: each corner is where two circles cross, so it lands where it
 * belongs instead of being mitred to a guess. A general offsetter has to mitre a corner it
 * cannot see the shape of, and a pocket's corners are cusps - two arcs meeting at a sliver -
 * where a miter shoots a long spike out through whatever lies beyond.
 *
 * <p>A pocket too narrow to give up the channel has no outline at all. Handing back its
 * true extent instead would put it flush against the cells, breaking the one rule the whole
 * shaping exists to keep - that nothing touches anything else without the channel between
 * them - and it would do so on exactly the pockets least able to justify the exception. It
 * keeps its centre, its span and the cells around it, so it can still be counted, measured
 * and divided; only the fill is withheld, and a caller with nowhere to draw it can mark the
 * centre.
 *
 * <p>Which is why the true extent is a {@link PocketShaping} a caller has to ask for by name.
 * It answers a different question - WHICH void is there, including the pockets with no room
 * for a channel that otherwise have nothing to show - and its answer is not a map that can be
 * drawn as one.
 *
 * <p>How many cells ring a pocket is not a special case: it is however many arcs the cycle
 * turned out to have.
 */
final class VoidPockets {

    /**
     * One pocket of void, bound by the cells around it.
     *
     * <p>Bound by construction: a pocket is a hole in the union of the reach discs, and a hole
     * is enclosed by definition. Void that opens outward is not a pocket at all - it is what
     * lies outside the union's outer cycles - so there is no flag to read.
     *
     * @param outlines       what to draw for it, at the reach that leaves the border
     *                       channel, or at the reach the surrounding fills stop at when one
     *                       owner rings it. Usually one. EMPTY when the channel closes the
     *                       pocket over, because a pocket with no room for the channel must
     *                       not be drawn without one. MORE THAN ONE when taking the channel
     *                       out pinches the pocket in two, which a single outline could not
     *                       say
     * @param centre         the middle of its true extent, so a pocket with nothing to draw
     *                       can still be pointed at
     * @param adjacentCells  the sites whose cells ring it, in the order its outline meets
     *                       them, which are also its sections
     * @param span           the distance between its two most distant points, measured on
     *                       the void itself and not on what is drawn, so the threshold it is
     *                       tested against does not move with the channel width
     * @param absorbingOwner the owner whose cells ring it on every side, or null when more
     *                       than one owner does, or any unowned cell - non-null means the
     *                       pocket is part of that owner's area rather than void between
     *                       owners
     * @param division       where it is cut across into roughly cell-sized sections, so a
     *                       pocket spanning several cells is not decided as a single piece,
     *                       and how long those sections came out
     */
    record VoidPocket(
        List<List<double[]>> outlines,
        double[] centre,
        List<Integer> adjacentCells,
        double span,
        String absorbingOwner,
        VoidSections.VoidDivision division) {
    }

    /**
     * How much of the channel a pocket takes out of its own outline.
     *
     * <p>Named rather than flagged, because the two are different geometry rather than two
     * settings of one thing: {@link #WITH_CHANNEL} re-traces the void at a moved reach, and
     * {@link #AT_TRUE_EXTENT} hands back the void itself. A caller has to say which of the
     * two maps it is asking about.
     */
    enum PocketShaping {

        /**
         * The channel taken out by moving the reach - out by one where the void lies between
         * owners, in by one where a single owner rings it. What a player sees.
         */
        WITH_CHANNEL,

        /**
         * The void's own extent, with nothing given up anywhere.
         *
         * <p>Every pocket then has an outline, including the ones with no room for a channel
         * that are otherwise handed back with nothing to draw - so this answers WHICH void is
         * there rather than what may be drawn in it. Not a map to keep: a fill taken from it
         * sits flush against every cell around it, which is the one thing the shaping exists
         * to prevent.
         */
        AT_TRUE_EXTENT;

        /**
         * Whether nothing is given up, so the void's own extent is what comes back.
         *
         * <p>Every construction asks this as it decides which discs to walk, and each of them
         * spelling out the comparison is three copies of one rule.
         *
         * @return whether this is the extent itself rather than a channel inside it
         */
        boolean isAtTrueExtent() {
            return this == AT_TRUE_EXTENT;
        }
    }

    /**
     * The knobs one pocket is built under, gathered because they never travel apart.
     *
     * <p>Three answers that have to agree with each other: what the void is measured against,
     * how a long piece of it is divided, and which of the two maps is being asked for. Handed
     * over loose, a caller can give one construction the shipped knobs and another a swept
     * set within the same frame, and the two then describe different sectors while looking
     * like one.
     *
     * @param parameters   the same knobs the cells are built under, so the void takes the
     *                     reach that decides where it begins and the channel the cells leave
     * @param sectionRules how long a piece of a pocket should be before it is cut into more
     *                     than one, and how narrow a crossing has to be to count as a place
     *                     to cut it
     * @param shaping      how much of the channel each pocket takes out of its own outline
     */
    record PocketRules(
        SectorGeometryParameters parameters,
        VoidSections.SectionRules sectionRules,
        PocketShaping shaping) {
    }

    private VoidPockets() {
    }

    /**
     * Finds every pocket of void the cells close around.
     *
     * @param sites       the sites
     * @param ownerBySite each site's owner, index-aligned with {@code sites} and null where
     *                    the site is unowned, to decide which pockets sit inside one owner's
     *                    area rather than between owners
     * @param rules       the knobs to build them under
     * @return the pockets, each with a closed outline
     */
    static List<VoidPocket> findVoidPockets(
            List<double[]> sites,
            List<String> ownerBySite,
            PocketRules rules) {

        var parameters = rules.parameters();
        var arcSegments = parameters.measureArcSegments();

        var trueHoles = DiscUnionBoundary.traceHoles(
            new DiscUnion(sites, parameters.cellRadius()), arcSegments);

        // Traced only where an outline is going to come off them. At the true extent a pocket
        // IS its hole, and these two are the expensive half of the call.
        var isAtTrueExtent = rules.shaping().isAtTrueExtent();

        var withChannel = isAtTrueExtent
            ? List.<VoidHole>of()
            : DiscUnionBoundary.traceHoles(buildDrawnUnion(sites, parameters), arcSegments);

        var atFills = isAtTrueExtent
            ? List.<VoidHole>of()
            : DiscUnionBoundary.traceHoles(
                new DiscUnion(sites, parameters.measureFilledReach()), arcSegments);

        var pockets = new ArrayList<VoidPocket>(trueHoles.size());

        for (var hole : trueHoles) {

            var absorbingOwner = resolveAbsorbingOwner(hole.ringing(), ownerBySite);

            pockets.add(shapeVoidPocket(
                hole,
                findOutlines(hole, absorbingOwner, rules.shaping(), withChannel, atFills),
                absorbingOwner,
                sites,
                rules.sectionRules()));
        }
        return pockets;
    }

    /**
     * Turns one piece of bound void into a pocket, given what it comes to once shaped.
     *
     * <p>Everything a pocket is beyond its own extent - who rings it, whether one owner has it
     * to itself, how wide it is, where it divides - follows from the hole and not from what
     * closed the hole. So this is shared with {@link CoastPockets}, which arrives at bound
     * void a different way entirely: cells that happened to meet in one case, a line the
     * coast smoothing drew in the other.
     *
     * <p>What is NOT shared is the shaping itself, which is the one part that turns on how the
     * void was closed. Void ringed by cells gives up the channel by being retraced at a reach
     * one channel further out; void closed by a drawn line has to give it up against the line
     * as well, which is not a reach and cannot be moved by changing one. The caller that knows
     * which kind it has does that part and hands the answer in.
     *
     * @param hole           the void's own extent
     * @param outlines       what to draw for it once it has given up the channel - empty when
     *                       the channel closes it over, more than one when it pinches in two
     * @param absorbingOwner the one owner that rings it, or null where more than one does.
     *                       The answer rather than the owner table it comes out of, because
     *                       a caller has to know it to shape the pocket at all and reading it
     *                       here as well is one fact read twice
     * @param sites          the sites
     * @param sectionRules   how long a piece should be before it is cut into more than one
     * @return the pocket
     */
    static VoidPocket shapeVoidPocket(
            VoidHole hole,
            List<List<double[]>> outlines,
            String absorbingOwner,
            List<double[]> sites,
            VoidSections.SectionRules sectionRules) {

        var span = VoidSections.measureWidestSpan(hole.corners());

        return new VoidPocket(
            outlines,
            Points.computeMean(hole.boundary()),
            hole.ringing(),
            span,
            absorbingOwner,
            VoidSections.divideVoidPocket(hole, sites, span, sectionRules));
    }

    /**
     * The discs a pocket is drawn against: the cells at the reach that leaves the channel.
     *
     * <p>Traced one channel out from the cells' own border rather than inset afterwards, so a
     * fill stops one channel short of every cell around it by construction. Shared with the
     * other way of arriving at a pocket, because both give up the channel against the cells
     * the same way and only differ in what closed the void.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @return the discs
     */
    static DiscUnion buildDrawnUnion(
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return new DiscUnion(sites, parameters.measureDrawnReach());
    }

    /**
     * The owner that rings a pocket on every side, if one does.
     *
     * <p>An unowned cell counts against it, exactly as a cell's own shaping counts unowned
     * space: a cell facing an unowned neighbour keeps its border channel rather than fusing,
     * and a pocket is shaped by the same rule or the two disagree about the same piece of map.
     *
     * <p>Shared with the other way of arriving at a pocket, because whether one owner has a
     * pocket to itself follows from which cells ring it and not from what closed it.
     *
     * @param ringing     the sites whose circles the void runs on
     * @param ownerBySite each site's owner, index-aligned with the sites and null where the
     *                    site is unowned
     * @return the one owner, or null where more than one rings it or any ringing cell is
     *         unowned
     */
    static String resolveAbsorbingOwner(
            List<Integer> ringing,
            List<String> ownerBySite) {

        String only = null;

        for (var site : ringing) {

            var owner = ownerBySite.get(site);
            if (owner == null) {
                return null;
            }
            if (only != null && !only.equals(owner)) {
                return null;
            }
            only = owner;
        }
        return only;
    }

    // What there is to draw for one pocket. At its true extent that is the hole itself, so
    // nothing is given up and nothing can close over; otherwise the channel is taken by
    // re-tracing at a moved reach, out where the void lies between owners and in where one
    // owner rings it.
    private static List<List<double[]>> findOutlines(
            VoidHole hole,
            String absorbingOwner,
            PocketShaping shaping,
            List<VoidHole> withChannel,
            List<VoidHole> atFills) {

        if (shaping.isAtTrueExtent()) {
            return List.of(hole.boundary());
        }
        return absorbingOwner == null
            ? DiscUnionBoundary.findHolesInside(withChannel, hole)
            : findHoleAround(atFills, hole);
    }

    // The widened hole a pocket opens into, if it stays closed at all. The other way round
    // from the narrowed case: the pocket is inside the widened hole, so it is the pocket's
    // own outline that is tested.
    private static List<List<double[]>> findHoleAround(List<VoidHole> widened, VoidHole hole) {

        var probe = hole.boundary().get(0);

        for (var candidate : widened) {
            if (PolygonRegions.isPointInsideRing(
                    candidate.boundary(), probe[0], probe[1])) {
                return List.of(candidate.boundary());
            }
        }
        return List.of();
    }

}
