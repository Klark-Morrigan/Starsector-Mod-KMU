package kmu.conditions.ui.picker.dialog;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.Objects;

public final class StarsectorInteractionDialogPickerOpener implements KmuConditionPickerDialogOpener {
    private final SectorAPI sector;

    public StarsectorInteractionDialogPickerOpener() {
        this(Global.getSector());
    }

    public StarsectorInteractionDialogPickerOpener(SectorAPI sector) {
        this.sector = Objects.requireNonNull(sector, "sector");
    }

    @Override
    public void open(KmuConditionPickerDialogDelegate dialogDelegate) {
        Objects.requireNonNull(dialogDelegate, "dialogDelegate");

        var campaignUI = sector.getCampaignUI();
        if (campaignUI == null) {
            throw new IllegalStateException("No campaign UI is active.");
        }

        var dialog = campaignUI.getCurrentInteractionDialog();
        if (dialog == null) {
            throw new IllegalStateException("No interaction dialog is active.");
        }

        dialog.showCustomDialog(dialogDelegate.getWidth(), dialogDelegate.getHeight(), dialogDelegate);
    }
}
