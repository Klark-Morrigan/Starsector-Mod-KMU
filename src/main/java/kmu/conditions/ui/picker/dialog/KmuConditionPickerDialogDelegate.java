package kmu.conditions.ui.picker.dialog;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.starsector.ui.highlight.HighlightedParagraph;

import kmu.util.KmuStrings;
import kmu.conditions.domain.KmuConditionAddResult;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.action.KmuConditionPickerActionHandler;
import kmu.conditions.ui.picker.action.KmuConditionPickerFeedback;
import kmu.conditions.ui.picker.action.KmuConditionPickerFeedbackSink;
import kmu.conditions.ui.picker.action.StarsectorConditionPickerFeedbackSink;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.render.KmuConditionIconGrid;
import kmu.conditions.ui.picker.render.KmuConditionPickerContainer;
import kmu.conditions.ui.picker.render.KmuConditionPickerRenderResult;
import kmu.conditions.ui.picker.render.paragraph.KmuConditionPickerSummaryParagraphFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KmuConditionPickerDialogDelegate implements CustomDialogDelegate {
    private static final float DEFAULT_WIDTH = KmuConditionIconGrid.computeDefaultTotalWidth();
    private static final float DEFAULT_HEIGHT = 560f;
    private static final float BODY_BG_ALPHA = 0.85f;

    private final KmuConditionPickerActionHandler actionHandler;
    private final KmuConditionPickerFeedbackSink feedbackSink;
    private final float width;
    private final float height;
    private final CustomUIPanelPlugin panelPlugin = new ActionPanelPlugin();
    private final List<UIComponentAPI> renderedCustomComponents = new ArrayList<>();
    private CustomPanelAPI panel;
    // Labels (location, summary) live in a non-scrollable element so they stay
    // pinned while the user scrolls through the condition grid below.
    private TooltipMakerAPI headerBody;
    private TooltipMakerAPI gridBody;
    private KmuConditionPickerRenderResult renderResult;

    public KmuConditionPickerDialogDelegate(KmuConditionPickerActionHandler actionHandler) {
        this(actionHandler, new StarsectorConditionPickerFeedbackSink(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public KmuConditionPickerDialogDelegate(
            KmuConditionPickerActionHandler actionHandler,
            float width,
            float height) {
        this(actionHandler, new StarsectorConditionPickerFeedbackSink(), width, height);
    }

    public KmuConditionPickerDialogDelegate(
            KmuConditionPickerActionHandler actionHandler,
            KmuConditionPickerFeedbackSink feedbackSink,
            float width,
            float height) {
        this.actionHandler = Objects.requireNonNull(actionHandler, "actionHandler");
        this.feedbackSink = Objects.requireNonNull(feedbackSink, "feedbackSink");
        this.width = width;
        this.height = height;
    }

    public KmuConditionPickerModel getModel() {
        return actionHandler.getModel();
    }

    public Optional<KmuConditionPickerFeedback> getFeedback() {
        return actionHandler.getFeedback();
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
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
        return KmuStrings.get(KmuStrings.DIALOG_CLOSE);
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
        return panelPlugin;
    }

    void handleAction(KmuConditionPickerAction action) {
        if (action.isPresentAtRender()) {
            return;
        }
        KmuConditionAddResult result = actionHandler.handle(action);
        actionHandler.getFeedback().ifPresent(feedbackSink::report);
        updateRenderedState(result);
    }

    private void refreshBody() {
        if (panel == null) {
            return;
        }
        removeRenderedCustomComponents();
        if (headerBody != null) {
            panel.removeComponent(headerBody);
        }
        if (gridBody != null) {
            panel.removeComponent(gridBody);
        }

        KmuConditionPickerModel model = actionHandler.getModel();
        float headerH = KmuConditionPickerContainer.computeHeaderHeight(model);
        float gridH = height - headerH;

        headerBody = panel.createUIElement(width, headerH, false);
        headerBody.setBgAlpha(BODY_BG_ALPHA);

        gridBody = panel.createUIElement(width, gridH, true);
        gridBody.setBgAlpha(BODY_BG_ALPHA);
        gridBody.setActionListenerDelegate((buttonId, data) -> {
            resolveActionFromUiEvent(buttonId, data).ifPresent(this::handleAction);
        });

        renderResult = new KmuConditionPickerContainer(panel).render(
                headerBody,
                gridBody,
                model,
                this::handleAction,
                width);
        renderedCustomComponents.addAll(renderResult.getCustomComponents());
        panel.addUIElement(headerBody).inTL(0f, 0f);
        panel.addUIElement(gridBody).inTL(0f, headerH);
    }

    private void updateRenderedState(KmuConditionAddResult result) {
        if (renderResult == null) {
            return;
        }

        LabelAPI summaryLabel = renderResult.getSummaryLabel();
        if (summaryLabel != null) {
            // Item 1 is the counts line; item 0 is the static "Conditions:" header.
            HighlightedParagraph countsParagraph =
                    KmuConditionPickerSummaryParagraphFactory.get(actionHandler.getModel()).get(1);
            summaryLabel.setText(countsParagraph.getText());
            countsParagraph.applyTo(summaryLabel);
        }

        Optional<String> conditionId = result.getConditionId();
        if (!conditionId.isPresent()) {
            return;
        }
        actionHandler.getModel()
                .findEntry(conditionId.get())
                .ifPresent(renderResult::updateEntry);
    }

    private void removeRenderedCustomComponents() {
        for (UIComponentAPI component : renderedCustomComponents) {
            try {
                panel.removeComponent(component);
            } catch (RuntimeException exception) {
                // A nested component may already have been removed with its parent UI element.
            }
        }
        renderedCustomComponents.clear();
        renderResult = null;
    }

    static Optional<KmuConditionPickerAction> resolveActionFromUiEvent(Object buttonId, Object data) {
        Optional<KmuConditionPickerAction> directAction = asAction(buttonId);
        if (directAction.isPresent()) {
            return directAction;
        }

        Optional<KmuConditionPickerAction> dataAction = asAction(data);
        if (dataAction.isPresent()) {
            return dataAction;
        }

        if (buttonId instanceof ButtonAPI) {
            return asAction(((ButtonAPI) buttonId).getCustomData());
        }
        return Optional.empty();
    }

    private static Optional<KmuConditionPickerAction> asAction(Object value) {
        if (value instanceof KmuConditionPickerAction) {
            return Optional.of((KmuConditionPickerAction) value);
        }
        return Optional.empty();
    }

    private final class ActionPanelPlugin implements CustomUIPanelPlugin {
        @Override
        public void positionChanged(PositionAPI position) {
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
        }

        @Override
        public void advance(float amount) {
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
        }

        @Override
        public void buttonPressed(Object buttonId) {
            resolveActionFromUiEvent(buttonId, null)
                    .ifPresent(KmuConditionPickerDialogDelegate.this::handleAction);
        }
    }
}
