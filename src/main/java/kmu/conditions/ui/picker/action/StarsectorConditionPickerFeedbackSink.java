package kmu.conditions.ui.picker.action;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import java.util.Objects;

public final class StarsectorConditionPickerFeedbackSink implements KmuConditionPickerFeedbackSink {
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
        } catch (RuntimeException exception) {
            // Feedback must not break the editor action.
        }
    }
}
