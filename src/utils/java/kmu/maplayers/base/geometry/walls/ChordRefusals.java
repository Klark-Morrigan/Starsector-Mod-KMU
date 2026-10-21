package kmu.maplayers.base.geometry.walls;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Why each offered wall was or was not laid, as the rule that decided it.
 *
 * <p>{@link WallLaying#findAttachableChords} answers WHICH walls were laid, which is what the
 * walk needs and nothing at all to someone looking at a gap on screen and asking why the line
 * across it holds nothing back. The refusals are not interchangeable: a wall whose end is
 * covered has cells that met without it, and one crowded out is competing with a neighbour -
 * the first is geometry, the second is ordering.
 *
 * <p>Built from one run of the SAME laying the walk does, so what is explained here is what
 * actually happened rather than a second opinion about it. One run answers every wall: whether
 * a mouth was already taken depends on every wall offered before it, so asking about each wall
 * with a laying of its own repeats the whole laying once per wall.
 *
 * <p>The wording is worked out only for a wall someone asks about. Naming the cell that covered
 * a wall's end is a scan over the discs, and a report asks about a handful of walls.
 */
public final class ChordRefusals {

    private final DiscUnion union;

    // Keyed by the wall itself rather than by its value: the laying answers about the walls it
    // was handed, so a fresh build of the same line is a wall no verdict can be about.
    private final Map<Chord, WallLaying.ChordVerdict> verdictByWall;

    private ChordRefusals(DiscUnion union, Map<Chord, WallLaying.ChordVerdict> verdictByWall) {

        this.union = union;
        this.verdictByWall = verdictByWall;
    }

    /**
     * Lays the offered walls once and keeps what became of each.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer, in the order they are offered
     * @return every wall's verdict, ready to be asked about any of them
     */
    public static ChordRefusals judgeEveryRefusal(DiscUnion union, Walls walls) {

        var verdictByWall = new IdentityHashMap<Chord, WallLaying.ChordVerdict>();

        for (var verdict : WallLaying.judgeEveryOfferedChord(union, walls)) {
            verdictByWall.put(verdict.chord(), verdict);
        }

        return new ChordRefusals(union, verdictByWall);
    }

    /**
     * What became of one wall, with its explanation attached.
     *
     * @param wall the one to ask about
     * @return what became of it; {@link RefusalReason#NOT_OFFERED} where it was not among the
     *         walls laid
     */
    public ChordRefusal describeRefusal(Chord wall) {

        var verdict = verdictByWall.get(wall);

        if (verdict == null) {
            return new ChordRefusal(RefusalReason.NOT_OFFERED, null);
        }

        return switch (verdict.reason()) {

            case OFF_BOUNDARY -> describeCoveredEnd(verdict);

            case CROWDED_OUT -> new ChordRefusal(
                RefusalReason.CROWDED_OUT,
                nameHolder(verdict.holder()));

            default -> new ChordRefusal(verdict.reason(), null);
        };
    }

    // The rival named by its own cells, so a crowding refusal points at it rather than leaving
    // it to be hunted for.
    private static String nameHolder(WallLaying.MouthHolder holder) {

        var wall = holder.wall();

        return wall.kind()
            + " "
            + wall.fromCircle()
            + "-" + wall.toCircle()
            + " on cell "
            + holder.circle();
    }

    // Which end failed is re-tested rather than remembered by the laying: it is two boundary
    // tests on one wall, against a scan per wall on every trace to carry it.
    private ChordRefusal describeCoveredEnd(WallLaying.ChordVerdict verdict) {

        var chord = verdict.chord();
        var mouthed = verdict.mouthed();

        var covered = WallLaying.isWallOnBoundary(
                union,
                chord,
                chord.fromCircle(),
                mouthed.fromMouth())
            ? chord.toCircle()
            : chord.fromCircle();

        return new ChordRefusal(
            RefusalReason.OFF_BOUNDARY,
            describeCover(
                chord,
                covered,
                covered == chord.fromCircle()
                    ? mouthed.fromMouth()
                    : mouthed.toMouth()));
    }

    // Which disc covers one of a wall's landings, and by how far it reaches over it.
    //
    // The depth is the whole of the diagnosis. A landing a whole cell deep inside a neighbour
    // is geometry - the cells met without the wall, and refusing it is right. A landing under
    // it by a rounding error is a wall the map draws and the trace throws away, and the two
    // read as the same verdict without a number beside them.
    private String describeCover(Chord chord, int circle, double[] mouth) {

        double[] deepest = null;

        for (var point : WallLaying.collectBoundaryTests(union, chord, circle, mouth)) {

            var cover = WallLaying.findDeepestCover(union, chord, circle, point);
            if (cover != null && (deepest == null || cover[1] > deepest[1])) {
                deepest = cover;
            }
        }

        return deepest == null
            ? "on cell " + circle
            : String.format(
                Locale.ROOT,
                "on cell %d, %.3f inside cell %d",
                circle,
                deepest[1],
                (int) deepest[0]);
    }
}
