package kmu.maplayers.base.visibility.observations;

import kmu.maplayers.base.visibility.observations.ObservationRecency.RecalledObservation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pins what a fact states in each of the three states, and the one invariant a reader leans on: a
 * fact nobody ever established holds no value, so a surface is forced to supply its own words for
 * it rather than being handed something plausible nobody saw.
 */
final class RevealedFactTest {

    private static final long OBSERVED_AT = 4_200L;
    private static final String OWNER = "hegemony";
    private static final String OTHER_OWNER = "tritachyon";

    private static final RecalledObservation RECORDED_OBSERVATION =
        new RecalledObservation(Optional.of(OBSERVED_AT));

    @Nested
    class CreateNeverObservedFact {

        @Test
        void states_nothing_and_reports_a_fact_nobody_established() {

            var fact = RevealedFact.createNeverObservedFact();

            assertThat(fact.readRecency())
                .isEqualTo(ObservationRecency.NEVER_OBSERVED);
            assertThat(fact.resolveValue())
                .isEmpty();
        }
    }

    @Nested
    class CreateObservedNowFact {

        @Test
        void states_what_the_live_reading_says_and_is_due_no_date() {

            var fact = RevealedFact.createObservedNowFact(OWNER);

            assertThat(fact.readRecency())
                .isEqualTo(ObservationRecency.OBSERVED_NOW);
            assertThat(fact.resolveValue())
                .contains(OWNER);
        }

        @Test
        void refuses_an_observed_fact_that_states_nothing() {
            // An observed fact whose value was absent would be an unobserved fact wearing the
            // wrong state, and every reader below would then date something nobody had seen.
            assertThatNullPointerException()
                .isThrownBy(() -> RevealedFact.createObservedNowFact(null));
        }
    }

    @Nested
    class CreateRecalledFact {

        @Test
        void states_what_was_recorded_and_the_observation_it_came_from() {

            var fact = RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER);

            assertThat(fact.readRecency())
                .isEqualTo(new RecalledObservation(Optional.of(OBSERVED_AT)));
            assertThat(fact.resolveValue())
                .contains(OWNER);
        }

        @Test
        void refuses_a_recalled_fact_with_no_observation_behind_it() {
            // What is recalled and when it was seen are one event. A value with no observation
            // behind it is a claim about the world nothing ever made.
            assertThatNullPointerException()
                .isThrownBy(() -> RevealedFact.createRecalledFact(null, OWNER));
        }

        @Test
        void refuses_a_recalled_fact_that_states_nothing() {
            assertThatNullPointerException()
                .isThrownBy(() -> RevealedFact.createRecalledFact(RECORDED_OBSERVATION, null));
        }
    }

    @Nested
    class ResolveValue {

        @Test
        void holds_a_value_in_both_observed_states_and_none_in_the_unobserved_one() {
            // The invariant the placeholder wording hangs off: exactly one of the three answers
            // empty, so a reader that handles the empty case has handled the only case with no
            // value in it.
            assertThat(RevealedFact.createObservedNowFact(OWNER).resolveValue())
                .contains(OWNER);
            assertThat(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER).resolveValue())
                .contains(OWNER);
            assertThat(RevealedFact.createNeverObservedFact().resolveValue())
                .isEmpty();
        }
    }

    @Nested
    class Equals {

        @Test
        void reports_facts_alike_where_the_state_and_the_value_agree() {

            assertThat(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER))
                .isEqualTo(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER));
        }

        @Test
        void reports_facts_apart_where_they_state_different_things() {

            assertThat(RevealedFact.createObservedNowFact(OWNER))
                .isNotEqualTo(RevealedFact.createObservedNowFact(OTHER_OWNER));
        }

        @Test
        void reports_facts_apart_where_the_same_value_reached_them_by_different_routes() {
            // The same owner seen now and recalled from a record are different news, and a reader
            // caching by value alone would otherwise drop the date off one of them.
            assertThat(RevealedFact.createObservedNowFact(OWNER))
                .isNotEqualTo(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER));
        }
    }

    @Nested
    class HashCode {

        @Test
        void reports_one_hash_for_facts_that_are_alike() {
            // Hand-written beside a hand-written equals, so the pair can drift: a hash reading
            // fewer fields than the comparison sends alike facts to different buckets.
            assertThat(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER).hashCode())
                .isEqualTo(RevealedFact.createRecalledFact(RECORDED_OBSERVATION, OWNER).hashCode());
        }
    }
}
