package kmu.maplayers.base.visibility.observations;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * How old the news about one concealed fact is: something is revealing it now, the record recalls
 * it from a stated moment, or nothing has ever established it.
 *
 * <p>Three states, and no fourth is possible. Two of them carry nothing at all: a fact being
 * revealed now is due no date - a date beside a thing somebody is looking at is stale by
 * construction - and a fact never observed has no date to be stale, being absent rather than old.
 * Only the recalled state carries a moment, so neither of the other two has a field to get wrong.
 *
 * <p>Sealed rather than stated as one record carrying a flag, because the three differ in what they
 * hold rather than in a setting. One value standing for all of them would have to be asked a second
 * question to learn which it was, and nothing would keep the two answers agreeing.
 *
 * <p>What the fact <em>states</em> rides on top of this rather than inside it, in
 * {@link RevealedFact}: whether a date is due is one question, and what is being dated is another.
 * An axis concealing nothing but the age of its own news spends this alone.
 *
 * <p>Recalled, not stale. Stale says the news is bad; recalled says where it came from, and a
 * recalled fact from this morning is not stale at all.
 *
 * <p>Nothing here reads a clock. The record decides which state a fact is in and the time is spent
 * only on the remark beside it - a state that consulted a clock would take a fact off a surface for
 * having gone quiet, which is the one thing it must never do.
 */
public sealed interface ObservationRecency {

    /**
     * Something is revealing the fact at this moment. It carries no moment of its own, since what
     * is being looked at needs no date beside it.
     */
    ObservationRecency OBSERVED_NOW = new ObservedNow();

    /**
     * Nothing has ever established the fact. It carries neither a value nor a moment: there is
     * nothing to state and nothing to date, which is what makes it different from old news.
     */
    ObservationRecency NEVER_OBSERVED = new NeverObserved();

    /**
     * Which of the three states a fact is in, given what is being read live and what the record
     * holds of it.
     *
     * <p>The one place the order is settled: a live reading beats a record, since the record is at
     * best what that reading would have said earlier, and a record beats nothing. Stated once so
     * two surfaces cannot reach the triad by different routes and then disagree about which of them
     * is due a date.
     *
     * <p>What counts as a live reading, and what put the record there, are the asking family's own
     * business. Both arrive already answered.
     *
     * @param isObservedNow       whether something is revealing the fact at this moment
     * @param recordedObservation what the register holds of the fact, or empty where it holds
     *                            nothing of it
     * @return the state the fact is in; never null
     */
    static ObservationRecency resolveRecency(
            boolean isObservedNow,
            Optional<RecalledObservation> recordedObservation) {

        if (isObservedNow) {
            return OBSERVED_NOW;
        }
        // A record is already the recalled state, so a present one stands as the answer.
        return recordedObservation.isPresent()
            ? recordedObservation.get()
            : NEVER_OBSERVED;
    }

    /**
     * Hands this state to whichever of the three the caller wrote for it, and answers what that one
     * produced.
     *
     * <p>The fold is how the sealed set is actually spent: a caller states all three cases or does
     * not compile, and a fourth case joining the set breaks every caller at once rather than being
     * silently swallowed by whichever branch happened to be last. The language's own exhaustive
     * switch would say the same thing, but only from Java 21 - the mod targets 17, which is the
     * runtime the game supplies.
     *
     * @param <R>               what the caller produces for a state
     * @param observedNowCase   what a fact something is revealing now produces
     * @param recalledCase      what a fact the record holds produces, given the observation it is
     *                          recalled from
     * @param neverObservedCase what a fact nothing has ever established produces
     * @return the answer of the case this state is
     */
    <R> R selectByCase(
        Supplier<R> observedNowCase,
        Function<RecalledObservation, R> recalledCase,
        Supplier<R> neverObservedCase);

    /** The shape behind {@link #OBSERVED_NOW}, carrying nothing beyond being that case. */
    record ObservedNow() implements ObservationRecency {

        @Override
        public <R> R selectByCase(
                Supplier<R> observedNowCase,
                Function<RecalledObservation, R> recalledCase,
                Supplier<R> neverObservedCase) {

            return observedNowCase.get();
        }
    }

    /**
     * The record holds the fact, from a moment it may or may not have written down.
     *
     * <p>The moment is optional because a value recorded before observations were timed carries
     * none, and a state that refused one would fail a load rather than a read. Such a fact reads as
     * seen, with nobody having written down when - recalled all the same, and due no date.
     *
     * @param observedTimestamp when the fact was observed, on the campaign clock's own scale, or
     *                          empty for an observation made before they were timed
     */
    record RecalledObservation(Optional<Long> observedTimestamp) implements ObservationRecency {

        /** Reads an unstated moment as no moment, so an undated record cannot fail late on it. */
        public RecalledObservation {
            observedTimestamp = observedTimestamp == null ? Optional.empty() : observedTimestamp;
        }

        @Override
        public <R> R selectByCase(
                Supplier<R> observedNowCase,
                Function<RecalledObservation, R> recalledCase,
                Supplier<R> neverObservedCase) {

            return recalledCase.apply(this);
        }
    }

    /** The shape behind {@link #NEVER_OBSERVED}, carrying nothing beyond being that case. */
    record NeverObserved() implements ObservationRecency {

        @Override
        public <R> R selectByCase(
                Supplier<R> observedNowCase,
                Function<RecalledObservation, R> recalledCase,
                Supplier<R> neverObservedCase) {

            return neverObservedCase.get();
        }
    }
}
