package kmu.conditions.ui.picker;

import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.conditions.ui.editor.KmuConditionEditor;
import kmu.conditions.ui.picker.action.KmuConditionPickerActionHandler;
import kmu.conditions.ui.picker.dialog.KmuConditionPickerDialogDelegate;
import kmu.conditions.ui.picker.dialog.KmuConditionPickerDialogOpener;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;
import kmu.ui.context.KmuMarketUiContext;

import java.util.Objects;

public final class KmuConditionPickerEditor implements KmuConditionEditor {
    private final KmuConditionService conditionService;
    private final KmuConditionPickerModelFactory modelFactory;
    private final KmuConditionPickerDialogOpener dialogOpener;

    public KmuConditionPickerEditor(
            KmuConditionService conditionService,
            KmuConditionPickerModelFactory modelFactory,
            KmuConditionPickerDialogOpener dialogOpener) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.dialogOpener = Objects.requireNonNull(dialogOpener, "dialogOpener");
    }

    public KmuConditionPickerEditor(
            KmuConditionService conditionService,
            KmuConditionPickerDialogOpener dialogOpener) {
        this(conditionService, new KmuConditionPickerModelFactory(conditionService), dialogOpener);
    }

    @Override
    public void open(KmuMarketUiContext context) {
        Objects.requireNonNull(context, "context");

        var market = new StarsectorEditableMarket(context.getMarket());
        var actionHandler = new KmuConditionPickerActionHandler(
            conditionService,
            modelFactory,
            market);
            
        dialogOpener.open(new KmuConditionPickerDialogDelegate(actionHandler));
    }
}
