package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a piece of void is called, in the namespace the cells are keyed by.
 *
 * <p>A piece has to be keyed exactly as a cell is, because that is what makes it one: the
 * cluster trace keys its members and its coincident neighbours by cell ID, and an edge names
 * what lies across it by the same id. So a piece's name is a {@code String} in the system-id
 * namespace, held apart from a real star's only by a prefix nothing else uses. Which prefix is
 * the caller's to say - it names the kind of piece, and the kinds are each construction's own.
 *
 * <p>Named from the CELLS AROUND IT rather than from anything about the walk that produced it.
 * A walk hands the pieces back in whatever order it met them, and that order moves with a knob,
 * a fixture edit, or a tie in a sort - so a name taken from it would rename half the map for a
 * reason nobody could see. The cells around a piece of void are what that piece of void IS: a
 * name anchored to them is untouched by a politics refresh, and changes exactly when the cells
 * bounding it change, which is when the region genuinely differs.
 *
 * <p><b>The cells alone are not quite enough.</b> Two distinct pieces of void can run on the
 * same pair of cells - one on each side of the line between them - and across the two fixtures
 * that happens 5 and 7 times, always on a two-cell ring. So the name carries a side as well:
 * which side of the line joining the two CLOSEST of its cells the piece lies on. Closest,
 * because that pair is the narrowest crossing among them and so the place a pocket is pinched
 * or cut, which is the one line that has void on both sides of it. That settles every collision
 * on both fixtures; the side of a wall settles only the ones that have a wall in common, which
 * is 3 of 5 and 5 of 7.
 *
 * <p>The pair is put in system-id order before the side is read, so which side is which is a
 * property of the two cells and not of what order the sector happened to load them in.
 *
 * <h2>What the key is allowed to contain</h2>
 *
 * <p>A star's own ID is generated rather than written by hand - vanilla procgen makes it
 * {@code "system_" + genUID()} - so the IDs this is built out of are word characters and
 * nothing else. A piece's key is held to the same shape: lowercase, digits and underscore,
 * with the parts joined by a hyphen. Nothing parses these keys, so the point is not that they
 * can be taken apart again; it is that a key travels into maps, logs, reports and settings, and
 * one carrying a space or a punctuation mark is a key that eventually meets something that
 * splits on it.
 *
 * <p>So every part is put through {@link #reduceToKeyCharacters} on the way in, and the joiner
 * is a character that survives nothing: a hyphen cannot come out of that reduction, so it can
 * only ever be a joiner and two different cell sets cannot produce one key between them.
 *
 * <p>That reduction can in principle fold two distinct IDs together - two systems whose IDs
 * differ only in punctuation - which would name two pieces of void alike. It is not guarded
 * against here because it cannot be fixed here: a report counting the distinct keys against the
 * pieces is where such a fold would show.
 *
 * <p><b>The fixture's IDs are not what production will hand this.</b> Its first column is the
 * system's NAME, spaces and all, because a failure reading "Askonia" beats one reading an
 * opaque handle - so the keys this produces over a fixture are longer and more mangled than the
 * ones it will produce over a sector. That is the fixture's doing, not the scheme's.
 */
public final class VoidKeys {

    // Doubled, so the joiner stands out from the underscores INSIDE a part. A single hyphen
    // would already be unambiguous - one cannot survive the reduction below - but a key is read
    // by eye far more often than it is compared, and at a glance "a-b-c" does not show where
    // one system ID ends and the next begins.
    private static final String PART_JOINER = "--";

    private static final String LEFT = "l";

    private static final String RIGHT = "r";

    // What stands in for the side when a piece runs on a single cell, which leaves no pair to
    // take a side of. Marked rather than left off, so a name that could not be told apart from
    // another's reads as such instead of looking settled.
    private static final String NO_PAIR = "x";

    // Two cells is what it takes to have a line between them.
    private static final int PAIR = 2;

    // Everything a generated system ID is made of. Anything else in a part is a run of one or
    // more characters that came out of a display name rather than out of an id.
    private static final String KEY_CHARACTERS = "[^a-z0-9_]+";

    private static final String REPLACEMENT = "_";

    private VoidKeys() {
    }

    /**
     * Names one piece of void.
     *
     * @param prefix         what marks the key as this kind of piece rather than a star: in key
     *                       characters already, since it is a constant of the caller's
     * @param cells          the cells the piece runs on, in any order
     * @param outline        its closed boundary, whose middle says which side of its narrowest
     *                       crossing it lies on
     * @param sites          the sites, to find which two of its cells sit closest together
     * @param systemIdBySite each site's system ID, index-aligned with {@code sites}
     * @return its key, in the system-id namespace
     */
    public static String buildKey(
            String prefix,
            List<Integer> cells,
            List<double[]> outline,
            List<double[]> sites,
            List<String> systemIdBySite) {

        var parts = new ArrayList<String>(cells.size() + 1);

        for (var cell : cells) {
            parts.add(reduceToKeyCharacters(systemIdBySite.get(cell)));
        }

        // Sorted after the reduction rather than before it, so the order of the parts is the
        // order they read in - two keys listing the same cells cannot come out ordered
        // differently because their original IDs sorted another way.
        parts.sort(String::compareTo);
        parts.add(readSideOfClosestPair(cells, outline, sites, systemIdBySite));

        return prefix + PART_JOINER + String.join(PART_JOINER, parts);
    }

    /**
     * One part of a key, cut down to what a generated system ID is made of.
     *
     * <p>Lowercased and with every run of anything else replaced by a single underscore, so a
     * display name arrives looking like an ID instead of like prose.
     *
     * @param part the ID to reduce
     * @return it in key characters only
     */
    public static String reduceToKeyCharacters(String part) {
        return part.toLowerCase(Locale.ROOT).replaceAll(KEY_CHARACTERS, REPLACEMENT);
    }

    // Which side of its own narrowest crossing the piece lies on, read at its middle. The
    // middle rather than any one point of the outline: the outline runs to both ends of that
    // crossing, so a point taken off it can sit on the line itself.
    private static String readSideOfClosestPair(
            List<Integer> cells,
            List<double[]> outline,
            List<double[]> sites,
            List<String> systemIdBySite) {

        if (cells.size() < PAIR) {
            return NO_PAIR;
        }
        var pair = findClosestPair(cells, sites, systemIdBySite);

        var from = sites.get(pair[0]);
        var to = sites.get(pair[1]);
        var middle = Points.computeMean(outline);

        var across = (to[0] - from[0]) * (middle[1] - from[1])
            - (to[1] - from[1]) * (middle[0] - from[0]);

        return across >= 0 ? LEFT : RIGHT;
    }

    // The two of a piece's cells whose sites sit closest together, in system-id order.
    //
    // Closest sites IS the narrowest crossing: every cell reaches the same distance, so the
    // void between two of them is their separation less two reaches, and the pair that
    // minimises the one minimises the other. Taken from the sites rather than from the
    // crossing so that it is answerable for a pair whose discs already overlap, which has no
    // crossing at all and is exactly the case a two-cell ring is.
    private static int[] findClosestPair(
            List<Integer> cells,
            List<double[]> sites,
            List<String> systemIdBySite) {

        var closest = new int[] {cells.get(0), cells.get(1)};
        var least = Double.MAX_VALUE;

        for (var first = 0; first < cells.size(); first++) {
            for (var second = first + 1; second < cells.size(); second++) {

                var ordered = orderBySystemId(
                    cells.get(first), cells.get(second), systemIdBySite);

                var apart = Points.computeDistance(
                    sites.get(ordered[0]), sites.get(ordered[1]));

                // Ties broken by the pair's own names, so two equally close pairs always
                // hand back the same one however the cells were listed.
                if (apart < least || (apart == least && isNamedBefore(
                        ordered, closest, systemIdBySite))) {

                    least = apart;
                    closest = ordered;
                }
            }
        }
        return closest;
    }

    private static int[] orderBySystemId(int first, int second, List<String> systemIdBySite) {

        return systemIdBySite.get(first).compareTo(systemIdBySite.get(second)) <= 0
            ? new int[] {first, second}
            : new int[] {second, first};
    }

    private static boolean isNamedBefore(
            int[] candidate,
            int[] standing,
            List<String> systemIdBySite) {

        var byFirst = systemIdBySite.get(candidate[0])
            .compareTo(systemIdBySite.get(standing[0]));

        return byFirst != 0
            ? byFirst < 0
            : systemIdBySite.get(candidate[1])
                .compareTo(systemIdBySite.get(standing[1])) < 0;
    }
}
