package kmu;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

/**
 * Running one step of the mod's start-up wiring behind its own failure boundary.
 *
 * <p>A collaborator that throws costs its own registration and nothing else: the load carries on
 * and every later step still runs. That isolation is the whole reason the wiring is a list of
 * steps rather than a sequence of calls - a mod whose first failing install aborted the rest would
 * come back with a half-wired sector and no indication of which piece went missing.
 *
 * <p>Held apart from the entry point so the installers the entry point calls can guard their own
 * steps at the same granularity. A step guarded once per installer instead would put every
 * registration in that installer behind one failure, which is the arrangement this exists to
 * prevent - and an installer that guards each of its steps cannot itself throw, so the entry point
 * calls it directly rather than wrapping a guard around a guard.
 *
 * <p>Catches what KMLib's {@code kmlib.starsector.startup.WiringSteps} catches, a failure to link
 * alongside a throw, for the reasons given there.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class KmuWiringSteps {

    private static final Logger LOG = Global.getLogger(KmuWiringSteps.class);

    private KmuWiringSteps() {
        // utility class, no instances.
    }

    /**
     * Runs one wiring step, logging rather than propagating whatever it throws.
     *
     * <p>The step comes before the message it fails with, so a reader of a wiring list meets what
     * each entry does before what it says when that does not happen - and so the message sits
     * beside the catch it is only ever read from.
     *
     * @param wiringStep     the registration to attempt
     * @param failureMessage what the log says when it throws, naming the piece that went missing
     */
    public static void runGuardedStep(Runnable wiringStep, String failureMessage) {

        try {
            wiringStep.run();

        } catch (LinkageError | RuntimeException exception) {
            LOG.error(failureMessage, exception);
        }
    }
}
