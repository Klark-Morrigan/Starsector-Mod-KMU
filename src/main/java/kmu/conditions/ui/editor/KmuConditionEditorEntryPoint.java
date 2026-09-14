package kmu.conditions.ui.editor;

import kmu.KmuErrorReporter;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextResolver;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionEditorEntryPoint {

    // Failure messages, each reported to the error sink and returned on the
    // result, so the two copies cannot drift.
    private static final String FAILED_TO_RESOLVE_MARKET_CONTEXT =
        "Failed to resolve current market context.";
    private static final String FAILED_TO_VALIDATE_TARGET =
        "Failed to validate MCM target.";
    private static final String FAILED_TO_OPEN_MANAGER =
        "Failed to open Market Condition Manager.";

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
            errorReporter.report(FAILED_TO_RESOLVE_MARKET_CONTEXT, exception);
            return KmuConditionEditorOpenResult.failed(
                FAILED_TO_RESOLVE_MARKET_CONTEXT,
                exception);
        }

        if (!context.isPresent()) {
            return KmuConditionEditorOpenResult.noMarketContext();
        }

        var marketContext = context.get();
        Optional<String> unsupportedReason;
        try {
            unsupportedReason = targetValidator.getUnsupportedReason(marketContext);
        } catch (RuntimeException exception) {
            errorReporter.report(FAILED_TO_VALIDATE_TARGET, exception);
            return KmuConditionEditorOpenResult.failed(
                FAILED_TO_VALIDATE_TARGET,
                exception);
        }

        if (unsupportedReason.isPresent()) {
            return KmuConditionEditorOpenResult.unsupportedTarget(unsupportedReason.get());
        }

        try {
            editor.open(marketContext);
            return KmuConditionEditorOpenResult.opened();
        } catch (RuntimeException exception) {
            errorReporter.report(FAILED_TO_OPEN_MANAGER, exception);
            return KmuConditionEditorOpenResult.failed(
                FAILED_TO_OPEN_MANAGER,
                exception);
        }
    }
}
