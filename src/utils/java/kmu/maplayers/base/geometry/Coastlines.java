package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outer edge of settled space, smoothed: one line through the middle of every cell's
 * frontage on the open void, instead of the scallop of arcs the cells actually make.
 *
 * <p>The silhouette a run of connected cells traces is a chain of circular arcs, and at the
 * scale a sector is read at that reads as a row of bites taken out of the edge rather than as
 * a coast. What the eye wants is the shape those cells collectively occupy, which is the line
 * joining the middle of each cell's frontage - so that is what is drawn.
 *
 * <p><b>Two reaches, and they are not interchangeable.</b> Which cells face the open void, and
 * which void the cells have closed around, is a fact about the map: void is what is further
 * than a cell radius from every site, so that radius is where the walk is decided. Where the
 * line goes is a fact about the drawing, and it goes on the cells' own filled border, a channel
 * inside that. Deciding the walk at the drawing's reach instead makes rings of cells that
 * close in fact come apart, so the coast wanders down into a pocket that every other shape on
 * the map draws as enclosed.
 *
 * <p><b>Nothing is searched for.</b> The silhouettes come out of {@link DiscUnionBoundary} as
 * stretches of coast in walk order, so "the next cell along the coast" is the next element of
 * the run. There is no nearest-neighbour matching and no ordering to get wrong, and a cell
 * facing the void on two separate stretches - a strait, or the inside of a C - contributes one
 * stretch per frontage without needing a rule for it.
 *
 * <p><b>A coast may never pass through a cell.</b> Joining the middles of two frontages with a
 * straight line does exactly that wherever the coast turns inward, which is at every waist
 * between two cells - so where the line would cut a cell, the point gives way instead of the
 * line. It slides along that cell's own border to the nearest place to the middle that the
 * line can actually reach, which needs no search: seen from a point, the part of a circle a
 * straight line can touch without cutting through it is the arc facing that point, half a turn
 * wide less {@code acos(reach / distance)}. Both ends give way, the far cell blocking the
 * approach and the near cell blocking its own departure, and where both are blocked at once
 * the two settle on the common tangent.
 *
 * <p>That is why a cell contributes an arrival point and a departure point rather than one
 * point. The line coming in and the line going out can want it slid in opposite directions,
 * which is precisely what happens at the inward turns this exists to fix; joined along the
 * cell's own border, the two can disagree without the coast leaving the boundary. Where
 * nothing blocks, both land on the middle and it is one point again.
 *
 * <p><b>Why cells get skipped.</b> A run of cells packed tightly along a coast contributes a
 * stretch every few hundred units, and joining all of them reproduces the scallop at a
 * slightly smaller amplitude rather than smoothing it. Dropping the ones that fall within a
 * set distance of the last one kept is what turns the chain into a line.
 *
 * <p>Three things bound that. Skipping runs out after a set number in a row, so a dense coast
 * cannot be swallowed whole and reduced to a triangle. <b>A cell a bridge attaches to is never
 * skipped</b>, because a bridge's wall is boundary the coast has to stay outside of, and
 * cutting the corner across one puts the coast on the wrong side of a shape already drawn.
 * And a skip is provisional: a cell nothing else knows about can sit in the gap a jump opens
 * up, so every jump is tested against every cell, and whichever one blocks it is put back.
 */
final class Coastlines {

    // Each pass slides one end to the furthest the other allows, so the two close on the
    // common tangent from opposite directions. Four is past the point where the movement
    // stops being visible; the clearance check afterwards is what says whether it was enough.
    private static final int CLAMP_PASSES = 4;

    // A skip put back changes the jump either side of it, which can expose a different cell.
    // Bounded rather than run to a fixed point, because each pass keeps strictly more cells
    // and the worst case - every cell kept - is the unsmoothed coast rather than a wrong one.
    private static final int REPAIR_PASSES = 4;

    // How far inside a cell a straight run may reach and still count as touching it rather
    // than crossing it. A run begins and ends ON two borders, so without a hair of slack
    // every one of them would count itself as crossing the two cells it runs between.
    static final double TOUCHING_TOLERANCE = 1;

    // No stretch stands between two kept ones, or none of those that do is in anything's way.
    private static final int NOTHING_BLOCKING = -1;

    private Coastlines() {
    }

