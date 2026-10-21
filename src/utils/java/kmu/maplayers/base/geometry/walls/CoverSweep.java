package kmu.maplayers.base.geometry.walls;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.DiscUnion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static kmu.maplayers.base.geometry.walls.BoundaryTerminals.FROM_SIDE;
import static kmu.maplayers.base.geometry.walls.BoundaryTerminals.NO_CIRCLE;
import static kmu.maplayers.base.geometry.walls.BoundaryTerminals.NO_TERMINAL;
import static kmu.maplayers.base.geometry.walls.BoundaryTerminals.TO_SIDE;

/**
 * Every circle of a union cut into the arcs nothing covers, with the laid walls' mouths taken
 * out alongside the neighbouring discs.
 *
 * <p>The shared radius is what makes this cheap and exact: the part of one circle lying inside
 * another is a single angular interval computed in closed form, and each circle's boundary arcs
 * are the gaps left when its neighbours' intervals are merged. A wall's mouth is one more such
 * interval, named by the wall rather than by a neighbouring circle, so one sweep merges both
 * kinds.
 */
final class CoverSweep {

    // How close a mouth of no width may start to a disc cover's own start and still be read as
    // starting AT it. Arithmetic rather than geometry: a wall pinched to a point lands on a
    // frontage point the coast collapsed onto, and a collapse sits exactly where two cells
    // cross - which is exactly where the neighbour's cover begins - so the two angles are one
    // number arrived at two ways and differ by rounding alone. Far below anything the touching
    // tolerance admits, and far above anything a rounding produces.
    private static final double POINT_MOUTH_EDGE_TIE = 1e-6;

    private CoverSweep() {
    }

    // Every stretch of every circle that nothing covers, which is the whole boundary of the
    // union - outer silhouettes and holes alike, not yet told apart - with the walls' mouths
    // taken out of it alongside the neighbouring discs.
    static List<BoundaryArc> findUncoveredArcs(DiscUnion union, Walls walls) {

        var arcs = new ArrayList<BoundaryArc>();

        for (var circle = 0; circle < union.sites().size(); circle++) {
            arcs.addAll(findUncoveredArcsOn(circle, union, walls));
        }

        return arcs;
    }

    // One circle's uncovered stretches, as the gaps between everything that covers it. Kept
    // as angles rather than points so the two ends of a gap stay attributed to whatever made
    // them, be that a neighbouring disc or a chord's mouth.
    private static List<BoundaryArc> findUncoveredArcsOn(
            int circle,
            DiscUnion union,
            Walls walls) {

        var covers = findCoveringIntervals(circle, union);

        covers.addAll(findChordCovers(circle, union, walls));

        if (covers.isEmpty()) {

            // A disc that nothing overlaps and no chord reaches is a closed cycle on its own.
            var whole = BoundaryTerminals.formatDiscTerminal(
                circle,
                NO_CIRCLE,
                union.sites().size());

            return List.of(new BoundaryArc(circle, 0, Angles.FULL_TURN, whole, whole));
        }
        return buildArcsBetweenCovers(circle, covers);
    }

    // The stretch of one circle lying inside a neighbour's disc. With equal radii the two
    // circles cross symmetrically about the line joining the sites, so the interval is the
    // half-angle whose cosine is half the separation over the reach, either side of that
    // line - no intersection points needed to find it.
    private static List<Cover> findCoveringIntervals(int circle, DiscUnion union) {

        var sites = union.sites();
        var centre = sites.get(circle);
        var covers = new ArrayList<Cover>();

        for (var other = 0; other < sites.size(); other++) {

            if (other == circle) {
                continue;
            }

            var separation = Points.computeDistance(centre, sites.get(other));
            if (separation >= 2 * union.reach() || separation == 0) {
                continue;
            }

            var towards = measureAngleTowards(union, circle, other);
            var halfWidth = Math.acos(separation / (2 * union.reach()));

            covers.add(new Cover(
                Angles.normalise(towards - halfWidth),
                2 * halfWidth,
                BoundaryTerminals.formatDiscTerminal(other, circle, sites.size()),
                BoundaryTerminals.formatDiscTerminal(circle, other, sites.size())));
        }

        return covers;
    }

