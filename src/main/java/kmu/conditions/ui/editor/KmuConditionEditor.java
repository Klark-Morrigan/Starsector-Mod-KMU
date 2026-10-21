package kmu.conditions.ui.editor;

import kmu.conditions.ui.context.KmuMarketUiContext;

@FunctionalInterface
public interface KmuConditionEditor {
    void open(KmuMarketUiContext context);
}
