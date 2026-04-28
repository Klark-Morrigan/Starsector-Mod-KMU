package kmu.ui.editor;

import kmu.ui.context.KmuMarketUiContext;

import java.util.Optional;

@FunctionalInterface
public interface KmuConditionEditorTargetValidator {
    Optional<String> getUnsupportedReason(KmuMarketUiContext context);
}
