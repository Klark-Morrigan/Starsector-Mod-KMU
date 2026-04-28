package kmu.console;

import kmu.conditions.KmuConditionService;
import kmu.conditions.StarsectorConditionRepository;
import kmu.ui.chooser.KmuConditionChooserEditor;
import kmu.ui.chooser.StarsectorInteractionDialogChooserOpener;
import kmu.ui.context.StarsectorMarketUiContextResolver;
import kmu.ui.editor.KmuConditionEditorEntryPoint;
import kmu.ui.editor.KmuConditionEditorOpenResult;
import kmu.ui.editor.KmuConditionEditorOpenStatus;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class KmuOpenConditionsCommand implements BaseCommand {
    private final Supplier<KmuConditionEditorOpenResult> openEditor;
    private final Consumer<String> output;

    public KmuOpenConditionsCommand() {
        this(() -> createDefaultEntryPoint().openForCurrentMarketDetailed(), message -> Console.showMessage(message));
    }

    KmuOpenConditionsCommand(
            Supplier<KmuConditionEditorOpenResult> openEditor,
            Consumer<String> output) {
        this.openEditor = Objects.requireNonNull(openEditor, "openEditor");
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        Objects.requireNonNull(context, "context");

        if (!context.isInCampaign()) {
            output.accept("kmu_open_conditions can only run from campaign or market context.");
            return CommandResult.WRONG_CONTEXT;
        }

        KmuConditionEditorOpenResult result;
        try {
            result = openEditor.get();
        } catch (RuntimeException exception) {
            output.accept("Failed to open planetary condition editor: " + exception.getMessage());
            return CommandResult.ERROR;
        }

        output.accept(messageFor(result));
        if (result.getStatus() == KmuConditionEditorOpenStatus.OPENED) {
            return CommandResult.SUCCESS;
        }
        if (result.getStatus() == KmuConditionEditorOpenStatus.NO_MARKET_CONTEXT
                || result.getStatus() == KmuConditionEditorOpenStatus.UNSUPPORTED_TARGET) {
            return CommandResult.WRONG_CONTEXT;
        }
        return CommandResult.ERROR;
    }

    private String messageFor(KmuConditionEditorOpenResult result) {
        if (result.getStatus() == KmuConditionEditorOpenStatus.FAILED
                && result.getCause().isPresent()
                && result.getCause().get().getMessage() != null
                && !result.getCause().get().getMessage().trim().isEmpty()) {
            String message = result.getMessage();
            if (message.endsWith(".")) {
                message = message.substring(0, message.length() - 1);
            }
            return message + ": " + result.getCause().get().getMessage();
        }
        return result.getMessage();
    }

    private static KmuConditionEditorEntryPoint createDefaultEntryPoint() {
        KmuConditionService conditionService = new KmuConditionService(new StarsectorConditionRepository());
        return new KmuConditionEditorEntryPoint(
                new StarsectorMarketUiContextResolver(),
                new KmuConditionChooserEditor(
                        conditionService,
                        new StarsectorInteractionDialogChooserOpener()));
    }
}
