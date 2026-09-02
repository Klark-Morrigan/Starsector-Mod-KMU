package kmu.maplayers.base.visibility.observations;

import kmu.maplayers.base.visibility.observations.ObservationRecency.RecalledObservation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the triad every family dates its news by: which state a fact lands in when the live reading
 * and the record disagree, and that each of the three reaches its own case and no other.
 *
 * <p>The order is the case worth pinning. A record beating a live reading would date a fact the
 * player is looking at, and a live reading beating nothing is what keeps a first sighting off the
 * unobserved arm - so both are asserted rather than left to the two lines that implement them.
 */
final class ObservationRecencyTest {

    private static final long OBSERVED_AT = 4_200L;
    private static final boolean SOMETHING_IS_LOOKING = true;
    private static final boolean NOBODY_IS_LOOKING = false;

    // What each case answers, so a fold reaching the wrong arm names the arm it reached.
    private static final String OBSERVED_NOW_ANSWER = "observed now";
    private static final String RECALLED_ANSWER = "recalled";
    private static final String NEVER_OBSERVED_ANSWER = "never observed";

    // Which arm a state reaches, named, so a fold landing in the wrong one says which one it was.
    private static String foldToAnswer(ObservationRecency recency) {

        return recency.selectByCase(
            () -> OBSERVED_NOW_ANSWER,
            recalled -> RECALLED_ANSWER,
            () -> NEVER_OBSERVED_ANSWER);
    }

    @Nested
    class ResolveRecency {

        @Test
        void reports_a_fact_being_revealed_now_whatever_the_record_holds() {
            // The live reading is the newer of the two by definition, so a record cannot outrank
            // it - a date beside a fact somebody is looking at is stale the moment it is drawn.
            var recency = ObservationRecency.resolveRecency(
                SOMETHING_IS_LOOKING,
                Optional.of(new RecalledObservation(Optional.of(OBSERVED_AT))));

            assertThat(recency)
                .isEqualTo(ObservationRecency.OBSERVED_NOW);
        }

        @Test
        void reports_a_fact_being_revealed_now_where_the_record_holds_nothing() {
            // A first sighting: the live reading is the whole of the news, and a rule that reached
            // for the record first would call it unobserved while somebody was looking at it.
            var recency = ObservationRecency.resolveRecency(SOMETHING_IS_LOOKING, Optional.empty());

            assertThat(recency)
                .isEqualTo(ObservationRecency.OBSERVED_NOW);
        }

        @Test
        void recalls_the_record_where_nothing_is_revealing_the_fact() {

            var recency = ObservationRecency.resolveRecency(
                NOBODY_IS_LOOKING,
                Optional.of(new RecalledObservation(Optional.of(OBSERVED_AT))));

            assertThat(recency)
                .isEqualTo(new RecalledObservation(Optional.of(OBSERVED_AT)));
        }

        @Test
        void reports_a_fact_nobody_ever_established_where_the_record_holds_nothing() {

            var recency = ObservationRecency.resolveRecency(NOBODY_IS_LOOKING, Optional.empty());

            assertThat(recency)
                .isEqualTo(ObservationRecency.NEVER_OBSERVED);
        }
    }

    @Nested
    class SelectByCase {

        @Test
        void runs_the_observed_now_case_for_a_fact_being_revealed() {
            assertThat(foldToAnswer(ObservationRecency.OBSERVED_NOW))
                .isEqualTo(OBSERVED_NOW_ANSWER);
        }

        @Test
        void runs_the_recalled_case_with_the_moment_the_record_holds() {

            var observation = new RecalledObservation(Optional.of(OBSERVED_AT));

            assertThat(observation.selectByCase(
                    () -> Optional.<Long>empty(),
                    RecalledObservation::observedTimestamp,
                    () -> Optional.<Long>empty()))
                .contains(OBSERVED_AT);
        }

        @Test
        void runs_the_recalled_case_for_an_observation_carrying_no_moment() {
            // A value recorded before observations were timed is still recalled news, not absent
            // news, so it must reach the same arm as a dated one and simply carry nothing.
            var observation = new RecalledObservation(Optional.empty());

            assertThat(foldToAnswer(observation))
                .isEqualTo(RECALLED_ANSWER);
            assertThat(observation.observedTimestamp())
                .isEmpty();
        }

        @Test
        void runs_the_never_observed_case_for_a_fact_nobody_established() {
            assertThat(foldToAnswer(ObservationRecency.NEVER_OBSERVED))
                .isEqualTo(NEVER_OBSERVED_ANSWER);
        }
    }

    @Nested
    class Constructor {

        @Test
        void reads_an_unstated_moment_as_no_moment() {
            // A hand-built value is one hop from a load, and a record made before observations
            // were timed must read back rather than surface at whatever asks it for a date.
            assertThat(new RecalledObservation(null).observedTimestamp())
                .isEmpty();
        }
    }
}
