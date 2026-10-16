package kmu.conditions.ui.picker;

import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.conditions.ui.context.KmuMarketUiContext;
import kmu.conditions.ui.editor.KmuConditionEditor;
import kmu.conditions.ui.picker.action.KmuConditionPickerActionHandler;
import kmu.conditions.ui.picker.action.KmuConditionPickerFeedbackSink;
import kmu.conditions.ui.picker.dialog.KmuConditionPickerDialogDelegate;
import kmu.conditions.ui.picker.dialog.KmuConditionPickerDialogOpener;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;

import java.util.Objects;

public final class KmuConditionPickerEditor
        implements KmuConditionEditor {

    private final KmuConditionService conditionService;
    private final KmuConditionPickerModelFactory modelFactory;
    private final KmuConditionPickerDialogOpener dialogOpener;
    private final KmuConditionPickerFeedbackSink feedbackSink;

    public KmuConditionPickerEditor(
            KmuConditionService conditionService,
            KmuConditionPickerModelFactory modelFactory,
            KmuConditionPickerDialogOpener dialogOpener,
            KmuConditionPickerFeedbackSink feedbackSink) {

        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.dialogOpener = Objects.requireNonNull(dialogOpener, "dialogOpener");
        this.feedbackSink = Objects.requireNonNull(feedbackSink, "feedbackSink");
    }

    public KmuConditionPickerEditor(
            KmuConditionService conditionService,
            KmuConditionPickerDialogOpener dialogOpener,
            KmuConditionPickerFeedbackSink feedbackSink) {

        this(
            conditionService,
            new KmuConditionPickerModelFactory(conditionService),
            dialogOpener,
            feedbackSink);
    }

    @Override
    public void open(KmuMarketUiContext context) {

        Objects.requireNonNull(context, "context");

        var market = new StarsectorEditableMarket(context.getMarket());
        var actionHandler = new KmuConditionPickerActionHandler(
            conditionService,
            modelFactory,
            market);

        dialogOpener.open(new KmuConditionPickerDialogDelegate(actionHandler, feedbackSink));
    }
}
