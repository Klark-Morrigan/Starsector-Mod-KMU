package kmu.ui.chooser;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
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
    private static final float ACTION_BUTTON_WIDTH = 120f;
    private static final float ACTION_BUTTON_HEIGHT = 24f;

    private final KmuConditionChooserActionHandler actionHandler;
    private final float width;
    private final float height;
    private CustomPanelAPI panel;
    private TooltipMakerAPI body;

    public KmuConditionChooserDialogDelegate(KmuConditionChooserActionHandler actionHandler) {
        this(actionHandler, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public KmuConditionChooserDialogDelegate(
            KmuConditionChooserActionHandler actionHandler,
            float width,
            float height) {
        this.actionHandler = Objects.requireNonNull(actionHandler, "actionHandler");
        this.width = width;
        this.height = height;
    }

    public KmuConditionChooserModel getModel() {
        return actionHandler.getModel();
    }

    public java.util.Optional<KmuConditionChooserFeedback> getFeedback() {
        return actionHandler.getFeedback();
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.panel = Objects.requireNonNull(panel, "panel");
        refreshBody();
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

    void handleAction(KmuConditionChooserAction action) {
        actionHandler.handle(action);
        refreshBody();
    }

    private void refreshBody() {
        if (panel == null) {
            return;
        }
        if (body != null) {
            panel.removeComponent(body);
        }

        body = panel.createUIElement(width, height, true);
        body.setActionListenerDelegate((buttonId, data) -> {
            if (buttonId instanceof KmuConditionChooserAction) {
                handleAction((KmuConditionChooserAction) buttonId);
            }
        });
        renderBody(body);
        panel.addUIElement(body).inTL(0f, 0f);
    }

    private void renderBody(TooltipMakerAPI body) {
        KmuConditionChooserModel model = actionHandler.getModel();
        body.addTitle("Planetary Conditions");
        body.addPara(summaryText(), ENTRY_PAD);
        actionHandler.getFeedback().ifPresent(feedback -> body.addPara(
                feedback.getMessage(),
                ENTRY_PAD,
                feedback.isFailure() ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor()));

        if (model.isEmpty()) {
            body.addPara("No planetary condition specs are available.", ENTRY_PAD, Misc.getGrayColor());
        } else {
            for (KmuConditionChooserEntry entry : model.getEntries()) {
                addEntry(body, entry);
            }
        }
    }

    private String summaryText() {
        KmuConditionChooserModel model = actionHandler.getModel();
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

        ButtonAPI button = body.addButton(
                actionButtonText(entry),
                KmuConditionChooserAction.fromEntry(entry),
                ACTION_BUTTON_WIDTH,
                ACTION_BUTTON_HEIGHT,
                4f);
        button.setEnabled(!entry.isPresent());

        body.addTooltipToPrevious(
                new EntryTooltipCreator(entry, highlightColor),
                TooltipMakerAPI.TooltipLocation.RIGHT);
    }

    private String actionButtonText(KmuConditionChooserEntry entry) {
        return entry.isPresent() ? "Present" : "Add";
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
