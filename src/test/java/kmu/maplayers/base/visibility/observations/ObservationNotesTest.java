package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.CampaignClockAPI;

import kmu.maplayers.base.visibility.observations.ObservationRecency.RecalledObservation;
import kmu.starsector.StarsectorSettingsFake;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins which of a row's concealed facts dates it, and in whose words.
 *
 * <p>The two facts a case weighs are stated under different lead-ins throughout, so an assertion on
 * the composed remark says both which moment won and which fact it came off. Under one shared
 * lead-in a rule that picked the right moment for the wrong reason would read identically.
 *
 * <p>The facts are synthetic. No family owns one yet, and a case written against a stand-in family
 * would pin that family's routes here rather than the rule the row is dated by.
 */
final class ObservationNotesTest {

    // Two moments a fact is recalled from, and what the clock reports for each: one well back and
    // one recent, so which of two contributions won is legible in the composed remark.
    private static final long OBSERVED_LONG_AGO = 4_200L;
    private static final float LONG_AGO_ELAPSED_DAYS = 34.0f;
    private static final String LONG_AGO_DATE = "c206.05.12";

    private static final long OBSERVED_RECENTLY = 7_100L;
    private static final float RECENTLY_ELAPSED_DAYS = 3.0f;
    private static final String RECENTLY_DATE = "c206.06.20";

    // The words two facts introduce their dates by. The second is borrowed from elsewhere in the
    // shipped strings purely for its shape - two slots, in the order a lead-in takes them - there
    // being one axis in the mod today. What matters is that the two compose visibly differently,
    // so the remark a case asserts names the fact it came off.
    private static final String LAST_SEEN_LEAD_IN = KmuStrings.POLITICAL_MAP_TOOLTIP_LAST_SEEN;
    private static final String JOINED_LEAD_IN = KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_JOINED;

    // What a row with no world to read is dated against: nothing, so nothing can be said.
    private static final CampaignClockAPI NO_CLOCK = null;

    private CampaignClockAPI clockMock;

    @BeforeEach
    void openClock() {

        clockMock = mock(CampaignClockAPI.class);

        ObservationClockFixture.stubMomentOnClock(
            clockMock,
            OBSERVED_LONG_AGO,
            LONG_AGO_ELAPSED_DAYS,
            LONG_AGO_DATE);
        ObservationClockFixture.stubMomentOnClock(
            clockMock,
            OBSERVED_RECENTLY,
            RECENTLY_ELAPSED_DAYS,
            RECENTLY_DATE);

        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveNoteForAxes {

        @Test
        void statesTheNoteOfTheOneRecalledFactOnTheRow() {

            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(note)
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void statesTheMostRecentlyRecalledFactInItsOwnWords() {
            // The whole of the rule: the later contribution dates the row, and the row says so in
            // the words of the fact that contributed it rather than in the other one's.
            var laterFactSecond = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO),
                    buildRecalledAxis(JOINED_LEAD_IN, OBSERVED_RECENTLY)));

            assertThat(laterFactSecond)
                .contains("3 days ago c206.06.20");

            // Same two facts, given the other way round. The moment decides, not the position.
            var laterFactFirst = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildRecalledAxis(JOINED_LEAD_IN, OBSERVED_RECENTLY),
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(laterFactFirst)
                .contains("3 days ago c206.06.20");
        }

        @Test
        void statesTheFirstOfTwoFactsRecalledAtTheSameMoment() {
            // Two equally recent contributions have to be settled by something, and the caller's
            // order is the only thing to hand. Pinned in both directions, so a row hovered twice
            // cannot come to state two different claims about the same pair of facts.
            var lastSeenFirst = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO),
                    buildRecalledAxis(JOINED_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(lastSeenFirst)
                .contains("last seen 34 days ago (c206.05.12)");

            var joinedFirst = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildRecalledAxis(JOINED_LEAD_IN, OBSERVED_LONG_AGO),
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(joinedFirst)
                .contains("34 days ago c206.05.12");
        }

