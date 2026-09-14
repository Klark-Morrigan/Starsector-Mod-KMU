package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.CampaignClockAPI;

import kmu.starsector.StarsectorSettingsFake;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the words a remark about the age of somebody's news is composed of.
 *
 * <p>Every case asserts the whole composed sentence rather than either half of it, because the two
 * arguments the lead-in takes are both strings and a pair swapped for each other would compose
 * something that still reads like a remark.
 *
 * <p>The clock's two reads of the moment are stubbed in the shared fixture's terms, the game's own
 * clock being what turns a stamp into a span and into a date - so a case states the age it is
 * about rather than arithmetic over two moments.
 */
final class ObservationNoteFormatterTest {

    // When the axis was observed, as a register stamps it, and the date the clock reports for it.
    private static final long OBSERVED_AT = 4_200L;
    private static final String OBSERVED_DATE = "c206.05.12";

    private CampaignClockAPI clockMock;

    @BeforeEach
    void openClock() {

        clockMock = mock(CampaignClockAPI.class);

        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class FormatObservationNote {

        @Test
        void statesHowLongAgoAndOnWhatDateTheAxisWasObserved() {
            // Both halves, in one sentence: the span is what a reader judges the news by, and the
            // date is what they hold it against.
            assertThat(formatNoteObservedDaysAgo(34.0f))
                .isEqualTo("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void statesTodayForAnAxisObservedWithinTheDay() {
            // A span short of a day named rather than rounded to nought: "0 days ago" reads as a
            // fault in the surface, and the reader is being told the news is fresh.
            assertThat(formatNoteObservedDaysAgo(0.4f))
                .isEqualTo("last seen today (c206.05.12)");
        }

        @Test
        void statesADayAgoForAnAxisObservedTheDayBefore() {

            assertThat(formatNoteObservedDaysAgo(1.5f))
                .isEqualTo("last seen a day ago (c206.05.12)");
        }

        @ParameterizedTest
        @MethodSource("kmu.maplayers.base.visibility.observations.ObservationNoteFormatterTest"
            + "#listSpansStandingOnAWordingThreshold")
        void statesTheOlderWordingForASpanStandingExactlyOnAThreshold(
                float elapsedDays,
                String expectedNote) {

            // Both thresholds are compared with a strict less-than, and every other case here sits
            // well clear of them - so the boundary itself is the one place the comparison could be
            // loosened without a single assertion noticing. A day that has fully elapsed is a day
            // ago, not today.
            assertThat(formatNoteObservedDaysAgo(elapsedDays))
                .isEqualTo(expectedNote);
        }

        @Test
        void statesWholeDaysForASpanCarryingPartOfAnother() {
            // The clock reports a fraction, and every other case hands over a span that happens to
            // be whole. Part of a day is not another day: rounding here would report an axis
            // observed this morning as observed tomorrow.
            assertThat(formatNoteObservedDaysAgo(34.9f))
                .isEqualTo("last seen 34 days ago (c206.05.12)");
        }

        @ParameterizedTest
        @MethodSource("kmu.maplayers.base.visibility.observations.ObservationNoteFormatterTest"
            + "#listLeadInsTheSameSpanIsComposedUnder")
        void statesTheSameSpanWordsUnderEitherLeadIn(String leadInKey, String expectedNote) {

            // What the split is for. The lead-in is the axis's own and the span words are not, so
            // one axis cannot come to phrase an age differently from another - which is the drift
            // the shared formatter exists to close.
            ObservationClockFixture.stubMomentOnClock(
                clockMock,
                OBSERVED_AT,
                34.0f,
                OBSERVED_DATE);

            assertThat(ObservationNoteFormatter
                    .formatObservationNote(clockMock, leadInKey, OBSERVED_AT))
                .isEqualTo(expectedNote);
        }
    }

    // The two spans the wording turns on, each stated exactly rather than either side of it: a span
    // of one whole day is the first that is no longer today, and one of two whole days the first
    // that is no longer the day before. Paired with the remark each is due, so a threshold loosened
    // by one comparison names the case it broke.
    static Stream<Arguments> listSpansStandingOnAWordingThreshold() {

        return Stream.of(
            Arguments.of(1.0f, "last seen a day ago (c206.05.12)"),
            Arguments.of(2.0f, "last seen 2 days ago (c206.05.12)"));
    }

    // Two lead-ins the same age is composed under. The second is borrowed from elsewhere in the
    // shipped strings purely for its shape - two slots, in the order a lead-in takes them - there
    // being one axis in hand today and the point being that the class holds no opinion about which
    // words introduce it. The span reads the same in both.
    static Stream<Arguments> listLeadInsTheSameSpanIsComposedUnder() {

        return Stream.of(
            Arguments.of(
                KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN,
                "last seen 34 days ago (c206.05.12)"),
            Arguments.of(
                KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_JOINED,
                "34 days ago c206.05.12"));
    }

    // The remark composed for an axis observed the given span ago, under the lead-in a direct
    // sighting states.
    private String formatNoteObservedDaysAgo(float elapsedDays) {

        ObservationClockFixture.stubMomentOnClock(
            clockMock,
            OBSERVED_AT,
            elapsedDays,
            OBSERVED_DATE);

        return ObservationNoteFormatter.formatObservationNote(
            clockMock,
            KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN,
            OBSERVED_AT);
    }
}