    // The stretch of one circle taken up by a chord's mouth: the arc facing the circle at the
    // chord's far end, wide enough for the channel to pass through. The boundary arriving at
    // its near edge leaves along the chord and reappears at the far circle's own mouth; the
    // boundary leaving its far edge is where the chord coming the other way put it down.
    private static List<Cover> findChordCovers(
            int circle,
            DiscUnion union,
            Walls walls) {

        var covers = new ArrayList<Cover>();

        for (var index = 0; index < walls.chords().size(); index++) {

            var chord = walls.chords().get(index);
            var isFromSide = chord.fromCircle() == circle;

            if (!isFromSide && chord.toCircle() != circle) {
                continue;
            }

            var mouth = WallMouths.measureMouth(union, chord, circle, walls.channelOn(circle));
            if (mouth == null) {
                continue;
            }

            covers.add(new Cover(
                mouth[0],
                mouth[1],
                BoundaryTerminals.formatChordTerminal(index, isFromSide ? TO_SIDE : FROM_SIDE),
                BoundaryTerminals.formatChordTerminal(index, isFromSide ? FROM_SIDE : TO_SIDE)));
        }

        return covers;
    }

    // Where the sweep hands over at a crossing that a point mouth sits on. Sorted by start
    // alone, a mouth of no width and the disc cover beginning at the same angle are a tie, and
    // the disc coming first puts the mouth INSIDE it: the overlap branch below then hands the
    // boundary to the wall at the disc's far edge rather than at the wall's own end, and the
    // wall's side runs from a point that is not its foot. The mouth coming first hands the
    // boundary to the wall at the crossing and the disc takes over from the wall's far
    // terminal, which is the map. So a point mouth is moved ahead of any disc cover it starts
    // within a rounding of. Only a mouth of no width: a mouth with width starting on a disc's
    // edge is the overlap the branch below was written for.
    private static void bringPointMouthsBeforeDiscEdges(List<Cover> covers) {

        for (var index = 1; index < covers.size(); index++) {

            var cover = covers.get(index);
            var before = covers.get(index - 1);

            if (cover.width() == 0
                    && BoundaryTerminals.isChordTerminal(cover.arrival())
                    && !BoundaryTerminals.isChordTerminal(before.arrival())
                    && cover.start() - before.start() <= POINT_MOUTH_EDGE_TIE) {

                covers.set(index - 1, cover);
                covers.set(index, before);
            }
        }
    }

    // The gaps a circle's covers leave between them, swept once round in order. Each gap runs
    // from wherever the last cover let go to wherever the next takes hold, so its two ends are
    // named by the covers that made them and nothing has to be matched up by position.
    private static List<BoundaryArc> buildArcsBetweenCovers(int circle, List<Cover> covers) {

        covers.sort(Comparator.comparingDouble(Cover::start));
        bringPointMouthsBeforeDiscEdges(covers);

        var origin = covers.get(0).start();
        var windowEnd = origin + Angles.FULL_TURN;
        var reached = openSweepAt(origin, covers);
        var arcs = new ArrayList<BoundaryArc>();

        for (var index = reached.firstUnswept(); index < covers.size(); index++) {

            var cover = covers.get(index);
            if (cover.start() > reached.coveredTo()) {

                arcs.add(new BoundaryArc(
                    circle, reached.coveredTo(), cover.start(),
                    reached.departingFrom(), cover.arrival()));

            } else if (isHandingOverToAWall(reached.departingFrom(), cover)) {

                arcs.add(buildEmptyHandover(circle, reached, cover));
                reached = reached.handOverIfSwallowed(cover);
            }

            reached = reached.advanceOver(cover);
        }

        if (reached.coveredTo() < windowEnd) {

            arcs.add(new BoundaryArc(
                circle, reached.coveredTo(), windowEnd,
                reached.departingFrom(), covers.get(0).arrival()));
        }

        return arcs;
    }

    // Where the sweep starts, which is not simply the first cover.
    //
    // An interval running past the far end of the window covers the near end of it as well, so
    // the sweep has to start already covered up to wherever that reaches - otherwise it reports
    // a gap the wrapping interval actually fills.
    //
    // Failing that it opens on the first cover explicitly, rather than letting the loop reach
    // past the origin on that cover's own width. A cover of no width reaches nowhere past the
    // origin and so would set neither the extent nor the departure, leaving its terminal to
    // begin no arc - and the cycle returning along that wall is then a chain, which is a pocket
    // dropped. Where a wrapping cover already lies over the first, the first is swallowed
    // instead and the handover branch is its reader.
    private static SweepReach openSweepAt(double origin, List<Cover> covers) {

        var coveredTo = origin;
        var departingFrom = NO_TERMINAL;

        for (var cover : covers) {

            var wrapped = cover.start() + cover.width() - Angles.FULL_TURN;
            if (wrapped > coveredTo) {

                coveredTo = wrapped;
                departingFrom = cover.departure();
            }
        }

        return coveredTo > origin
            ? new SweepReach(coveredTo, departingFrom, 0)
            : new SweepReach(
                origin + covers.get(0).width(),
                covers.get(0).departure(),
                1);
    }

