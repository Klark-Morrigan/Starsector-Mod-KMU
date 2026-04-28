package kmu.ui.chooser;

import kmu.conditions.StarsectorEditableMarket;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.editor.KmuConditionEditor;

import java.util.Objects;

public final class KmuConditionChooserEditor implements KmuConditionEditor {
    private final KmuConditionChooserModelFactory modelFactory;
    private final KmuConditionChooserDialogOpener dialogOpener;

    public KmuConditionChooserEditor(
            KmuConditionChooserModelFactory modelFactory,
            KmuConditionChooserDialogOpener dialogOpener) {
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.dialogOpener = Objects.requireNonNull(dialogOpener, "dialogOpener");
    }

    @Override
    public void open(KmuMarketUiContext context) {
        Objects.requireNonNull(context, "context");
        KmuConditionChooserModel model = modelFactory.create(new StarsectorEditableMarket(context.getMarket()));
        dialogOpener.open(new KmuConditionChooserDialogDelegate(model));
    }
}
