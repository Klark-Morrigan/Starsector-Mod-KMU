package kmu.conditions.ui.picker.action;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import org.apache.log4j.Logger;

import java.util.Objects;

public final class StarsectorConditionPickerFeedbackSink
        implements KmuConditionPickerFeedbackSink {

    private static final Logger LOG = Global.getLogger(StarsectorConditionPickerFeedbackSink.class);

    @Override
    public void report(KmuConditionPickerFeedback feedback) {

        Objects.requireNonNull(feedback, "feedback");

        try {

            var sector = Global.getSector();
            if (sector == null) {
                return;
            }

            var campaignUI = sector.getCampaignUI();
            if (campaignUI == null) {
                return;
            }

            var colour = feedback.isFailure()
                ? StarsectorUiColour.VANILLA_HIGHLIGHT_RED
                : StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN;

            campaignUI.addMessage(
                feedback.getMessage(),
                colour.resolve());

        } catch (RuntimeException runtimeException) {

            // Feedback must not break the editor action, but a message the player never saw is
            // still a fault worth a trace, and the log is the only place left to put it.
            LOG.warn(
                "Failed to show Market Condition Manager feedback: " + feedback.getMessage(),
                runtimeException);
        }
    }
}
