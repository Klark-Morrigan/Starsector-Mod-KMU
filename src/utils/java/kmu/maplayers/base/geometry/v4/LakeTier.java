package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidKeys;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The lakes' lines: each lake's coast where it cuts across a bay, and the bridges across the
 * water inside it.
 *
 * <p>One tier, laid in substeps - the coast, then the bridges - each under a label of its own so
 * a piece can tell which of them closed it. Both kinds of line are found elsewhere and untouched
 * here: the smoothing is the whole point of a coastline, and which pairs a bridge is offered, how
 * it lands on its two frontages and which bridges survive the ones already down is the search's
 * whole construction, both tuned by eye against the map. What this tier decides is only where
 * the lines go - into the one partition every tier divides, as walls, each end moved onto the
 * shore by {@link CarriedLines} - so the water they close off comes back as pieces rather
 * than as fills traced by a second construction.
 *
 * <p><b>Only a coast's reaches are laid.</b> A coast alternates between fillets, which run along
 * one cell's own border, and reaches, which cross open void from one cell to another. Along a
 * fillet the piece's boundary already IS the coast, and laying a line there would lay it within
 * the walk's weld of the shore - where it is welded onto the shore's corners and becomes a
 * partial overlap the walk cannot cut. So a fillet lays nothing and a reach lays one wall. Which
 * step is which is the tracer's own answer, and the reaches arrive already told apart.
 *
 * <p><b>A bridge lands only on the lake's frontage.</b> Both ends stand where the lake's coast
 * runs along a cell: a stretch of fillet, or the one point where two reaches meet on a cell the
 * coast only touches. Border the coast stands off from, behind a reach, faces water the coast
 * has already captured, and nothing arrives there. The search anchors on the coast's own points,
 * so that holds of every bridge handed in rather than being checked here.
 *
 * <p>What is drawn is what is laid - the reaches and the bridges - and not the fillets, which are
 * the shore and are already on screen as the piece's own edge.
 *
 * <p><b>What the tier closes, it says.</b> A piece is named by what closed it, and only the
 * tier knows which side of its own lines is which - so what a piece is, and whether anything
 * can still arrive in it, are both answered here rather than read off the labels elsewhere. A
 * piece is walked with itself on the left of every edge, and a reach is handed over with its
 * lake's water on its left, a direction its wall keeps: an edge along a coast wall that runs
 * WITH the wall puts the piece inside the coast, in the lake, and one that runs AGAINST it puts
 * the piece on the reach's land side, in the bay the coast gave up - the margin. A bridge divides
 * the lake, so either side of one is lake water. The bay behind a reach and the water either side
 * of a bridge are closed off; the water in front of a reach is where the bridges are still to
 * land.
 */
public final class LakeTier {

    /**
     * What a reach of a lake's coast is labelled with on the pieces it closes.
     *
     * <p>Its own negative below the frame's, for the reason the frame has one: a piece is named
     * by what closed it.
     */
    public static final int THE_LAKE_COAST = -3;

    /**
     * What a lake bridge is labelled with on the pieces it closes.
     *
     * <p>Apart from the coast's, because water shut in by a bridge is not water shut in by the
     * coast.
     */
    public static final int THE_LAKE_BRIDGES = -4;

    private LakeTier() {
    }

    /**
     * The lakes as the trace hands them over: what the coast lays, and which water is a lake.
     *
     * <p>Both are needed because a lake's water is read off the lines round it, and a lake whose
     * coast never leaves the shore has none: every step of it a fillet, so nothing is laid and
     * its one piece is bounded by cells alone - as a puddle's is. The trace already said which
     * of those holes it drew a coast for, and the ring of cells round each is how it says so.
     *
     * @param reaches the reaches of every lake's coast, each running with its lake's water on
     *                its left
     * @param rings   the cells round each lake, one set per lake
     */
    public record TracedLakes(
        List<CellGap> reaches,
        List<Set<Integer>> rings) {
    }

    /**
     * What the tier says a piece of its own is, and what that kind of piece is called.
     *
     * <p>A piece the tier never touched has no kind at all rather than a kind of its own, so
     * nothing can name such a piece as the tier's.
     */
    public enum Kind {

        /**
         * Water inside a lake's coast: the whole lake where nothing crosses it, or one pocket
         * of it a bridge closed. One prefix for all of it, crossed or not: a pocket a bridge
         * closed is the lake's water still.
         */
        LAKE("void_lake"),

        /**
         * The bay behind a reach: water the coast gave up to the cells, between its line and
         * the shore it stands off from.
         */
        MARGIN("void_lakemargin");

        private final String keyPrefix;

        Kind(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        /**
         * What marks a piece's key as this kind of the tier's, in key characters.
         *
         * @return the prefix
         */
        public String keyPrefix() {
            return keyPrefix;
        }
    }

    /**
     * Lays every reach of the lakes' coasts as a wall, each end moved onto its shore.
     *
     * @param reaches    the reaches, already told apart from the fillets
     * @param frontier   every cell edge facing void, which the ends are moved onto
     * @param sites      the cells' own positions, which the ends are moved towards
     * @param parameters the knobs the map is drawn under
     * @return the walls to lay, and the reaches to draw
     */
    public static CarriedLines.LaidLines layCoastWalls(
            List<CellGap> reaches,
            List<LabelledWall> frontier,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return CarriedLines.layCarriedLines(reaches, frontier, sites, parameters, THE_LAKE_COAST);
    }

