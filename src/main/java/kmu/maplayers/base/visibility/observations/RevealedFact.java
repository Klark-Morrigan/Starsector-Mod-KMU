package kmu.maplayers.base.visibility.observations;

import kmu.maplayers.base.visibility.observations.ObservationRecency.RecalledObservation;

import java.util.Objects;
import java.util.Optional;

/**
 * A concealed fact together with what it states - where the fact is a value rather than the bare
 * age of somebody's news.
 *
 * <p>{@link ObservationRecency} answers only how old the news is. Where the concealed thing is a
 * value - who holds a place, who is stationed there - that value has to travel with the state
 * saying whether it may be stated at all. Kept apart, a reader would join a value to a moment
 * nothing had ever joined, and state this morning's holder beside last cycle's date.
 *
 * <p>The two are separate types rather than one carrying a payload nobody reads, because an axis
 * concealing nothing but the age of its own news is common enough to be worth not paying for: it
 * spends the bare state, and a value rides on top only where a family conceals one.
 *
 * <p>A fact nothing has ever established cannot be built holding a value, so a reader is made to
 * decide what to say in its place - the "unknown" wording each family supplies - rather than being
 * handed something plausible that nobody ever saw.
 *
 * @param <T> what the fact states once somebody has observed it
 */
public final class RevealedFact<T> {

    private final ObservationRecency recency;
    private final Optional<T> revealedValue;

    private RevealedFact(ObservationRecency recency, Optional<T> revealedValue) {

        this.recency = recency;
        this.revealedValue = revealedValue;
    }

    /**
     * A fact nothing has ever established: no value, and no moment to date one at.
     *
     * @param <T> what the fact would have stated had anybody observed it
     * @return the unobserved fact
     */
    public static <T> RevealedFact<T> createNeverObservedFact() {
        return new RevealedFact<>(ObservationRecency.NEVER_OBSERVED, Optional.empty());
    }

    /**
     * A fact something is revealing at this moment, stating what is being read off it now. It
     * carries no moment, being due no date.
     *
     * @param <T>           what the fact states
     * @param revealedValue what the live reading says the fact is
     * @return the fact, as it is being observed
     */
    public static <T> RevealedFact<T> createObservedNowFact(T revealedValue) {

        Objects.requireNonNull(revealedValue, "An observed fact states what was observed.");
        return new RevealedFact<>(ObservationRecency.OBSERVED_NOW, Optional.of(revealedValue));
    }

    /**
     * A fact the record holds, stating what was written down and when it was seen.
     *
     * <p>The observation arrives already built rather than as a bare moment, so a recalled state is
     * put together in one place however it is reached.
     *
     * @param <T>                 what the fact states
     * @param recalledObservation the record the value came from, carrying the moment it was made
     * @param revealedValue       what was observed, as the record holds it
     * @return the fact, as it was last seen
     */
    public static <T> RevealedFact<T> createRecalledFact(
            RecalledObservation recalledObservation,
            T revealedValue) {

        Objects.requireNonNull(recalledObservation, "A recalled fact comes from an observation.");
        Objects.requireNonNull(revealedValue, "A recalled fact states what was observed.");
        return new RevealedFact<>(recalledObservation, Optional.of(revealedValue));
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }
        if (!(other instanceof RevealedFact)) {
            return false;
        }
        var that = (RevealedFact<?>) other;

        return recency.equals(that.recency)
            && revealedValue.equals(that.revealedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recency, revealedValue);
    }

    /**
     * How old the news of this fact is, which is what decides whether a date is due beside it.
     *
     * @return the state the fact is in; never null
     */
    public ObservationRecency readRecency() {
        return recency;
    }

    /**
     * What the fact states, where anybody has ever established it.
     *
     * @return what was observed, or empty where nothing ever was - in which case the reader states
     *         its own words for a thing nobody has seen
     */
    public Optional<T> resolveValue() {
        return revealedValue;
    }

    @Override
    public String toString() {
        return "RevealedFact[recency=" + recency + ", revealedValue=" + revealedValue + "]";
    }
}
