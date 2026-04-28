package kmu.ui.editor;

import kmu.ui.context.KmuMarketUiContext;

@FunctionalInterface
public interface KmuConditionEditor {
    void open(KmuMarketUiContext context);
}