    /**
     * The knobs a coast is traced under, in the units they are set in.
     *
     * <p>Multiples of a cell radius rather than distances, because that is what they mean:
     * "closer than a cell across" is a claim about the map, where a number of units stops
     * being one the moment the reach slider moves. Converting to distances is this class's
     * job and happens once, so no caller can do it differently.
     *
     * @param bridgeReachMultiple how far apart two cells may sit and still be walled together,
     *                            centre to centre, in cell radii
     * @param skipMultiple        how near the last kept point a cell must be to be dropped,
     *                            in cell radii
     * @param maxSkips            how many may be dropped in a row before one is kept whatever
     *                            its distance, so a dense stretch still contributes a point
     */
    record CoastRules(
        double bridgeReachMultiple,
        double skipMultiple,
        int maxSkips) {
    }

    /**
     * What the viewer opens on, and so what every drawing of a coast describes.
     *
     * <p>Declared once here rather than beside each drawing. Three copies of these numbers is
     * how a report comes to describe a different map from the one on screen without either of
     * them saying so.
     */
    static final CoastRules DEFAULT_RULES = new CoastRules(4, 1, 5);

    /**
     * A traced coast and the two things it was traced against.
     *
     * <p>Handed back together because everything asked of a coast afterwards needs one or
     * both: which cells a run crosses needs the discs, and how many stretches there were
     * before smoothing needs the walls. Rebuilt separately by each asker, they can be built
     * from knobs that have since moved.
     *
     * @param coasts      one closed run of points per run of connected cells
     * @param silhouettes the stretches of coast the cells make, in walk order, before any
     *                    smoothing. Carried rather than walked again by whatever wants them:
     *                    the walk is not cheap, and a second one is a second answer that can
     *                    disagree with the coast it is supposed to describe
     * @param trapped     the void the coast closed off, one per straight reach that shut
     *                    anything in. Handed back rather than worked out afterwards because
     *                    only the walk knows which stretches a reach bypassed, and because a
     *                    pocket's edge and the coast are two sides of one line
     * @param onBorders   the same coasts on the cells' TRUE borders rather than their fills.
     *                    What a reach of coast shut in is decided by intersecting it with the
     *                    cell borders and the bridges, and those meet at the true reach - a
     *                    reach taken off the drawn line passes a channel inside the borders
     *                    it is meant to touch
     * @param union       the discs it is DRAWN against, which is not the wider set the walk
     *                    was decided on - the crossing checks have to ask about the line that
     *                    actually got drawn
     */
    record TracedCoasts(
        List<List<CoastVertex>> coasts,
        List<List<CoastVertex>> onBorders,
        List<List<DiscUnionBoundary.CoastMark>> silhouettes,
        List<TrappedStretch> trapped,
        DiscUnion union) {
    }

    /**
     * Traces a whole sector's coast: the one place that knows how a coast is built.
     *
     * <p>The recipe is six steps - find the bridges, turn them into chords, wall the void
     * with them, take the discs at both reaches a coast needs, convert the knobs from cell
     * radii to distances, and trace. Spelled out at each of the three places that wanted a
     * coast, it had to be kept in step by hand, and changing a reach meant remembering all
     * of them.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the coast is traced under
     * @return the coast, and what it was traced against
     */
    static TracedCoasts traceSectorCoasts(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            CoastRules rules) {

        var walls = new DiscUnionBoundary.Walls(
            DiscUnionBoundary.buildChordsFrom(VoidBridges.findVoidBridges(
                sites,
                parameters.cellRadius(),
                parameters.cellRadius() * rules.bridgeReachMultiple())),
            parameters.borderInset());

        // Which void the cells bind is a fact about the map rather than about any one
        // drawing of it, and it is settled at the reach that DEFINES void: a point is void
        // when its nearest site is further than the cell radius. Walked a channel short of
        // that, rings of cells that close in fact come apart, the void they held joins the
        // open sea, and the silhouette runs down into a pocket every other shape on the map
        // draws as enclosed - which is what put a coastline across the middle of one.
        var bounding = new DiscUnion(sites, parameters.cellRadius());

        // Drawn at the reach the CELLS are filled to, though. Where a coast runs along a
        // cell it should be the cell's own border and nothing else; drawn a channel further
        // out it sits a channel outside every cell it hugs.
        //
        // Taking the stretches from the wider walk and drawing them here costs nothing,
        // because a cell's frontage only GROWS as the reach comes in - a neighbour covers
        // less of a smaller circle - so every stretch the walk found is still a stretch of
        // border here, with room to spare at both ends.
        var drawn = new DiscUnion(sites, parameters.measureFilledReach());

        var silhouettes = DiscUnionBoundary.traceSilhouetteCoasts(
            bounding, walls, parameters.measureArcSegments());

        var bridged = findBridgedCircles(bounding, walls);

        var smoothing = new SmoothingRules(
            rules.skipMultiple() * parameters.cellRadius(),
            rules.maxSkips(),
            parameters.measureArcSegments());

        var smoothed = smoothSilhouettes(silhouettes, drawn, bridged, smoothing);

        // The same coast a second time, on the cells' true borders instead of their fills.
        // Only the radius differs - same stretches, same skips, same reaches - so the two are
        // the same line at two distances, not two answers about where the coast is.
        //
        // Wanted because a reach of coast is boundary as well as decoration. Anything that
        // has to work out what a reach shut in must intersect it with the cell borders and
        // the bridges, and those meet at the true reach; taken from the drawn line instead it
        // passes a channel INSIDE the borders it is supposed to touch, and every shape built
        // on the intersection is wrong by that channel.
        var onBorders = smoothSilhouettes(silhouettes, bounding, bridged, smoothing);

        return new TracedCoasts(
            smoothed.coasts(), onBorders.coasts(), silhouettes, smoothed.trapped(), drawn);
    }

