package kmu.conditions.ui.editor;

import kmu.KmuErrorReporter;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextResolver;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionEditorEntryPoint {
    private final KmuMarketUiContextResolver contextResolver;
    private final KmuConditionEditor editor;
    private final KmuConditionEditorTargetValidator targetValidator;
    private final KmuErrorReporter errorReporter;

    public KmuConditionEditorEntryPoint(
            KmuMarketUiContextResolver contextResolver,
            KmuConditionEditor editor) {
        this(contextResolver, editor, new StarsectorConditionEditorTargetValidator(), KmuErrorReporter.noop());
    }

    public KmuConditionEditorEntryPoint(
            KmuMarketUiContextResolver contextResolver,
            KmuConditionEditor editor,
            KmuErrorReporter errorReporter) {
        this(contextResolver, editor, new StarsectorConditionEditorTargetValidator(), errorReporter);
    }

    public KmuConditionEditorEntryPoint(
            KmuMarketUiContextResolver contextResolver,
            KmuConditionEditor editor,
            KmuConditionEditorTargetValidator targetValidator,
            KmuErrorReporter errorReporter) {
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.targetValidator = Objects.requireNonNull(targetValidator, "targetValidator");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    public boolean openForCurrentMarket() {
        return openForCurrentMarketDetailed().isOpened();
    }

    public KmuConditionEditorOpenResult openForCurrentMarketDetailed() {
        Optional<KmuMarketUiContext> context;
        try {
            context = contextResolver.findCurrentMarketContext();
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve current market context.", exception);
            return KmuConditionEditorOpenResult.failed(
                    "Failed to resolve current market context.",
                    exception);
        }

        if (!context.isPresent()) {
            return KmuConditionEditorOpenResult.noMarketContext();
        }

        KmuMarketUiContext marketContext = context.get();
        Optional<String> unsupportedReason;
        try {
            unsupportedReason = targetValidator.getUnsupportedReason(marketContext);
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to validate MCM target.", exception);
            return KmuConditionEditorOpenResult.failed(
                    "Failed to validate MCM target.",
                    exception);
        }

        if (unsupportedReason.isPresent()) {
            return KmuConditionEditorOpenResult.unsupportedTarget(unsupportedReason.get());
        }

        try {
            editor.open(marketContext);
            return KmuConditionEditorOpenResult.opened();
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to open Market Condition Manager.", exception);
            return KmuConditionEditorOpenResult.failed(
                    "Failed to open Market Condition Manager.",
                    exception);
        }
    }
}
