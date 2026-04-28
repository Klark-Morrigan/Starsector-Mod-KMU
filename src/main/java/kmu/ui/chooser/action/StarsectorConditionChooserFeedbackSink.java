package kmu.ui.chooser.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Objects;

public final class StarsectorConditionChooserFeedbackSink implements KmuConditionChooserFeedbackSink {
    @Override
    public void report(KmuConditionChooserFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        try {
            SectorAPI sector = Global.getSector();
            if (sector == null) {
                return;
            }
            CampaignUIAPI campaignUI = sector.getCampaignUI();
            if (campaignUI == null) {
                return;
            }
            campaignUI.addMessage(
                    feedback.getMessage(),
                    feedback.isFailure() ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor());
        } catch (RuntimeException exception) {
            // Feedback must not break the editor action.
        }
    }
}