    /**
     * How aggressively a coast is smoothed, in the units the smoothing works in.
     *
     * <p>One record because the two only mean anything together: the distance decides how
     * much is dropped and the cap decides how much may be dropped at once, and a distance
     * handed round without its cap is the setting that eats a whole coastline.
     *
     * @param skipDistance        how near the last kept point a cell must be to be dropped
     * @param maxConsecutiveSkips how many may be dropped in a row before one is kept whatever
     *                            its distance, so a dense stretch still contributes a point
     * @param arcSegments         how finely a half-turn of arc is sampled, which is how
     *                            smooth the fillets come out and so the third thing deciding
     *                            what a smoothed coast looks like
     */
    private record SmoothingRules(
        double skipDistance,
        int maxConsecutiveSkips,
        int arcSegments) {
    }

    /**
     * One point of a smoothed coast, and the cell whose border it sits on.
     *
     * <p>The cell travels with the point because the two things drawn here are not the same
     * kind of run: a step between two points on ONE cell is a fillet along that cell's own
     * border, where a step between two cells is a straight reach across open void. Only the
     * second has to clear every cell, and a check that could not tell them apart would read
     * every fillet as a coast cutting into the cell it is drawn on.
     *
     * @param point  where the coast passes
     * @param circle whose cell's border it is on
     */
    record CoastVertex(
        double[] point,
        int circle) {
    }

    /**
     * Traces the smoothed outer edge of every run of connected cells.
     *
     * <p>Judged on the points that came out rather than on the cells that went in. A cell
     * contributes a whole run of border rather than a single point, so one cell alone in the
     * void encloses an area perfectly well - its own - and two that touch enclose the pair.
     * Only a run that came out too small to be a shape at all is dropped, which after the
     * winding is settled means one that could not be built.
     *
     * @param silhouettes the stretches of coast the cells make, in walk order
     * @param union       the discs to draw against
     * @param bridged     the cells a laid wall attaches to, which are never skipped
     * @param rules       how aggressively to smooth, and how finely
     * @return one closed run of points per run of connected cells, and the void they shut in
     */
    private static SmoothedSector smoothSilhouettes(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var smoothed = new ArrayList<List<CoastVertex>>();
        var trapped = new ArrayList<TrappedStretch>();

        for (var coast : silhouettes) {

            var built = buildClearedOutline(
                coast, keepSmoothedMarks(coast, union, bridged, rules), union, rules);

            // A coast too small to be a shape takes its trapped void with it. The two are
            // the two sides of one line, so keeping the pockets of a coast nobody draws
            // would fill space against an edge that is not on the map.
            if (built.outline().size() >= Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {

                smoothed.add(built.outline());
                trapped.addAll(built.trapped());
            }
        }
        return new SmoothedSector(smoothed, trapped);
    }

    /**
     * Every smoothed coast in a sector, and all the void they closed off between them.
     *
     * @param coasts  one closed run of points per run of connected cells
     * @param trapped the void they shut in, pooled - which silhouette a pocket came off is
     *                not something anything asks, and the pockets are shaped as one set
     */
    private record SmoothedSector(
        List<List<CoastVertex>> coasts,
        List<TrappedStretch> trapped) {
    }

    /**
     * The ring on its own, for anything that only wants to draw or fill it.
     *
     * @param coast a smoothed coast
     * @return its points, in order
     */
    static List<double[]> collectPoints(List<CoastVertex> coast) {

        var points = new ArrayList<double[]>(coast.size());

        for (var vertex : coast) {
            points.add(vertex.point());
        }
        return points;
    }

    // The cells a laid wall attaches to. Asked of the laid chords rather than of every bridge
    // offered, because a bridge that was never drawn has no wall for the coast to cut across
    // and protecting its cells would only cost smoothing for nothing.
    private static Set<Integer> findBridgedCircles(
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var bridged = new LinkedHashSet<Integer>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            bridged.add(chord.fromCircle());
            bridged.add(chord.toCircle());
        }
        return bridged;
    }

