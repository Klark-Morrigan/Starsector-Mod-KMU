package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Limits;

import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The selection stage of a coast: which stretches of border the line visits.
 *
 * <p>The ones that show enough of themselves to the void, plus the ones dropping would have put
 * a cell across the jump. The two halves are in that order for a reason. The first is a matter
 * of taste and reads nothing but the stretch itself; the second is a matter of correctness and
 * can only be asked once there is a set of drops to test. So a rule of taste may propose any
 * drop it likes, and the repair passes are what stop a proposal stranding the line inside a
 * cell.
 *
 * <p>Held over one union, one set of bridged cells and one set of rules, because every stretch
 * of a trace is selected against the same three.
 */
final class StretchSelection {

    // A skip put back changes the jump either side of it, which can expose a different cell.
    // Bounded rather than run to a fixed point, because each pass keeps strictly more cells
    // and the worst case - every cell visited - is the unsmoothed coast rather than a wrong one.
    private static final int REPAIR_PASSES = 4;

    // No stretch stands between two visited ones, or none of those that do is in anything's way.
    private static final int NOTHING_BLOCKING = -1;

    private final DiscUnion union;
    private final Set<Integer> bridged;
    private final CoastPlacement.BorderRules rules;

    /**
     * @param union   the discs the stretches were read off
     * @param bridged the cells a laid wall attaches to, which selection never drops
     * @param rules   what selection drops, and which turn the clearance clamp reads
     */
    StretchSelection(DiscUnion union, Set<Integer> bridged, CoastPlacement.BorderRules rules) {

        this.union = union;
        this.bridged = bridged;
        this.rules = rules;
    }

    /**
     * Which stretches of one run of coast the border visits.
     *
     * @param coast one run of coast marks, in walk order
     * @return the visited stretches, as positions along that walk
     */
    List<Integer> selectVisitedStretches(List<CoastMark> coast) {

        var isVisited = flagExposedStretches(coast);

        for (var pass = 0; pass < REPAIR_PASSES; pass++) {

            if (!restoreBlockingStretches(coast, isVisited)) {
                break;
            }
        }

        return collectVisitedPositions(isVisited);
    }

    // The visited stretches as positions along the walk, which is what placement indexes by.
    // The flags are how selection decides; the positions are how its answer is read.
    private static List<Integer> collectVisitedPositions(boolean[] isVisited) {

        var visited = new ArrayList<Integer>();

        for (var index = 0; index < isVisited.length; index++) {

            if (isVisited[index]) {
                visited.add(index);
            }
        }

        return visited;
    }

    // Selection's first half: one pass along the coast, visiting every stretch that faces the
    // void with enough of its own border to be worth drawing.
    //
    // Order-free, and that is the point of it: each stretch is judged against nothing but
    // itself, so the walk could start anywhere and drop the same set. Nothing accumulates
    // across the loop, which is why there is no state here to get wrong.
    private boolean[] flagExposedStretches(List<CoastMark> coast) {

        var isVisited = new boolean[coast.size()];

        for (var index = 0; index < coast.size(); index++) {
            isVisited[index] = !isBarelyFacingTheVoid(coast.get(index));
        }

        return isVisited;
    }

    // Whether a stretch offers too little of its cell's border to be worth passing through.
    //
    // A cell that peeks out by a few degrees contributes a notch the width of a rounding
    // error, and the coast is drawn all the way in and back out for it. Dropping it is what
    // the caller asked for by setting the rule above zero.
    //
    // A cell a bridge attaches to is exempt: a bridge's wall is boundary the coast has to
    // stay OUTSIDE of, so cutting the corner across one puts the coast on the wrong side of
    // a shape already drawn. That is a matter of correctness rather than of taste, and no
    // selection knob may overrule it.
    //
    // Nothing here can strand the coast inside a cell. A stretch dropped from this pass is
    // put straight back by the repair pass if the jump over it turns out to cross anything -
    // so the rule can only ever remove detail that was not load-bearing.
    private boolean isBarelyFacingTheVoid(CoastMark mark) {

        return mark.measureShareOfCircle() < rules.minFrontageShare()
            && !bridged.contains(mark.circle());
    }

    // Selection's second half: puts back any stretch a jump turned out to cross. A jump is
    // tested against every cell, not only the two it runs between, because the cell in the way
    // is by definition one neither end knows about - and if that cell is one of the stretches
    // skipped over, visiting it is what stops the jump being made at all.
    private boolean restoreBlockingStretches(List<CoastMark> coast, boolean[] isVisited) {

        var visited = collectVisitedPositions(isVisited);
        if (visited.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return false;
        }

        var restored = false;

        for (var step = 0; step < visited.size(); step++) {

            var from = visited.get(step);
            var to = visited.get((step + 1) % visited.size());
            var blocker = findBlockedStretch(coast, isVisited, from, to);

            if (blocker >= 0) {

                isVisited[blocker] = true;
                restored = true;
            }
        }

        return restored;
    }

    // Which of the stretches skipped between two visited ones to put back, or NOTHING_BLOCKING
    // when the jump is clear.
    //
    // The jump is judged first, by the one clearance test placement itself uses, and only a
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
    private int findBlockedStretch(
            List<CoastMark> coast,
            boolean[] isVisited,
            int from,
            int to) {

        var jump = new StraightRuns.StraightRun(union, coast.get(from), coast.get(to));
        var edge = StraightRuns.resolveEdge(jump, rules.reachAnchor());

        if (StraightRuns.isRunClearOfEveryCell(jump, edge)) {
            return NOTHING_BLOCKING;
        }

        var firstSkipped = NOTHING_BLOCKING;

        for (var step = 1;
                step < coast.size() && !isVisited[(from + step) % coast.size()];
                step++) {

            var index = (from + step) % coast.size();

            if (firstSkipped < 0) {
                firstSkipped = index;
            }

            if (jump.isRunBetween(coast.get(index).circle())) {
                continue;
            }

            if (jump.measureIncursion(edge, coast.get(index).circle()) > DiscUnion.TOUCHING_TOLERANCE) {
                return index;
            }
        }

        return firstSkipped;
    }
}
