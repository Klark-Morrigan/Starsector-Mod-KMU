package kmu.conditions.ui.picker.action;

import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the message a picker action leaves in the campaign's message list: its colour by outcome, and that a
 * campaign unable to take it never breaks the action that reported it, while the failure is logged.
 */
class StarsectorConditionPickerFeedbackSinkTests {

    // Distinct shades, so a message tinted in the wrong one can be told apart.
    private static final Color NEGATIVE_HIGHLIGHT = new Color(255, 60, 60);
    private static final Color POSITIVE_HIGHLIGHT = new Color(60, 255, 60);

    private static final String MESSAGE = "hot added";

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installHighlightColours() {

        // Misc reads the settings as it loads, and a class that fails to load stays broken for every
        // later suite in the JVM, so the settings go in first.
        StarsectorSettingsFake.installSettings();

        miscMock = mockStatic(Misc.class);
        miscMock
            .when(Misc::getNegativeHighlightColor)
            .thenReturn(NEGATIVE_HIGHLIGHT);
        miscMock
            .when(Misc::getPositiveHighlightColor)
            .thenReturn(POSITIVE_HIGHLIGHT);
    }

    @AfterEach
    void closeHighlightColours() {

        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class Constructor {

        @Test
        void rejectsNoSector() {

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                assertThatThrownBy(() -> new StarsectorConditionPickerFeedbackSink(null))
                    .isInstanceOf(NullPointerException.class);
            }
        }
    }

    @Nested
    class Report {

        @Test
        void showsSuccessInPositiveHighlight() {

            // Stubbed before the static stubbing is opened, never inside its argument: Mockito reads
            // a mock arranged mid-statement as an unfinished stubbing of the outer one.
            var campaignUiMock = mock(CampaignUIAPI.class);
            var sectorMock = stubSectorShowing(campaignUiMock);
            var feedbackMock = stubFeedback(false);

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                new StarsectorConditionPickerFeedbackSink(sectorMock).report(feedbackMock);
            }

            verify(campaignUiMock)
                .addMessage(MESSAGE, POSITIVE_HIGHLIGHT);
        }

        @Test
        void showsFailureInNegativeHighlight() {

            var campaignUiMock = mock(CampaignUIAPI.class);
            var sectorMock = stubSectorShowing(campaignUiMock);
            var feedbackMock = stubFeedback(true);

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                new StarsectorConditionPickerFeedbackSink(sectorMock).report(feedbackMock);
            }

            verify(campaignUiMock)
                .addMessage(MESSAGE, NEGATIVE_HIGHLIGHT);
        }

        @Test
        void passesQuietlyWithNoCampaignUi() {

            var sectorMock = stubSectorShowing(null);
            var feedbackMock = stubFeedback(false);

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                assertThatCode(() -> new StarsectorConditionPickerFeedbackSink(sectorMock).report(feedbackMock))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void containsFailureRaisedByTheMessageList() {

            // The condition is already added by the time the message is posted, so a message that
            // cannot be shown must not turn a finished action into a failed one.
            var campaignUiMock = mock(CampaignUIAPI.class);

            doThrow(new IllegalStateException("message list closed"))
                .when(campaignUiMock)
                .addMessage(anyString(), any(Color.class));

            var sectorMock = stubSectorShowing(campaignUiMock);
            var feedbackMock = stubFeedback(false);

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                assertThatCode(() -> new StarsectorConditionPickerFeedbackSink(sectorMock).report(feedbackMock))
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void logsTheFailureRaisedByTheMessageListWithItsCause() {
            // Contained is not the same as silent: a message the player never saw leaves its trace in
            // the log, which is the one place left to put it.
            var campaignUiMock = mock(CampaignUIAPI.class);
            var failure = new IllegalStateException("message list closed");

            doThrow(failure)
                .when(campaignUiMock)
                .addMessage(anyString(), any(Color.class));

            var sectorMock = stubSectorShowing(campaignUiMock);
            var feedbackMock = stubFeedback(false);

            try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

                var log = LogAppenderFake.captureLogOf(
                    StarsectorConditionPickerFeedbackSink.class,
                    () -> new StarsectorConditionPickerFeedbackSink(sectorMock).report(feedbackMock));

                assertThat(log.getMessages())
                    .containsExactly("Failed to show Market Condition Manager feedback: hot added");
                assertThat(log.getThrowables())
                    .containsExactly(failure);
            }
        }
    }

    private static SectorAPI stubSectorShowing(CampaignUIAPI campaignUi) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getCampaignUI())
            .thenReturn(campaignUi);

        return sectorMock;
    }

    // The wording is the feedback's own and is not this sink's to pin, so it is handed in whole.
    private static KmuConditionPickerFeedback stubFeedback(boolean isFailure) {

        var feedbackMock = mock(KmuConditionPickerFeedback.class);

        when(feedbackMock.getMessage())
            .thenReturn(MESSAGE);
        when(feedbackMock.isFailure())
            .thenReturn(isFailure);

        return feedbackMock;
    }
}
