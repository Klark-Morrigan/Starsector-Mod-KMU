package kmu.maplayers.base.geometry.v3;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a click on the map is written down: what was under the pointer, and where.
 *
 * <p>A shape on screen raises questions a shape cannot answer - whether a black patch is void
 * nothing drew or a fill in a colour close to the backdrop, whether two pockets are one shape
 * or two. Reading a pixel settles those, and a file settles them for a reader who is not
 * sitting at the window: a coordinate and a colour can be pasted into a report, diffed between
 * two runs, or handed to whatever is being asked about that spot.
 *
 * <p>Emptied when the viewer opens. A session's picks are notes about the shapes on screen in
 * that session, and a file that accumulated across runs would put yesterday's answers beside
 * today's with nothing to tell them apart.
 */
public final class PickLog {

    private static final Path PICK_FILE =
        Path.of("build", "reports", "political-map", "viewer-picks.txt");

    private PickLog() {
    }

    /**
     * Opens the log for a new session, emptying whatever the last one left.
     *
     * @return the log
     */
    public static PickLog startPickLog() {

        try {
            Files.createDirectories(PICK_FILE.getParent());
            Files.writeString(PICK_FILE, "");

        } catch (IOException ioException) {

            throw new UncheckedIOException(
                "cannot open " + PICK_FILE.toAbsolutePath(),
                ioException);
        }

        return new PickLog();
    }

    /**
     * Where the picks are written, for a reader that wants to open them rather than add to
     * them.
     *
     * <p>Static because a reader has no log to hold: the window owns the writing, and asking
     * it for the path would mean holding a window open to read a file it wrote.
     *
     * @return the file, which need not exist yet
     */
    public static Path getPickFile() {
        return PICK_FILE;
    }

    /**
     * The picks for one sector, as the viewer wrote them down.
     *
     * <p>Read here, beside the writing, so the line format has one home. A missing file and
     * picks from another sector are both ordinary: the log is emptied per session and holds
     * whichever sectors were looked at.
     *
     * @param sectorName which fixture the picks were taken on
     * @return each pick as {x, y}, in the order they were taken; empty where there are none
     */
    public static List<double[]> readPicks(String sectorName) {

        var picks = new ArrayList<double[]>();

        if (!Files.exists(PICK_FILE)) {
            return picks;
        }

        try {
            for (var line : Files.readAllLines(PICK_FILE)) {

                if (!line.startsWith(sectorName)) {
                    continue;
                }

                var fields = line.substring(sectorName.length()).trim().split("[,\\s]+");

                picks.add(new double[] {
                    Double.parseDouble(fields[0]),
                    Double.parseDouble(fields[1])});
            }
        } catch (IOException ioException) {

            throw new UncheckedIOException(
                "cannot read " + PICK_FILE.toAbsolutePath(),
                ioException);
        }

        return picks;
    }

    /**
     * Writes down one pick.
     *
     * <p>The sector travels with it because the window can be moved to another one mid
     * session, and a coordinate means something different in each.
     *
     * @param sectorName which fixture was on screen
     * @param worldX     where the pointer was, in the sector's own coordinates
     * @param worldY     the same
     * @param picked     the colour under it
     */
    public void appendPick(String sectorName, double worldX, double worldY, Color picked) {

        var line = String.format(
            Locale.ROOT,
            "%s  %.0f, %.0f  #%02x%02x%02x%n",
            sectorName,
            worldX,
            worldY,
            picked.getRed(),
            picked.getGreen(),
            picked.getBlue());

        try {
            Files.writeString(
                PICK_FILE,
                line,
                StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

        } catch (IOException ioException) {

            throw new UncheckedIOException(
                "cannot append to " + PICK_FILE.toAbsolutePath(),
                ioException);
        }
    }
}