    /**
     * Lays every bridge across the lakes as a wall, each end moved onto its shore.
     *
     * @param bridges    the bridges, as the search left them
     * @param frontier   every cell edge facing void, which the ends are moved onto
     * @param sites      the cells' own positions, which the ends are moved towards
     * @param parameters the knobs the map is drawn under
     * @return the walls to lay, and the bridges to draw
     */
    public static CarriedLines.LaidLines layBridgeWalls(
            List<CellGap> bridges,
            List<LabelledWall> frontier,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return CarriedLines.layCarriedLines(bridges, frontier, sites, parameters, THE_LAKE_BRIDGES);
    }

    /**
     * Which of the tier's kinds a piece is, read off its own edges.
     *
     * <p>Against the walls as laid rather than the reaches as found, because the piece's edges
     * lie on the walls to rounding and on the reaches only to within the distance an end was
     * moved onto its shore.
     *
     * <p>An edge behind a reach settles it: the bay is outside the coast, and no bridge crosses
     * the coast to reach into one. Read first so that a piece with such an edge is the margin
     * whatever else lies along it.
     *
     * <p>A piece none of the lines touch is a lake when its cells are exactly a lake's ring: the
     * lake whose coast is all fillets, and every lake while the coast is switched off. Matched
     * by the ring rather than measured, so a second floor cannot come to disagree with the
     * trace's about which hole is a lake and which a puddle.
     *
     * @param piece     the piece, its edges labelled with what they lie on
     * @param laidWalls this tier's walls as laid; those of other tiers are passed over
     * @param lakeRings the cells round each lake the trace drew a coast for
     * @return the kind; empty for a piece the tier never touched
     */
    public static Optional<Kind> readKind(
            Face piece, List<LabelledWall> laidWalls, List<Set<Integer>> lakeRings) {

        var edges = readEdges(piece, laidWalls);

        if (edges.behindAReach()) {
            return Optional.of(Kind.MARGIN);
        }
        if (edges.onABridge()
                || edges.inFrontOfAReach()
                || lakeRings.contains(Set.copyOf(piece.collectCells()))) {

            return Optional.of(Kind.LAKE);
        }
        return Optional.empty();
    }

    /**
     * Whether this tier's lines have closed a piece off, so nothing can arrive in it.
     *
     * <p>The bay behind a reach, and the water either side of a bridge. Not the water in front
     * of a reach that no bridge has divided: that is the lake the bridges are still to land in.
     *
     * @param piece     the piece, its edges labelled with what they lie on
     * @param laidWalls this tier's walls as laid; those of other tiers are passed over
     * @return true where the piece lies behind a reach or against a bridge
     */
    public static boolean isCaptured(Face piece, List<LabelledWall> laidWalls) {

        var edges = readEdges(piece, laidWalls);

        return edges.onABridge() || edges.behindAReach();
    }

    /**
     * What a piece of the tier's is called.
     *
     * @param piece          the piece, which the tier has read as the given kind
     * @param kind           what the tier read it as, which picks the prefix
     * @param sites          the cells' own positions
     * @param systemIdBySite each cell's system ID, index-aligned with the sites
     * @return its key, in the namespace the cells are keyed by
     */
    public static String namePiece(
            Face piece, Kind kind, List<double[]> sites, List<String> systemIdBySite) {

        return VoidKeys.buildKey(
            kind.keyPrefix(), piece.collectCells(), piece.boundary(), sites, systemIdBySite);
    }

    // What the tier's lines say along every ring of a piece, holes included, read once for
    // both questions asked of it.
    private static LaidEdges readEdges(Face piece, List<LabelledWall> laidWalls) {

        var onABridge = false;
        var inFrontOfAReach = false;
        var behindAReach = false;

        for (var ring : piece.collectRings()) {

            var corners = ring.vertices();
            var labels = ring.edgeLabels();

            for (var edge = 0; edge < corners.size(); edge++) {

                if (labels[edge] == THE_LAKE_BRIDGES) {
                    onABridge = true;
                } else if (labels[edge] == THE_LAKE_COAST) {

                    var side = readSideOfReach(
                        corners.get(edge), corners.get((edge + 1) % corners.size()), laidWalls);

                    inFrontOfAReach |= side == Side.WATER;
                    behindAReach |= side == Side.BAY;
                }
            }
        }
        return new LaidEdges(onABridge, inFrontOfAReach, behindAReach);
    }

    // Which side of a reach an edge along its wall puts the piece on: with the wall is the
    // water, against it the bay. An edge on no coast wall says nothing either way.
    private static Side readSideOfReach(
            double[] from, double[] to, List<LabelledWall> laidWalls) {

        for (var wall : laidWalls) {

            if (wall.label() != THE_LAKE_COAST) {
                continue;
            }

            var start = wall.segment().readStart();
            var end = wall.segment().readEnd();

            if (Segments.computeDistanceToPoint(start, end, from) > FaceWalk.ON_THE_LINE
                    || Segments.computeDistanceToPoint(start, end, to) > FaceWalk.ON_THE_LINE) {

                continue;
            }

            var along = (to[0] - from[0]) * (end[0] - start[0])
                + (to[1] - from[1]) * (end[1] - start[1]);

            return along < 0 ? Side.BAY : Side.WATER;
        }
        return Side.OFF_EVERY_REACH;
    }

    // Where an edge along a coast wall puts the piece walked along it.
    private enum Side {
        WATER,
        BAY,
        OFF_EVERY_REACH
    }

    // What one piece's edges lie on, of the tier's lines.
    private record LaidEdges(boolean onABridge, boolean inFrontOfAReach, boolean behindAReach) {
    }
}
