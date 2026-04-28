package kmu.ui.chooser;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Objects;

public final class KmuConditionChooserDialogDelegate implements CustomDialogDelegate {
    private static final float DEFAULT_WIDTH = 720f;
    private static final float DEFAULT_HEIGHT = 560f;
    private static final float ICON_SIZE = 36f;
    private static final float ENTRY_PAD = 8f;
    private static final float TOOLTIP_WIDTH = 420f;

    private final KmuConditionChooserModel model;
    private final float width;
    private final float height;

    public KmuConditionChooserDialogDelegate(KmuConditionChooserModel model) {
        this(model, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public KmuConditionChooserDialogDelegate(
            KmuConditionChooserModel model,
            float width,
            float height) {
        this.model = Objects.requireNonNull(model, "model");
        this.width = width;
        this.height = height;
    }

    public KmuConditionChooserModel getModel() {
        return model;
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        TooltipMakerAPI body = panel.createUIElement(width, height, true);
        body.addTitle("Planetary Conditions");
        body.addPara(summaryText(), ENTRY_PAD);

        if (model.isEmpty()) {
            body.addPara("No planetary condition specs are available.", ENTRY_PAD, Misc.getGrayColor());
        } else {
            for (KmuConditionChooserEntry entry : model.getEntries()) {
                addEntry(body, entry);
            }
        }

        panel.addUIElement(body).inTL(0f, 0f);
    }

    @Override
    public boolean hasCancelButton() {
        return false;
    }

    @Override
    public String getConfirmText() {
        return "Close";
    }

    @Override
    public String getCancelText() {
        return null;
    }

    @Override
    public void customDialogConfirm() {
    }

    @Override
    public void customDialogCancel() {
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return null;
    }

    private String summaryText() {
        return model.getEntryCount()
                + " planetary conditions found; "
                + model.getPresentCount()
                + " already present on this market.";
    }

    private void addEntry(TooltipMakerAPI body, KmuConditionChooserEntry entry) {
        Color textColor = entry.isPresent() ? Misc.getTextColor() : Misc.getGrayColor();
        Color highlightColor = entry.isPresent() ? Misc.getPositiveHighlightColor() : Misc.getHighlightColor();

        if (entry.getIcon().isPresent()) {
            TooltipMakerAPI imageWithText = body.beginImageWithText(entry.getIcon().get(), ICON_SIZE);
            imageWithText.addPara(entry.getName(), 0f, textColor, entry.getName());
            imageWithText.addPara(entry.descriptionLine(), 2f, Misc.getGrayColor(), entry.getConditionId());
            body.addImageWithText(ENTRY_PAD);
        } else {
            body.addPara(entry.getName(), ENTRY_PAD, textColor, entry.getName());
            body.addPara(entry.descriptionLine(), 2f, Misc.getGrayColor(), entry.getConditionId());
        }

        body.addTooltipToPrevious(
                new EntryTooltipCreator(entry, highlightColor),
                TooltipMakerAPI.TooltipLocation.RIGHT);
    }

    private static final class EntryTooltipCreator implements TooltipMakerAPI.TooltipCreator {
        private final KmuConditionChooserEntry entry;
        private final Color highlightColor;

        private EntryTooltipCreator(KmuConditionChooserEntry entry, Color highlightColor) {
            this.entry = entry;
            this.highlightColor = highlightColor;
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return TOOLTIP_WIDTH;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addTitle(entry.getName());
            tooltip.addPara("Condition id: " + entry.getConditionId(), ENTRY_PAD, highlightColor,
                    entry.getConditionId());
            tooltip.addPara("Status: " + entry.getState().getDisplayName(), ENTRY_PAD, highlightColor,
                    entry.getState().getDisplayName());
            if (entry.getIcon().isPresent()) {
                tooltip.addPara("Icon: " + entry.getIcon().get(), ENTRY_PAD, Misc.getGrayColor(), entry.getIcon().get());
            }
            tooltip.addPara(entry.getTooltipText(), ENTRY_PAD, Misc.getGrayColor());
        }
    }
}