    // Whether two overlapping covers are a place the boundary changes hands rather than one
    // stretch of covered circle.
    //
    // At least one of them has to be a wall's mouth: a wall laid against another wall's mouth,
    // or laid so close past a third cell that its mouth and that cell's cover meet. The wall's
    // terminal MUST be paired at such a place, because nowhere else will - a disc cover ending
    // beyond the mouth takes over as the departure, the mouth's own terminal begins no arc, and
    // the walk arriving from the far circle finds nothing to continue onto and throws away every
    // cycle through it. That is a pocket missing behind any wall grazing a cell it does not join.
    //
    // Never where two DISCS are what overlap. Their crossing lies inside the union and the
    // boundary transition between them happens out on their own rims, so joining their terminals
    // would run a cycle through covered space. NO_TERMINAL is not a join either: it is the sweep
    // not yet having passed a cover, not a terminal awaiting a pair.
    private static boolean isHandingOverToAWall(long departingFrom, Cover cover) {

        return departingFrom != NO_TERMINAL
            && (BoundaryTerminals.isChordTerminal(departingFrom)
                || BoundaryTerminals.isChordTerminal(cover.arrival()));
    }

    // The handover itself: an arc of no width, since what the walk wants of it is that its two
    // terminals name each other rather than any length. The boundary comes back along one cover
    // and leaves along the other with nothing on the circle in between, so the stretch between
    // them is empty rather than absent.
    private static BoundaryArc buildEmptyHandover(int circle, SweepReach reached, Cover cover) {

        return new BoundaryArc(
            circle,
            reached.coveredTo(),
            reached.coveredTo(),
            reached.departingFrom(),
            cover.arrival());
    }

    // The direction from one circle's centre to another's, which is where the stretch of the
    // first that the second swallows is centred.
    private static double measureAngleTowards(
            DiscUnion union,
            int fromCircle,
            int toCircle) {

        var from = union.sites().get(fromCircle);
        var to = union.sites().get(toCircle);

        return Angles.normalise(Math.atan2(to[1] - from[1], to[0] - from[0]));
    }

    /**
     * One stretch of a circle that the boundary does not run along.
     *
     * <p>Either a neighbouring disc swallowing it or a chord's mouth passing through it - the
     * sweep merges both the same way, and each names what the arcs either side of it link to
     * so that nothing downstream has to know which kind made the gap.
     *
     * @param start     the angle it begins at, anticlockwise
     * @param width     how far it runs
     * @param arrival   what an arc ending at {@code start} continues onto
     * @param departure what an arc beginning at the far end of it is named by
     */
    private record Cover(
        double start,
        double width,
        long arrival,
        long departure) {
    }

    /**
     * How far round the circle the sweep has got, and which terminal the next gap leaves from.
     *
     * <p>The pair travel together because a gap is named by both: it runs from wherever the
     * last cover let go to wherever the next takes hold, and the terminal is what the walk
     * joins one gap to the next by. Carried apart, an extent advanced without its terminal is
     * an arc beginning at a name nothing else uses.
     *
     * @param coveredTo     how far round the circle is covered so far
     * @param departingFrom the terminal the next gap begins at, or
     *                      {@link BoundaryTerminals#NO_TERMINAL} before the sweep has passed a
     *                      cover at all
     * @param firstUnswept  which cover the walk starts at, the opening one having been read
     */
    private record SweepReach(double coveredTo, long departingFrom, int firstUnswept) {

        // A cover reaching further round than anything so far becomes what the next gap leaves
        // from. One that does not is already inside covered space and changes nothing.
        SweepReach advanceOver(Cover cover) {

            return cover.start() + cover.width() > coveredTo
                ? new SweepReach(cover.start() + cover.width(), cover.departure(), firstUnswept)
                : this;
        }

        // A cover swallowed WHOLE reaches no further round than what is already covered, so
        // advanceOver leaves it alone and its far terminal would be dropped - an arrival with
        // no departure, which is a chain the walk runs off rather than a cycle. Handing the
        // departure on makes the swallowed cover the one the next stretch of boundary leaves
        // from, which is what the pair do on the map.
        //
        // Not the same as a crowded-out mouth. Crowding is a pairwise test against each
        // earlier mouth; what is covered here is the merged extent of every cover so far, so a
        // mouth no single wall nests can still be buried by two of them between them - laid,
        // and with nowhere to depart from.
        SweepReach handOverIfSwallowed(Cover cover) {

            return cover.start() + cover.width() <= coveredTo
                ? new SweepReach(coveredTo, cover.departure(), firstUnswept)
                : this;
        }
    }
}
