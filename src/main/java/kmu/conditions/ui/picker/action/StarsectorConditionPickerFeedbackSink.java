package kmu.conditions.ui.picker.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import kmlib.starsector.ui.color.StarsectorUiColor;

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

            var color = feedback.isFailure()
                ? StarsectorUiColor.VANILLA_HIGHLIGHT_RED
                : StarsectorUiColor.VANILLA_HIGHLIGHT_GREEN;

            campaignUI.addMessage(
                feedback.getMessage(),
                color.resolve());
        } catch (RuntimeException exception) {
            // Feedback must not break the editor action.
        }
    }
}
