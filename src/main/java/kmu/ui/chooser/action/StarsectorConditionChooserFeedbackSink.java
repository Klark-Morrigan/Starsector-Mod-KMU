package kmu.ui.chooser.action;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

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

            StarsectorUiColor color = feedback.isFailure()
                ? StarsectorUiColor.RED
                : StarsectorUiColor.GREEN;

            campaignUI.addMessage(
                feedback.getMessage(),
                StarsectorUiColorProvider.get(color));
        } catch (RuntimeException exception) {
            // Feedback must not break the editor action.
        }
    }
}
