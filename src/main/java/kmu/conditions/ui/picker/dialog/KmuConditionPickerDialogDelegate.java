package kmu.conditions.ui.picker.dialog;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.starsector.ui.buttons.VanillaActionIds;

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
import kmu.util.KmuStringKeys;

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
        return KmuStringKeys.get(KmuStringKeys.DIALOG_CLOSE);
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
        var result = actionHandler.handle(action);
        actionHandler.getFeedback().ifPresent(feedbackSink::report);
        updateRenderedState(result);
    }

    // Where the ID sits among the two objects a vanilla action delegate is handed is a fact about the
    // engine's widgets rather than about this picker, so the search lives in KMLib beside the rest of what
    // is known about a button the game built. Wrapped here because this end already answers in Optional.
    static Optional<KmuConditionPickerAction> resolveActionFromUiEvent(Object buttonId, Object data) {

        return Optional.ofNullable(VanillaActionIds.resolveActionId(
            buttonId,
            data,
            KmuConditionPickerAction.class));
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

        var model = actionHandler.getModel();
        var headerH = KmuConditionPickerContainer.computeHeaderHeight(model);
        var gridH = height - headerH;

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

        var summaryLabel = renderResult.getSummaryLabel();
        if (summaryLabel != null) {

            // Item 1 is the counts line; item 0 is the static "Conditions:" header.
            var countsParagraph = KmuConditionPickerSummaryParagraphFactory
                .get(actionHandler.getModel())
                .get(1);

            summaryLabel.setText(countsParagraph.getText());
            countsParagraph.applyTo(summaryLabel);
        }

        var conditionId = result.getConditionId();
        if (!conditionId.isPresent()) {
            return;
        }
        actionHandler.getModel()
            .findEntry(conditionId.get())
            .ifPresent(renderResult::updateEntry);
    }

    private void removeRenderedCustomComponents() {
        for (var component : renderedCustomComponents) {
            try {
                panel.removeComponent(component);
            } catch (RuntimeException exception) {
                // A nested component may already have been removed with its parent UI element.
            }
        }
        renderedCustomComponents.clear();
        renderResult = null;
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
