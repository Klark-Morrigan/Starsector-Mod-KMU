package kmu.maplayers.politicalmap.base.render;

import org.apache.log4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A diagnostic line reported only when what it says changes.
 *
 * <p>The readings behind such a line hold still for long stretches - which widgets the cursor is
 * inside, what order the map will draw its terrain icons in - while the pass that takes them runs
 * every frame. Repeated verbatim they bury the one line that moved, which is the only line worth
 * having.
 *
 * <p>Describing is the expensive half - both readings walk the live widget tree - so it is asked
 * for only once the log is known to be taking it.
 *
 * <p>Holding the last line here rather than in the reader that produced it is what keeps the two
 * apart: a reader describes what it sees and says nothing about how often anyone wants to hear it.
 * The logger is handed in for the same reason, so a line answers to the verbosity of the feature it
 * diagnoses rather than to a category named after this type.
 */
final class ChangedLineTrace {

    // What the line says, asked per report rather than held, the answer being a live read.
    private final Supplier<String> describeLine;

    // The last line reported, or null before any. Compared rather than counted, so a reading that
    // returns to a value it held before is reported again - it moved twice.
    private String lastReportedLine;

    private final Logger log;

    // What the line is about, printed ahead of it so one log holds several of these apart.
    private final String subject;

    /**
     * Refuses a logger it was handed as null, rather than holding one and failing at the first line
     * it is asked for.
     *
     * <p>Not defensive noise: a holder of one of these is typically a static field, initialised
     * while its own class still is, so a logger declared below it in the same class arrives here as
     * null and nothing says so. Held, that surfaces as a null dereference on a later frame, inside a
     * render pass, several classes away from the declaration order that caused it. Refused, it
     * surfaces where it was made - at load, naming this constructor.
     */
    ChangedLineTrace(Logger log, String subject, Supplier<String> describeLine) {
        this.log = Objects.requireNonNull(log, "A trace with no logger could report nothing.");
        this.subject = subject;
        this.describeLine = describeLine;
    }

    /**
     * Reports this line, unless the log is above DEBUG, the reading has no line to give, or the line
     * says what it said last.
     */
    void traceWhenChanged() {

        if (!log.isDebugEnabled()) {
            return;
        }
        var line = describeLine.get();

        if (line == null || line.equals(lastReportedLine)) {
            return;
        }
        lastReportedLine = line;
        log.debug(subject + ": " + line);
    }
}
