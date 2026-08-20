package kmu.maplayers.base.geometry;

import java.util.List;
import java.util.Locale;

/**
 * What the walk did with the walls it was offered, whatever kind they are.
 *
 * <p>A wall that is drawn and a wall that is LAID are different things, and the gap between
 * them is where a fill goes missing: the line is on screen, the trace never attached it, and
 * the void behind it is open to everything around it. The same three refusals reach a bridge
 * and a reach of coast, so the census is written once and asked of each.
 *
 * <p>Asked at BOTH reaches, because a wall can be laid at one and refused at the other - and a
 * wall refused at the reach a shape is drawn at is a pocket the reader never sees, however
 * well the void was traced at its own extent.
 */
final class WallRefusals {

    private WallRefusals() {
    }

    /**
     * One walk over the offered walls, sorted into the reasons the walk had for each.
     *
     * <p>Asked of the verdict rather than of its wording, so a rule that renames a refusal
     * cannot quietly move a count into the wrong column.
     *
     * @param union   the discs the walls are laid across
     * @param walls   the walls on offer, in the order they are offered
     * @param offered the ones to count, which must be among them
     * @return the counts, as one line
     */
    static String summariseRefusals(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> offered) {

        var laid = 0;
        var offBoundary = 0;
        var crowded = 0;
        var noMouth = 0;

        for (var chord : offered) {

            switch (DiscUnionBoundary.describeChordRefusal(union, walls, chord).reason()) {
                case LAID -> laid++;
                case OFF_BOUNDARY -> offBoundary++;
                case CROWDED_OUT -> crowded++;
                case NO_MOUTH -> noMouth++;
                default -> { }
            }
        }
        return String.format(
            Locale.ROOT,
            "%d laid, %d off the boundary, %d crowded out, %d with no mouth",
            laid,
            offBoundary,
            crowded,
            noMouth);
    }

    /**
     * Names every wall the walk turned down, one line each, with where it runs.
     *
     * <p>A count says how many; the void left open by one of them is found by knowing WHICH,
     * and there are few enough of these to name them all.
     *
     * @param union   the discs the walls are laid across
     * @param walls   the walls on offer, in the order they are offered
     * @param offered the ones to report on, which must be among them
     * @param kind    what to call one in the report, since a bridge and a reach of coast read
     *                as different things to anyone looking for the one being described
     */
    static void reportEachRefusal(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> offered,
            String kind) {

        for (var chord : offered) {

            var refusal = DiscUnionBoundary.describeChordRefusal(union, walls, chord);

            if (refusal.reason() == DiscUnionBoundary.RefusalReason.LAID) {
                continue;
            }
            var line = chord.line();

            System.out.printf(
                Locale.ROOT,
                "  %s %d-%d from %.0f,%.0f to %.0f,%.0f: %s%n",
                kind,
                chord.fromCircle(),
                chord.toCircle(),
                line.originX(),
                line.originY(),
                line.originX() + line.directionX(),
                line.originY() + line.directionY(),
                refusal);
        }
    }
}
