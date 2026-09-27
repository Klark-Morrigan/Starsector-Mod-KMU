package kmu;

import com.fs.starfarer.api.Global;

import kmlib.starsector.compatibility.ModIntegration;
import kmlib.starsector.startup.WiringSteps;

import java.util.function.Supplier;

/**
 * Running one step of the mod's start-up wiring behind its own failure boundary, which is KMLib's
 * {@link WiringSteps} logging as KMU.
 *
 * <p>What the boundary catches, why, and what a step that failed costs are stated there and held
 * there. This is the entry point KMU's installers name: statically imported by every one of them,
 * so a step reads as one call rather than as a guard fetched and then asked.
 *
 * <p>Held apart from the mod plugin so the installers it calls can guard their own steps at the same
 * granularity. A step guarded once per installer instead would put every registration in that
 * installer behind one failure, which is the arrangement the boundary exists to prevent - and an
 * installer that guards each of its steps cannot itself throw, so the mod plugin calls it directly
 * rather than wrapping a guard around a guard.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class KmuWiringSteps {

    // One guard for the whole mod, logging under this class so a step that failed is logged inside
    // the subtree KMU's log-level setting reaches.
    private static final WiringSteps WIRING_STEPS = new WiringSteps(Global.getLogger(KmuWiringSteps.class));

    private KmuWiringSteps() {
        // utility class, no instances.
    }

    /**
     * Runs one wiring step that binds to nothing a player could act on, logging rather than
     * propagating whatever it throws.
     *
     * @param wiringStep     the registration to attempt
     * @param failureMessage what the log says when it throws, naming the piece that went missing
     */
    public static void runGuardedStep(Runnable wiringStep, String failureMessage) {
        WIRING_STEPS.runGuardedStep(wiringStep, failureMessage);
    }

    /**
     * Runs one wiring step that binds to a third-party mod, logging whatever it throws and telling
     * the player once which mod it was and what KMU lost by it.
     *
     * @param wiringStep          the registration to attempt
     * @param failureMessage      what the log says when it throws
     * @param describeIntegration which mod the step binds to and what KMU loses without it, composed
     *                            only where the step failed
     */
    public static void runGuardedStep(
            Runnable wiringStep,
            String failureMessage,
            Supplier<ModIntegration> describeIntegration) {

        WIRING_STEPS.runGuardedStep(wiringStep, failureMessage, describeIntegration);
    }
}
