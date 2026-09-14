package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.CornerRounding;
import kmlib.math.geometry.PolygonSmoothing;

import java.util.List;

/**
 * The last thing done to a coast before it is drawn: the tips taken off its sharp joins.
 *
 * <p><b>Cosmetic, and structurally so.</b> A traced coast is a border - where settled space
 * ends - and everything deciding anything against one reads that border. This makes a second
 * line from it for the eye, moving each sharp corner in by at most the rounding radius, and
 * nothing on the map is measured, placed or judged against what comes out. Set the radius to
 * nothing and the map draws the border itself; every other answer is unchanged.
 *
 * <p><b>Its own class rather than a step of the trace</b>, because that is what keeps the
 * property above true rather than merely intended. A trace that produced a rounded line would
 * hand every reader both, and picking the wrong one costs nothing at the call and is invisible
 * afterwards - which is how geometry comes to be built on presentation. A trace that produces
 * only the border cannot be read that way at all.
 *
 * <p><b>Run once per rebuild, not once per drawing.</b> A whole trace goes through together and
 * the answer is held: a sector's coasts are tens of thousands of points, and a pass repeated per
 * frame is paid for per frame. Holding it also means the paint, the fills and the exported
 * picture are looking at ONE rounded line rather than at three roundings of one border.
 */
public final class CoastRounding {

    private CoastRounding() {
    }

    /**
     * Every line a trace drew, rounded for the map.
     *
     * <p>The three kinds together rather than one call each, because they are drawn together
     * and rounding some of them under one setting and the rest under another would put two
     * treatments of one edge on screen at once.
     *
     * @param coasts       the outer coasts, in the order they were traced
     * @param lakes        the lake shores, in the order they were traced
     * @param walledShores the shores of water a laid wall closed, empty for a coast traced
     *                     without walls
     */
    public record RoundedCoasts(
        List<List<double[]>> coasts,
        List<List<double[]>> lakes,
        List<List<double[]>> walledShores) {

        /** Nothing traced, and so nothing to draw. */
        public static final RoundedCoasts NONE =
            new RoundedCoasts(List.of(), List.of(), List.of());
    }

    /**
     * Rounds every line of a trace for drawing.
     *
     * @param traced   the coast, whose borders are what is rounded
     * @param rounding how sharply a join has to turn to be rounded, and how far the rounding
     *                 steps back along each arm of it
     * @return the same lines with their sharp joins taken off, one ring for one ring and in the
     *         same order, so a caller can pair them with whatever it holds per coast
     */
    public static RoundedCoasts roundTracedCoasts(
            Coastlines.TracedCoasts traced,
            CornerRounding rounding) {

        return new RoundedCoasts(
            roundOutlines(Coastlines.collectCoastOutlines(traced), rounding),
            roundOutlines(Coastlines.collectLakeOutlines(traced), rounding),
            roundOutlines(Coastlines.collectWalledShoreOutlines(traced), rounding));
    }

    // One rounding applied to a whole layer of lines. The rounding is a property of the map
    // rather than of any one shore, so every layer is put through the same call rather than
    // each reaching for the smoothing itself.
    private static List<List<double[]>> roundOutlines(
            List<List<double[]>> outlines,
            CornerRounding rounding) {

        return outlines.stream()
            .map(outline -> PolygonSmoothing.roundCorners(outline, rounding))
            .toList();
    }
}