        @Test
        void statesTheRecalledFactBesideOneBeingRevealedNow() {
            // A current fact contributes nothing rather than suppressing the row's date: what the
            // row is recalling is still old news, and the reader is owed the age of it.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildObservedNowAxis(JOINED_LEAD_IN),
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(note)
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void statesTheRecalledFactBesideOneNobodyHasEverEstablished() {
            // An unknown fact has no age to argue with, so the row reads as the recalled fact's
            // date beside whatever placeholder the unknown one states. That pairing is correct by
            // construction rather than by a rule spent on it.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildNeverObservedAxis(JOINED_LEAD_IN),
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(note)
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void statesNothingWhereEveryFactIsBeingRevealedNow() {
            // A date beside a row the reader is looking at is stale the moment it is drawn.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildObservedNowAxis(LAST_SEEN_LEAD_IN),
                    buildObservedNowAxis(JOINED_LEAD_IN)));

            assertThat(note)
                .isEmpty();
        }

        @Test
        void statesNothingWhereNobodyHasEverEstablishedAnyFact() {
            // Absent news, not old news. There is no moment to be stale.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildNeverObservedAxis(LAST_SEEN_LEAD_IN),
                    buildNeverObservedAxis(JOINED_LEAD_IN)));

            assertThat(note)
                .isEmpty();
        }

        @Test
        void statesNothingForARecalledFactCarryingNoMoment() {
            // News somebody wrote down without saying when. Recalled all the same, and simply not
            // datable - so it contributes nothing rather than a moment invented for it.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(buildUndatedRecalledAxis(LAST_SEEN_LEAD_IN)));

            assertThat(note)
                .isEmpty();
        }

        @Test
        void statesTheDatedFactBesideOneRecalledWithoutAMoment() {
            // The undated fact must contribute nothing rather than an assumed moment: taken as
            // zero it would be the oldest news on every row and never win, which reads correctly
            // here and would start dating rows the moment the winner were sought the other way.
            var note = ObservationNotes.resolveNoteForAxes(
                clockMock,
                List.of(
                    buildUndatedRecalledAxis(JOINED_LEAD_IN),
                    buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(note)
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void statesNothingForARowCarryingNoFactsAtAll() {

            assertThat(ObservationNotes.resolveNoteForAxes(clockMock, List.of()))
                .isEmpty();
        }

        @Test
        void statesNothingWhereThereIsNoClockToDateTheMomentBy() {
            // A surface with no world behind it still draws its rows; it just has nothing to date
            // them against, and must say nothing rather than fail on the way to saying it.
            var note = ObservationNotes.resolveNoteForAxes(
                NO_CLOCK,
                List.of(buildRecalledAxis(LAST_SEEN_LEAD_IN, OBSERVED_LONG_AGO)));

            assertThat(note)
                .isEmpty();
        }
    }

    // A fact the record holds from a stated moment, under the given words.
    private static ObservationAxis buildRecalledAxis(String leadInKey, long observedTimestamp) {

        return new ObservationAxis(
            leadInKey,
            new RecalledObservation(Optional.of(observedTimestamp)));
    }

    // A fact the record holds without a moment, as one written down before observations were timed.
    private static ObservationAxis buildUndatedRecalledAxis(String leadInKey) {
        return new ObservationAxis(leadInKey, new RecalledObservation(Optional.empty()));
    }

    // A fact something is revealing at this moment.
    private static ObservationAxis buildObservedNowAxis(String leadInKey) {
        return new ObservationAxis(leadInKey, ObservationRecency.OBSERVED_NOW);
    }

    // A fact nothing has ever established.
    private static ObservationAxis buildNeverObservedAxis(String leadInKey) {
        return new ObservationAxis(leadInKey, ObservationRecency.NEVER_OBSERVED);
    }

}