    // Which stretches the coast is drawn through: the ones too far from their neighbour to be
    // dropped, plus the ones dropping would have put a cell across the jump.
    private static List<Integer> keepSmoothedMarks(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = markSpacedOutStretches(coast, union, bridged, rules);

        for (var pass = 0; pass < REPAIR_PASSES; pass++) {

            if (!restoreBlockingStretches(coast, union, isKept)) {
                break;
            }
        }

        return collectKeptPositions(isKept);
    }

    private static List<Integer> collectKeptPositions(boolean[] isKept) {

        var kept = new ArrayList<Integer>();

        for (var index = 0; index < isKept.length; index++) {

            if (isKept[index]) {
                kept.add(index);
            }
        }
        return kept;
    }

    // One pass along the coast, dropping stretches that sit too near the last one kept. The
    // first is always kept, because there is nothing yet for it to be near.
    private static boolean[] markSpacedOutStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = new boolean[coast.size()];
        var lastKept = (double[]) null;
        var skippedInARow = 0;

        for (var index = 0; index < coast.size(); index++) {

            var mark = coast.get(index);
            var middle = findMidpoint(union, mark);

            if (lastKept != null
                    && !bridged.contains(mark.circle())
                    && skippedInARow < rules.maxConsecutiveSkips()
                    && Points.computeDistance(lastKept, middle) < rules.skipDistance()) {

                skippedInARow++;
                continue;
            }
            isKept[index] = true;
            lastKept = middle;
            skippedInARow = 0;
        }
        return isKept;
    }

    // Puts back any stretch a jump turned out to cross. A jump is tested against every cell,
    // not only the two it runs between, because the cell in the way is by definition one
    // neither end knows about - and if that cell is one of the stretches skipped over, keeping
    // it is what stops the jump being made at all.
    private static boolean restoreBlockingStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept) {

        var kept = collectKeptPositions(isKept);

        if (kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return false;
        }

        var restored = false;

        for (var step = 0; step < kept.size(); step++) {

            var from = kept.get(step);
            var to = kept.get((step + 1) % kept.size());
            var blocker = findBlockedStretch(coast, union, isKept, from, to);

            if (blocker >= 0) {

                isKept[blocker] = true;
                restored = true;
            }
        }
        return restored;
    }

    // Which of the stretches skipped between two kept ones to put back, or NOTHING_BLOCKING
    // when the jump is clear.
    //
    // The jump is judged first, by the one clearance test the drawing itself uses, and only a
    // jump that failed goes looking for something to blame. That ordering is what keeps one
    // definition of "in the way" in play: an inline scan with its own threshold answered the
    // same question a hair differently, and a method holding two definitions of one question
    // is how the next reader picks the wrong one.
    //
    // The blame falls on the skipped stretch actually across the jump where there is one, and
    // otherwise simply on the first skipped. A jump can cut into one of the two cells it runs
    // BETWEEN, which no skipped stretch can be blamed for - it happens where both ends block
    // and the common tangent has to be clamped back onto a frontage that does not reach it,
    // leaving a run tangent to nothing. Putting any skipped stretch back is still the remedy:
    // the one long jump becomes two short ones, and two neighbouring stretches can always fall
    // back on the boundary's own join between them, which cuts nothing by construction.
    private static int findBlockedStretch(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept,
            int from,
            int to) {

        var jump = new StraightRun(union, coast.get(from), coast.get(to));
        var edge = resolveEdge(jump);

        if (isRunClearOfEveryCell(jump, edge)) {
            return NOTHING_BLOCKING;
        }

        var firstSkipped = NOTHING_BLOCKING;

        for (var step = 1; step < coast.size() && !isKept[(from + step) % coast.size()]; step++) {

            var index = (from + step) % coast.size();

            if (firstSkipped < 0) {
                firstSkipped = index;
            }
            if (jump.isRunBetween(coast.get(index).circle())) {
                continue;
            }
            if (jump.measureIncursion(edge, coast.get(index).circle()) > TOUCHING_TOLERANCE) {
                return index;
            }
        }
        return firstSkipped;
    }

    // The kept stretches turned into a closed run of points: a fillet along each cell's own
    // border from where the coast arrives to where it leaves, and a straight reach from there
    // to the next cell. The void each of those reaches closes off comes out of the same walk.
    private static SmoothedCoast buildClearedOutline(
            List<DiscUnionBoundary.CoastMark> coast,
            List<Integer> kept,
            DiscUnion union,
            SmoothingRules rules) {

        // A run of one has no reach to any other cell, so there is nothing to clamp against
        // and its whole frontage is the coast. That is a cell alone in the void, whose coast
        // is its own border - drawn over the top of it and so invisible, which is right, and
        // a coast lying along a border traps nothing between the two.
        if (kept.size() == 1) {

            var only = coast.get(kept.get(0));

            return new SmoothedCoast(
                buildVertices(
                    only,
                    sampleFillet(union, only, only.fromAngle(), only.toAngle(), rules)),
                List.of());
        }

        var arriveAngles = new double[kept.size()];
        var departAngles = new double[kept.size()];

        for (var index = 0; index < kept.size(); index++) {

            var next = (index + 1) % kept.size();

            var edge = findClearEdge(
                new StraightRun(
                    union, coast.get(kept.get(index)), coast.get(kept.get(next))),
                kept.get(next) == (kept.get(index) + 1) % coast.size());

            departAngles[index] = edge.departAngle();
            arriveAngles[next] = edge.arriveAngle();
        }

        var outline = new ArrayList<CoastVertex>();
        var trapped = new ArrayList<TrappedStretch>();

        for (var index = 0; index < kept.size(); index++) {

            var mark = coast.get(kept.get(index));

            outline.addAll(buildVertices(
                mark,
                sampleFillet(
                    union, mark, arriveAngles[index], departAngles[index], rules)));

            var closed = describeTrappedVoid(
                coast, kept, index, arriveAngles, departAngles);

            if (closed != null) {
                trapped.add(closed);
            }
        }
        return new SmoothedCoast(outline, trapped);
    }

    /**
     * Which stretch of border one straight reach of coast closed off behind it.
     *
     * <p>A reach cuts the corner off a notch, and what it cuts off is bound: open sea on the
     * far side of the line, cells on the near side, no way between. That is a pocket in the
     * same sense an enclosed one is - closed by a line the smoothing drew rather than by
     * cells that happened to meet.
     *
     * <p>Described rather than drawn. What comes back is which stretches the reach passed
     * over and where on the two ends it left and landed, because a pocket has to be built at
     * more than one reach - its own extent and the reach it is drawn at - and only the
     * description is common to both. Handing back a finished ring would fix the reach here,
     * where nothing knows what a channel is.
     *
     * <p>Worked out here rather than read back off the drawn coast, though, because it needs
     * the stretches the reach BYPASSED and this walk is the only place that still knows which
     * those were. Recovered from the line it would be a search - which sampled point belongs
     * to which cell, and which cells fell between two that survived - and searching is what
     * this construction exists to avoid.
     *
     * @return what the reach at {@code index} shut in, or null when it shut in nothing, which
     *         is what a reach that fell back on the boundary's own join does
     */
    private static TrappedStretch describeTrappedVoid(
            List<DiscUnionBoundary.CoastMark> coast,
            List<Integer> kept,
            int index,
            double[] arriveAngles,
            double[] departAngles) {

        var next = (index + 1) % kept.size();
        var bypassed = new ArrayList<DiscUnionBoundary.CoastMark>();

        bypassed.add(coast.get(kept.get(index)));

        for (var step = 1; step <= coast.size(); step++) {

            var at = (kept.get(index) + step) % coast.size();

            if (at == kept.get(next)) {
                break;
            }
            bypassed.add(coast.get(at));
        }
        bypassed.add(coast.get(kept.get(next)));

        // A reach that leaves the near cell at the very end of its frontage and lands on the
        // far cell at the very start of its own has passed over no border at all. That is the
        // boundary's own join, taken where no straight run could be drawn, and it shuts in
        // nothing because it IS the boundary.
        if (bypassed.size() == 2
                && departAngles[index] >= coast.get(kept.get(index)).toAngle()
                && arriveAngles[next] <= coast.get(kept.get(next)).fromAngle()) {

            return null;
        }
        return new TrappedStretch(bypassed, departAngles[index], arriveAngles[next]);
    }

    /**
     * One stretch of border a reach of coast closed off, and where the reach met its ends.
     *
     * <p>Angles rather than points, so the same stretch can be built at any reach. A pocket
     * needs two of them - its own extent, and the reach it is drawn at once it has given up
     * the channel - and a pair of points would only ever describe one.
     *
     * @param bypassed  the stretches the reach passed over, in walk order. The first and last
     *                  are the cells it runs between and are used in part; everything between
     *                  them is a cell the smoothing dropped, used whole
     * @param fromAngle where on the first the reach left it
     * @param toAngle   where on the last the reach landed
     */
    record TrappedStretch(
        List<DiscUnionBoundary.CoastMark> bypassed,
        double fromAngle,
        double toAngle) {
    }

    /**
     * One smoothed coast and the void its reaches closed off.
     *
     * <p>Paired because they come out of one walk and describe the two sides of one line.
     * Asked for separately they would be two walks, and two walks over one coast can disagree
     * about where the line went - which would put a pocket's edge somewhere the coast is not.
     *
     * @param outline the line to draw
     * @param trapped the void it shut in, one ring per reach that closed anything off
     */
    private record SmoothedCoast(
        List<CoastVertex> outline,
        List<TrappedStretch> trapped) {
    }

    private static List<CoastVertex> buildVertices(
            DiscUnionBoundary.CoastMark mark,
            List<double[]> points) {

        var vertices = new ArrayList<CoastVertex>(points.size());

        for (var point : points) {
            vertices.add(new CoastVertex(point, mark.circle()));
        }
        return vertices;
    }

    // The run of a cell's own border the coast keeps to, from where it arrives to where it
    // leaves. Sampled rather than cut straight across, so that a cell whose two neighbours
    // pull it far apart is still traced around rather than through.
    //
    // Bounded by the cell's OWN frontage rather than by any fixed sweep. The last cell of a
    // chain faces void nearly the whole way round, so its coast has to wrap most of a turn to
    // come back down the other side - and a rule that stopped at a half turn collapsed
    // exactly those cells to a point, which is what put a spike on every chain end and drove
    // the two runs either side of it straight through the cell.
    //
    // Both ends are already clamped into that frontage, so the arrival cannot sit past the
    // departure unless the cell's two neighbours pulled each beyond the other. There is no
    // run of border between them then, and the point they are least far from is the only
    // answer left.
    private static List<double[]> sampleFillet(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double arriveAngle,
            double departAngle,
            SmoothingRules rules) {

        var sweep = departAngle - arriveAngle;

        if (sweep <= 0) {
            return List.of(findPointAt(union, mark, (arriveAngle + departAngle) / 2));
        }

        var steps = Math.max(
            1, (int) Math.ceil(rules.arcSegments() * sweep / Angles.HALF_TURN));
        var points = new ArrayList<double[]>(steps + 1);

        for (var step = 0; step <= steps; step++) {
            points.add(findPointAt(union, mark, arriveAngle + sweep * step / steps));
        }
        return points;
    }

    // The straight reach between two cells where one can be drawn, and the boundary's own
    // join where it cannot.
    //
    // The reach is what buys the pocket space, and it is what nearly every pair gets: two
    // cells that overlap still have an outer tangent, and a run along it spans the notch
    // between them from outside, taking the void in the notch with it.
    //
    // What stops it is a THIRD cell covering the direction the tangent needs. The tangent
    // point is then not on this cell's frontage at all, and clamping it back onto the
    // frontage moves it - by most of a right angle in the worst measured case - which leaves
    // a line that is no longer tangent to anything and cuts straight through a cell. For
    // those pairs there is no straight run from one frontage to the other that stays outside
    // both, so inventing one can only produce a bad one.
    //
    // Two stretches the walk itself put next to each other need no line invented for them:
    // the boundary already joins them, at the far end of one frontage and the near end of the
    // other, which is one point where the cells overlap and the two sides of a mouth where a
    // bridge runs between them. That join cannot cut anything, because it IS the boundary.
    //
    // Only where the reach fails, though. Taking this join for every adjacent pair - which an
    // earlier version did - hugs the whole coast and gives up every pocket the smoothing was
    // for.
    private static EdgeAngles findClearEdge(StraightRun run, boolean isAdjacent) {

        var reach = resolveEdge(run);

        if (!isAdjacent || isRunClearOfEveryCell(run, reach)) {

            return reach;
        }
        return new EdgeAngles(run.from().toAngle(), run.to().fromAngle());
    }

    // Whether a straight reach passes outside every cell on the map. Every cell, including
    // the two it runs between - those it touches rather than enters, so a hair of slack is
    // what tells the two apart.
    private static boolean isRunClearOfEveryCell(StraightRun run, EdgeAngles edge) {

        for (var circle = 0; circle < run.union().sites().size(); circle++) {

            if (run.measureIncursion(edge, circle) > TOUCHING_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    // Where one straight reach of coast leaves one cell and lands on the next. Each end is
    // slid to the nearest place to its own middle that the other end can see, and each pass
    // moves one of them to the furthest the other now allows - so two ends that both block
    // settle towards the common tangent instead of one of them winning outright.
    //
    // The passes leave one end a step stale, though: the arrival was worked out against the
    // departure BEFORE the departure last moved. Where that still clears both cells it is the
    // answer, and where it does not the two are put straight onto the tangent, which is the
    // configuration they were converging on and is exact rather than approached.
    private static EdgeAngles resolveEdge(StraightRun run) {

        var clamped = clampEdgeEnds(run);

        // Clear is asked of EVERY cell rather than only the two the run joins. The two are
        // what the clamp was working against, so a run can satisfy both and still shave a
        // third cell that neither end knows about - and that run was then accepted, which is
        // where the shallow crossings came from.
        return isKeepingCellsLeft(run, clamped) && isRunClearOfEveryCell(run, clamped)
            ? clamped
            : findTangentEdge(run);
    }

    // Whether a straight run keeps the cells it joins on its left, which is the side the walk
    // puts them on and so the only side a coast may pass them.
    //
    // The clamp cannot tell this for itself. It slides each end towards that cell's own
    // middle, and the two outer tangents sit the same distance either side of that middle, so
    // it settles on whichever one it happened to drift towards. The wrong one clears every
    // cell perfectly well - it is a tangent - and is still wrong: it leaves a cell where the
    // coast ought to arrive and arrives where it ought to leave, so the border between the two
    // sweeps backwards, collapses to a point, and the cell contributes nothing.
    //
    // On a long coast that costs one cell. On a two-cell island it costs both, and an outline
    // of two points is not a shape, so the island vanished off the map entirely.
    private static boolean isKeepingCellsLeft(StraightRun run, EdgeAngles edge) {

        var departure = run.findDeparture(edge);
        var arrival = run.findArrival(edge);

        return isLeftOfRun(departure, arrival, run.findCentre(run.from()))
            && isLeftOfRun(departure, arrival, run.findCentre(run.to()));
    }

    // On the line counts as left: a run that departs straight along a diameter has its own
    // cell's centre exactly on it, and that is the ordinary case rather than a failure.
    private static boolean isLeftOfRun(double[] from, double[] to, double[] point) {

        return (to[0] - from[0]) * (point[1] - from[1])
            - (to[1] - from[1]) * (point[0] - from[0]) >= 0;
    }

    // Each end slid to the nearest place to its own middle that the other end can see, over
    // and over, so two ends that both block settle towards the common tangent instead of one
    // of them winning outright.
    private static EdgeAngles clampEdgeEnds(StraightRun run) {

        var union = run.union();
        var departAngle = run.from().midAngle();
        var arriveAngle = run.to().midAngle();

        for (var pass = 0; pass < CLAMP_PASSES; pass++) {

            arriveAngle = findReachableAngle(
                union, run.to(), findPointAt(union, run.from(), departAngle));
            departAngle = findReachableAngle(
                union, run.from(), findPointAt(union, run.to(), arriveAngle));
        }
        return new EdgeAngles(departAngle, arriveAngle);
    }

    // The common tangent, put on each cell's own frontage. Where the frontage does not reach
    // the tangent point - a third cell covering that direction - this clamp moves it, and the
    // run stops being tangent to anything; that is the case the boundary's own join exists
    // for, and it is why the clamp is named rather than buried.
    private static EdgeAngles findTangentEdge(StraightRun run) {

        var tangent = measureTangentAngle(run);

        return new EdgeAngles(
            clampIntoFrontage(run.from(), tangent), clampIntoFrontage(run.to(), tangent));
    }

    // Equal reaches, so the outer tangent runs parallel to the line joining the two sites and
    // meets both circles square to it - one angle, the same on each.
    private static double measureTangentAngle(StraightRun run) {

        var fromCentre = run.findCentre(run.from());
        var toCentre = run.findCentre(run.to());

        return Math.atan2(
            -(toCentre[0] - fromCentre[0]),
            toCentre[1] - fromCentre[1]);
    }

    private static double clampIntoFrontage(DiscUnionBoundary.CoastMark mark, double angle) {

        return Angles.clampInto(
            Angles.placeAfter(angle, mark.fromAngle()), mark.fromAngle(), mark.toAngle());
    }

    // The place on one cell's frontage nearest its middle that a straight line from somewhere
    // else can touch without cutting through the cell. Seen from a point, the reachable part
    // of a circle is the arc facing it, half a turn wide less acos(reach / distance) - there
    // is nothing to search for. Narrowed again to the frontage itself, because a coast has no
    // business on the part of a cell's border that faces another cell rather than the void.
    private static double findReachableAngle(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double[] viewer) {

        var centre = union.sites().get(mark.circle());
        var distance = Points.computeDistance(centre, viewer);

        // A viewer inside the cell can reach no part of its border at all, so there is no
        // window to clamp into. The middle is the least wrong answer, and the clearance check
        // is what reports that this one could not be satisfied.
        if (distance <= union.reach()) {
            return mark.midAngle();
        }

        var facing = Angles.placeAfter(
            Math.atan2(viewer[1] - centre[1], viewer[0] - centre[0]),
            mark.fromAngle());

        var reachable = Math.acos(union.reach() / distance);

        return Angles.clampInto(
            mark.midAngle(),
            Math.max(mark.fromAngle(), facing - reachable),
            Math.min(mark.toAngle(), facing + reachable));
    }

    private static double[] findMidpoint(DiscUnion union, DiscUnionBoundary.CoastMark mark) {
        return findPointAt(union, mark, mark.midAngle());
    }

    private static double[] findPointAt(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double angle) {

        return DiscUnionBoundary.findPointOnCircle(
            union.sites().get(mark.circle()), union.reach(), angle);
    }

    /**
     * Where one straight reach of coast leaves one cell and where it lands on the next.
     *
     * @param departAngle where on the near cell's border it leaves
     * @param arriveAngle where on the far cell's border it lands
     */
    private record EdgeAngles(
        double departAngle,
        double arriveAngle) {
    }

    /**
     * One straight reach of coast still being decided - the two stretches it joins, and the
     * discs it is drawn against.
     *
     * <p>Everything that works out where a reach should go needs all three, so seven
     * signatures carried the same trio and four of them opened by turning it into the same
     * pair of points. The trio is not three arguments that happen to travel together: the
     * marks name circles by INDEX, so they mean nothing except against the union they were
     * indexed in, and a caller free to pair one construction's marks with another's discs can
     * do it and still compile.
     *
     * <p>Deliberately not the answer, only the question. Where the reach ends up is an
     * {@link EdgeAngles}, worked out several ways and chosen between, so it is passed in
     * rather than held - one run, several candidate edges.
     *
     * @param union the discs the reach is drawn against
     * @param from  the stretch it leaves
     * @param to    the stretch it lands on
     */
    private record StraightRun(
        DiscUnion union,
        DiscUnionBoundary.CoastMark from,
        DiscUnionBoundary.CoastMark to) {

        double[] findDeparture(EdgeAngles edge) {
            return findPointAt(union, from, edge.departAngle());
        }

        double[] findArrival(EdgeAngles edge) {
            return findPointAt(union, to, edge.arriveAngle());
        }

        double[] findCentre(DiscUnionBoundary.CoastMark mark) {
            return union.sites().get(mark.circle());
        }

        /**
         * How far this reach goes inside one cell, at one candidate edge.
         *
         * @param edge   where the reach leaves and lands
         * @param circle whose cell to measure against
         * @return how far past that cell's border it goes, negative when it stays outside
         */
        double measureIncursion(EdgeAngles edge, int circle) {

            return union.measureIncursionInto(findDeparture(edge), findArrival(edge), circle);
        }

        /**
         * Whether a cell is one of the two this reach runs between.
         *
         * @param circle whose cell to ask about
         * @return true when the reach starts or ends on it
         */
        boolean isRunBetween(int circle) {
            return circle == from.circle() || circle == to.circle();
        }
    }
}
