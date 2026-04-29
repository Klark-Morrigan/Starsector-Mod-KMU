package kmu.ui.chooser.dialog;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import kmu.conditions.KmuConditionAddResult;
import kmu.ui.chooser.action.KmuConditionChooserAction;
import kmu.ui.chooser.action.KmuConditionChooserActionHandler;
import kmu.ui.chooser.action.KmuConditionChooserFeedback;
import kmu.ui.chooser.action.KmuConditionChooserFeedbackSink;
import kmu.ui.chooser.action.StarsectorConditionChooserFeedbackSink;
import kmu.ui.chooser.model.KmuConditionChooserModel;
import kmu.ui.chooser.render.KmuConditionPickerContainer;
import kmu.ui.chooser.render.KmuConditionPickerRenderResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class KmuConditionChooserDialogDelegate implements CustomDialogDelegate {
    private static final float DEFAULT_WIDTH = KmuConditionPickerContainer.defaultContainerWidth();
    private static final float DEFAULT_HEIGHT = 560f;
    private static final float BODY_BG_ALPHA = 0.85f;

    private final KmuConditionChooserActionHandler actionHandler;
    private final KmuConditionChooserFeedbackSink feedbackSink;
    private final float width;
    private final float height;
    private final CustomUIPanelPlugin panelPlugin = new ActionPanelPlugin();
    private final List<UIComponentAPI> renderedCustomComponents = new ArrayList<>();
    private CustomPanelAPI panel;
    private TooltipMakerAPI body;
    private KmuConditionPickerRenderResult renderResult;

    public KmuConditionChooserDialogDelegate(KmuConditionChooserActionHandler actionHandler) {
        this(actionHandler, new StarsectorConditionChooserFeedbackSink(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public KmuConditionChooserDialogDelegate(
            KmuConditionChooserActionHandler actionHandler,
            float width,
            float height) {
        this(actionHandler, new StarsectorConditionChooserFeedbackSink(), width, height);
    }

    public KmuConditionChooserDialogDelegate(
            KmuConditionChooserActionHandler actionHandler,
            KmuConditionChooserFeedbackSink feedbackSink,
            float width,
            float height) {
        this.actionHandler = Objects.requireNonNull(actionHandler, "actionHandler");
        this.feedbackSink = Objects.requireNonNull(feedbackSink, "feedbackSink");
        this.width = width;
        this.height = height;
    }

    public KmuConditionChooserModel getModel() {
        return actionHandler.getModel();
    }

    public java.util.Optional<KmuConditionChooserFeedback> getFeedback() {
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
        return panelPlugin;
    }

    void handleAction(KmuConditionChooserAction action) {
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
        if (body != null) {
            panel.removeComponent(body);
        }

        body = panel.createUIElement(width, height, true);
        body.setBgAlpha(BODY_BG_ALPHA);
        body.setActionListenerDelegate((buttonId, data) -> {
            resolveActionFromUiEvent(buttonId, data).ifPresent(this::handleAction);
        });
        renderResult = new KmuConditionPickerContainer(panel).render(
                body,
                actionHandler.getModel(),
                this::handleAction,
                width);
        renderedCustomComponents.addAll(renderResult.getCustomComponents());
        panel.addUIElement(body).inTL(0f, 0f);
    }

    private void updateRenderedState(KmuConditionAddResult result) {
        if (renderResult == null) {
            return;
        }

        LabelAPI summaryLabel = renderResult.getSummaryLabel();
        if (summaryLabel != null) {
            summaryLabel.setText(KmuConditionPickerContainer.summaryText(actionHandler.getModel()));
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

    static Optional<KmuConditionChooserAction> resolveActionFromUiEvent(Object buttonId, Object data) {
        Optional<KmuConditionChooserAction> directAction = asAction(buttonId);
        if (directAction.isPresent()) {
            return directAction;
        }

        Optional<KmuConditionChooserAction> dataAction = asAction(data);
        if (dataAction.isPresent()) {
            return dataAction;
        }

        if (buttonId instanceof ButtonAPI) {
            return asAction(((ButtonAPI) buttonId).getCustomData());
        }
        return Optional.empty();
    }

    private static Optional<KmuConditionChooserAction> asAction(Object value) {
        if (value instanceof KmuConditionChooserAction) {
            return Optional.of((KmuConditionChooserAction) value);
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
                    .ifPresent(KmuConditionChooserDialogDelegate.this::handleAction);
        }
    }
}
