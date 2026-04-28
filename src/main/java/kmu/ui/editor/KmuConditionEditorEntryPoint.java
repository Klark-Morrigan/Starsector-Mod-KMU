package kmu.ui.editor;

import kmu.KmuErrorReporter;
import kmu.ui.context.KmuMarketUiContext;
import kmu.ui.context.KmuMarketUiContextResolver;

import java.util.Objects;
import java.util.Optional;

public final class KmuConditionEditorEntryPoint {
    private final KmuMarketUiContextResolver contextResolver;
    private final KmuConditionEditor editor;
    private final KmuErrorReporter errorReporter;

    public KmuConditionEditorEntryPoint(
            KmuMarketUiContextResolver contextResolver,
            KmuConditionEditor editor) {
        this(contextResolver, editor, KmuErrorReporter.noop());
    }

    public KmuConditionEditorEntryPoint(
            KmuMarketUiContextResolver contextResolver,
            KmuConditionEditor editor,
            KmuErrorReporter errorReporter) {
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver");
        this.editor = Objects.requireNonNull(editor, "editor");
        this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
    }

    public boolean openForCurrentMarket() {
        Optional<KmuMarketUiContext> context;
        try {
            context = contextResolver.findCurrentMarketContext();
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to resolve current market context.", exception);
            return false;
        }

        if (!context.isPresent()) {
            return false;
        }

        try {
            editor.open(context.get());
            return true;
        } catch (RuntimeException exception) {
            errorReporter.report("Failed to open planetary condition editor.", exception);
            return false;
        }
    }
}
