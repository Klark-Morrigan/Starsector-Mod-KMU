package kmu.ui.chooser;

import kmu.conditions.KmuConditionService;
import kmu.conditions.StarsectorEditableMarket;
import kmu.ui.chooser.action.KmuConditionChooserActionHandler;
import kmu.ui.chooser.dialog.KmuConditionChooserDialogDelegate;
import kmu.ui.chooser.dialog.KmuConditionChooserDialogOpener;
import kmu.ui.chooser.model.KmuConditionChooserModelFactory;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.editor.KmuConditionEditor;

import java.util.Objects;

public final class KmuConditionChooserEditor implements KmuConditionEditor {
    private final KmuConditionService conditionService;
    private final KmuConditionChooserModelFactory modelFactory;
    private final KmuConditionChooserDialogOpener dialogOpener;

    public KmuConditionChooserEditor(
            KmuConditionService conditionService,
            KmuConditionChooserModelFactory modelFactory,
            KmuConditionChooserDialogOpener dialogOpener) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.dialogOpener = Objects.requireNonNull(dialogOpener, "dialogOpener");
    }

    public KmuConditionChooserEditor(
            KmuConditionService conditionService,
            KmuConditionChooserDialogOpener dialogOpener) {
        this(conditionService, new KmuConditionChooserModelFactory(conditionService), dialogOpener);
    }

    @Override
    public void open(KmuMarketUiContext context) {
        Objects.requireNonNull(context, "context");
        StarsectorEditableMarket market = new StarsectorEditableMarket(context.getMarket());
        KmuConditionChooserActionHandler actionHandler = new KmuConditionChooserActionHandler(
                conditionService,
                modelFactory,
                market);
        dialogOpener.open(new KmuConditionChooserDialogDelegate(actionHandler));
    }
}
