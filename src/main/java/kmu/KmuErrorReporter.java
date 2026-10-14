package kmu;

import com.fs.starfarer.api.Global;

/**
 * Where a feature sends a failure it caught and carried on past, so the failure leaves a trace even
 * though the player's action went on.
 */
@FunctionalInterface
public interface KmuErrorReporter {

    /**
     * Records one failure the caller caught and carried on past.
     *
     * @param message what the caller was doing when it failed
     * @param cause   the failure, kept for its trace
     */
    void report(String message, RuntimeException cause);

    /**
     * A reporter writing each failure as a warning under the reporting class's own logger, so the
     * line names where it came from and the level set for that package applies to it.
     *
     * @param reportingClass the class whose caught failures this reporter writes
     * @return the logging reporter
     */
    static KmuErrorReporter createLoggingReporter(Class<?> reportingClass) {

        var log = Global.getLogger(reportingClass);

        return (message, cause) -> log.warn(message, cause);
    }
}
