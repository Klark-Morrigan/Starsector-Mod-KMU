package kmu.ui.chooser.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.Objects;

public final class StarsectorInteractionDialogChooserOpener implements KmuConditionChooserDialogOpener {
    private final SectorAPI sector;

    public StarsectorInteractionDialogChooserOpener() {
        this(Global.getSector());
    }

    public StarsectorInteractionDialogChooserOpener(SectorAPI sector) {
        this.sector = Objects.requireNonNull(sector, "sector");
    }

    @Override
    public void open(KmuConditionChooserDialogDelegate dialogDelegate) {
        Objects.requireNonNull(dialogDelegate, "dialogDelegate");

        CampaignUIAPI campaignUI = sector.getCampaignUI();
        if (campaignUI == null) {
            throw new IllegalStateException("No campaign UI is active.");
        }

        InteractionDialogAPI dialog = campaignUI.getCurrentInteractionDialog();
        if (dialog == null) {
            throw new IllegalStateException("No interaction dialog is active.");
        }

        dialog.showCustomDialog(dialogDelegate.getWidth(), dialogDelegate.getHeight(), dialogDelegate);
    }
}
