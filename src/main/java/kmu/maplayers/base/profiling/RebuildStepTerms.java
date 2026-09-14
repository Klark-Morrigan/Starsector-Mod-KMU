package kmu.maplayers.base.profiling;

import kmlib.profiling.CallLogThreshold;
import kmlib.profiling.SectionTerms;

/**
 * The terms every step of a map rebuild registers its section on.
 *
 * <p>One value rather than a threshold spelled at each step, because the steps share the reason: a
 * rebuild happens when something changed rather than on a clock, so each of its steps is an event
 * a reader following the rebuild through the log wants to see - the fast ones included, a cut that
 * recomputed nothing being an answer in itself. Stated here once, a step that should say more or
 * less than the others changes its own registration rather than this.
 */
public final class RebuildStepTerms {

    /** Every call of the step writes its line, whatever it took. */
    public static final SectionTerms LOGGED_EVERY_CALL =
        SectionTerms.DEFAULT.withCallLogThreshold(CallLogThreshold.LOGGING_EVERY_CALL);

    private RebuildStepTerms() {
    }
}
