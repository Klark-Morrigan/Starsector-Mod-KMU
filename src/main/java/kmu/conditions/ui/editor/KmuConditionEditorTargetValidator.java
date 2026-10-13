package kmu.conditions.ui.editor;

import kmu.conditions.ui.context.KmuMarketUiContext;

import java.util.Optional;

@FunctionalInterface
public interface KmuConditionEditorTargetValidator {
    Optional<String> getUnsupportedReason(KmuMarketUiContext context);
}
