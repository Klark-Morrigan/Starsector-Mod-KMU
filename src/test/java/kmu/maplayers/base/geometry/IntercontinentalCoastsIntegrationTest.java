package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.SectorPipeline.COAST_RULES;
import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.layLinks;
import static kmu.maplayers.base.geometry.SectorPipeline.loadFixture;
import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the coastline the links added, over real sectors.
 *
 * <p>The construction is a second trace cut against the first, so the two things that can be
 * wrong are the cut and what the trace made of the links. The checks ask both of the geometry:
 * that no run lies along a line already drawn, that a run leaves off where a drawn line takes
 * over, and that the shapes the links reached - the isthmuses and the cells that were alone in
 * the void - now carry coastline where they carried none.
 *
 * <p>The set being non-empty is checked first and on its own, since every other claim here is
 * satisfied by drawing nothing at all.
 */
class IntercontinentalCoastsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // Whether the spans sharing an anchor are thinned, which is how the map lays the inlet
    // spans: the thinned set is what the map lays, and an unthinned one is a different laying for the
    // links to be judged against.
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // How far off a wall already down a span may run and still count as doubling it. The width a
    // span is drawn at, which is the shipped setting: two lines closer than that overlap on
    // screen, which is the state a reader calls doubled. Stated here rather than read off the
    // drawing, which this package may not reach into.
    private static final double COAST_SLACK = 120;

    // How close two span feet may stand before one of them moves. The shipped setting, which
    // separates feet that are coincident and leaves the rest where the search put them.
    private static final double ANCHOR_SEPARATION = 120;

    // How far a run may sit from a pinched foot and still count as having reached it, and how
    // near it has to come to be judged at all. A foot on a cell that offers one point is the one
    // place the border may meet it, so a run that comes within half a cell radius and stops
    // anywhere but on it has missed - whereas a run further off is the outer line passing by
    // a foot on a walled shore, which is the fill's business rather than this layer's.
    private static final double ON_THE_FOOT = 1;
    private static final double WITHIN_REACH_OF_A_FOOT = PARAMETERS.cellRadius() / 2;

    // How far off a drawn line a point may sit and still be read as overlapping it, in map
    // units. A reader's measure rather than the cut's: two lines this close are inside the
    // stroke each is drawn at, which is the state a reader calls one line drawn twice.
    private static final double OVER_THE_LINE = 4;

    // How far off a drawn line a run may end and still be read as meeting it. Looser than the
    // measure above and deliberately so: a run ends at the last point the cut called drawn, and
    // the cut treats a wider band as one line so that a stretch re-sampled from a moved corner
    // is not mistaken for new coastline.
    private static final double MEETS_THE_LINE = 30;

    // How far from an island a run may begin and still be that island's own coastline. A rim is
    // drawn on the cell's border, so a generous share of one cell's radius is enough to tell
    // "round this island" from "somewhere else in the sector".
    private static final double ROUND_THE_ISLAND =
        SectorGeometryParameters.DEFAULT_CELL_RADIUS * 1.5;

    private static final Map<String, List<List<double[]>>> SHORES = new ConcurrentHashMap<>();

    private static final Map<String, Coastlines.TracedCoasts> LINKED_TRACES =
        new ConcurrentHashMap<>();

    @Nested
    class FindLinkedShores {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_sector_the_links_changed_gets_coastline_it_had_none_of(String sector) {
            // Asked first and alone, because every other claim below is true of an empty list. A
            // sector with links across it has cells the trace could not draw a coast for and
            // isthmuses that were strokes over the void, so an empty answer is the cut having
            // taken everything.
            assertThat(layLinks(sector))
                .as("%s: nothing was linked, so no coastline can have been added", sector)
                .isNotEmpty();

            assertThat(shoresOf(sector))
                .as("%s: the second trace agreed with the first everywhere", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void no_run_lies_along_a_coastline_already_drawn(String sector) {
            // The whole of what "must not duplicate" means, and the claim the construction
            // exists to make. Asked of every point but the two at a run's ends: a run is MEANT
            // to begin and end on the drawn line, since that is what joins it to the coast it
            // runs into, while a point in between lying there is one line drawn twice.
            var drawn = gatherDrawnLinesOf(sector);
            var doubled = new ArrayList<String>();

            for (var run : shoresOf(sector)) {
                for (var index = 1; index < run.size() - 1; index++) {
                    if (isPointOfAnyRing(run.get(index), drawn, OVER_THE_LINE)) {
                        doubled.add(String.format(
                            "point %d of %d", index, run.size()));
                    }
                }
            }

            assertThat(doubled)
                .as("%s: a run of new coastline lying along the coastline already drawn", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_run_meets_the_drawn_coastline_at_both_ends(String sector) {
            // What makes the added line join the map rather than float over it. A run is cut out
            // of a closed ring at the points the drawn coasts carry, so those points are on both
            // lines - unless the ring has no drawn point at all, which is a shape the first
            // trace knew nothing about and which therefore comes back whole and closed.
            var drawn = gatherDrawnLinesOf(sector);
            var loose = new ArrayList<String>();

            for (var run : shoresOf(sector)) {
                var isClosed = Points.computeDistance(run.get(0), run.get(run.size() - 1))
                    <= OVER_THE_LINE;

                if (isClosed) {
                    continue;
                }

                if (!isPointOfAnyRing(run.get(0), drawn, MEETS_THE_LINE)
                        || !isPointOfAnyRing(run.get(run.size() - 1), drawn, MEETS_THE_LINE)) {
                    loose.add(String.format("a run of %d points", run.size()));
                }
            }

            assertThat(loose)
                .as("%s: an open run of coastline that meets nothing at an end", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_linked_island_is_on_the_border_or_inside_the_seas(String sector) {
            // The cells that had no line at all. This layer draws only the sector's OUTER
            // border, so an island the border sweeps over gets coastline from it - and one the
            // seas swallowed gets none, since the water round it is the fills' subject and its
            // rim is a walled hole's shore. What may not happen is an island with neither: a
            // linked cell on no line anywhere is the walk having refused the very cell the link
            // was laid to reach.
            var traced = traceContinentCoast(sector);
            var reached = collectLinkedIslands(sector);

            assertThat(reached)
                .as("%s: no link reached an island, so this check asks nothing", sector)
                .isNotEmpty();

            var seaRinged = collectWalledShoreCellsOf(sector);
            var bare = new ArrayList<String>();

            for (var island : reached) {
                if (!seaRinged.contains(island)
                        && !isAnyRunNear(traced.union().sites().get(island), shoresOf(sector))) {
                    bare.add(String.format("island %d", island));
                }
            }

            assertThat(bare)
                .as("%s: a linked island on no line of any kind", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void the_added_line_keeps_off_the_cells(String sector) {
            // A coastline is where settled space ends, so it runs on the cells' border and never
            // inside one. Held to the reach the line is drawn at, less what the rounding is
            // allowed to cut off a corner - which is the only thing that moves a point of a
            // drawn coast inward at all.
            var union = traceContinentCoast(sector).union();
            var floor = union.reach() - COAST_RULES.rounding().radius();
            var buried = new ArrayList<String>();

            for (var run : shoresOf(sector)) {
                for (var point : run) {
                    for (var site = 0; site < union.sites().size(); site++) {
                        var separation = Points.computeDistance(union.sites().get(site), point);

                        if (separation < floor) {
                            buried.add(String.format(
                                "cell %d at %.0f, inside %.0f", site, separation, floor));
                        }
                    }
                }
            }

            assertThat(buried)
                .as("%s: new coastline running inside a cell", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_run_that_comes_to_a_pinched_foot_lands_on_it(String sector) {
            // A cell whose whole frontage is one point offers a wall nowhere else to attach, so
            // the wall is laid with no width there and the border it carries can meet the point
            // - which is what a bridge landing on a single-point frontage was always meant to
            // look like. Judged only where a run comes near such a foot at all: this layer draws
            // the outer line, and a foot on a walled shore is met by that shore instead.
            var stoppedShort = new ArrayList<String>();

            for (var foot : collectPinchedFeetOf(sector)) {
                if (isPointOfAnyRing(foot, shoresOf(sector), WITHIN_REACH_OF_A_FOOT)
                        && !isPointOfAnyRing(foot, shoresOf(sector), ON_THE_FOOT)) {
                    stoppedShort.add(String.format("(%.0f, %.0f)", foot[0], foot[1]));
                }
            }

            assertThat(stoppedShort)
                .as("%s: a run came to a pinched foot and stopped short of it", sector)
                .isEmpty();
        }
    }

    // The islands a link actually reaches, which are the ones the second trace can draw a coast
    // for. An island nothing reaches is alone in the void in both traces.
    private static List<Integer> collectLinkedIslands(String sector) {
        var islands = List.copyOf(traceContinentCoast(sector).islands());
        var reached = new ArrayList<Integer>();

        for (var link : layLinks(sector)) {
            for (var cell : List.of(link.fromSite(), link.toSite())) {
                if (islands.contains(cell) && !reached.contains(cell)) {
                    reached.add(cell);
                }
            }
        }
        return reached;
    }

    // Every line the first trace drew, which is what the added coastline must not repeat. Both
    // sides of the water: a stretch drawn over a lake shore is as duplicated as one drawn over
    // an outer coast, and a run may as legitimately meet one as the other.
    private static List<List<double[]>> gatherDrawnLinesOf(String sector) {
        var traced = traceContinentCoast(sector);
        var drawn = new ArrayList<>(Coastlines.collectCoastOutlines(traced));

        drawn.addAll(Coastlines.collectLakeOutlines(traced));
        drawn.addAll(Coastlines.collectWalledShoreOutlines(traced));

        return List.copyOf(drawn);
    }

    // The cells whose border a walled hole's shore runs along, off the trace with the links
    // laid - the same walk the construction cuts, asked here for the water side it leaves out.
    private static Set<Integer> collectWalledShoreCellsOf(String sector) {
        var ringed = new LinkedHashSet<Integer>();

        for (var shore : traceLinkedCoastOf(sector).walledShores()) {
            for (var vertex : shore.vertices()) {
                ringed.add(vertex.circle());
            }
        }
        return ringed;
    }

    // The links laid exactly as the construction lays them - pinched on every cell whose whole
    // frontage is one point - so what is asked of the linked walk here is asked of the walk the
    // shores were cut from rather than of a second laying that merely resembles it.
    private static Coastlines.TracedCoasts traceLinkedCoastOf(String sector) {
        return LINKED_TRACES.computeIfAbsent(sector, named ->
            Coastlines.traceCoastsAcrossWalls(
                loadFixture(named).getSites(),
                PARAMETERS,
                COAST_RULES,
                buildLinkWallsOf(named)));
    }

    private static DiscUnionBoundary.Walls buildLinkWallsOf(String sector) {
        return new DiscUnionBoundary.Walls(
            DiscUnionBoundary.buildChordsFrom(layLinks(sector)),
            PARAMETERS.borderInset(),
            CoastFrontages.collectPinchedCells(traceContinentCoast(sector)));
    }

    // Every foot a laid link puts on a pinched cell: the one point that cell offered, and the
    // one place the border the wall carries is meant to meet.
    private static List<double[]> collectPinchedFeetOf(String sector) {
        var walls = buildLinkWallsOf(sector);
        var feet = new ArrayList<double[]>();

        for (var chord : DiscUnionBoundary.findAttachableChords(
                traceLinkedCoastOf(sector).union(), walls)) {
            if (walls.pinchedCells().contains(chord.fromCircle())) {
                feet.add(chord.findStart());
            }
            if (walls.pinchedCells().contains(chord.toCircle())) {
                feet.add(chord.findEnd());
            }
        }
        return feet;
    }

    private static boolean isAnyRunNear(double[] site, List<List<double[]>> runs) {
        for (var run : runs) {
            for (var point : run) {
                if (Points.computeDistance(site, point) <= ROUND_THE_ISLAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPointOfAnyRing(
            double[] point,
            List<List<double[]>> rings,
            double within) {
        for (var ring : rings) {
            for (var index = 1; index < ring.size(); index++) {
                if (Segments.computeDistanceToPoint(
                        ring.get(index - 1), ring.get(index), point) <= within) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<List<double[]>> shoresOf(String sector) {
        return SHORES.computeIfAbsent(sector, named ->
            IntercontinentalCoasts.findLinkedShores(
                traceContinentCoast(named),
                layLinks(named),
                PARAMETERS,
                COAST_RULES));
    }
}
