package kmu.maplayers.base.profiling;

import java.util.function.DoubleSupplier;

/**
 * What a beat of a map frame is allowed, as a value bound from outside rather than a setting read
 * here.
 *
 * <p>Inverted for one reason: the framework must not be able to see how much of it is being
 * measured. The knob that decides that sits in the same settings section as this bound, so a
 * framework file naming the class that holds one would have the other within reach - and a
 * framework able to read whether it is watched can act on it, which makes the measurement worth
 * less than the reading cost. Bound this way, no file under the framework names a settings class at
 * all, and the bound arrives as a plain number that says nothing about what else is in that
 * section.
 *
 * <p>Unbound is no bound at all rather than a number restated here. What the shipped default is
 * belongs to the settings row and to the accessor that answers with it; a copy kept here would be a
 * second place to correct, and one that could disagree with the row while looking authoritative.
 * Nothing is lost by it: a bound is only ever consulted while a capture is running, and a capture
 * is bound by the same composition root that binds this.
 */
public final class MapFrameBudgets {

    // Zero is the library's "judge nothing", which is also what the player's own row means by 0.
    private static final double NO_BOUND_MILLIS = 0d;

    private static DoubleSupplier frameBeatBudgetMillis = () -> NO_BOUND_MILLIS;

    private MapFrameBudgets() {
    }

    /**
     * Binds where the frame-beat bound is read from, for a composition root that has a settings
     * file to read it out of.
     *
     * <p>A supplier rather than a number, because the player moves this while the game runs: a
     * bound taken once at load would hold the rest of the session to what it said then.
     *
     * @param budgetMillis how long one beat may take, asked as each beat ends
     */
    public static void registerFrameBeatBudget(DoubleSupplier budgetMillis) {
        frameBeatBudgetMillis = budgetMillis;
    }

    /**
     * @return how long one beat of a map frame may take before a running capture reports it, in
     *         milliseconds, or zero for no bound - which is what an unbound holder answers
     */
    public static double resolveFrameBeatBudgetMillis() {
        return frameBeatBudgetMillis.getAsDouble();
    }
}
