package kmu.console;

import kmlib.console.KmlibBaseConsoleCommand;
import kmlib.console.output.CommandOutput;
import kmlib.console.output.ConsoleCommandOutput;
import kmlib.console.parsing.ParameterSpec;

import kmu.KmuErrorReporter;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.StarsectorConditionOfferPolicy;
import kmu.conditions.domain.StarsectorConditionRepository;
import kmu.conditions.ui.editor.KmuConditionEditorEntryPoint;
import kmu.conditions.ui.editor.KmuConditionEditorOpenResult;
import kmu.conditions.ui.editor.KmuConditionEditorOpenStatus;
import kmu.conditions.ui.picker.KmuConditionPickerEditor;
import kmu.conditions.ui.picker.dialog.StarsectorInteractionDialogPickerOpener;
import kmu.settings.KmuLunaSettings;
import kmu.ui.context.StarsectorMarketUiContextResolver;

import java.util.Objects;
import java.util.function.Supplier;

import static kmu.util.KmuValues.normalizeText;

public final class KmuOpenMarketConditionManagerCommand extends KmlibBaseConsoleCommand {
    // No parameters; declaring the spec still makes the parser reject a stray
    // argument as bad syntax rather than silently ignoring it.
    private static final ParameterSpec SPEC =
            ParameterSpec.takingNoArguments("Usage: kmu_mcm_open.");

    private final Supplier<KmuConditionEditorOpenResult> openEditor;

    public KmuOpenMarketConditionManagerCommand() {
        this(() -> createDefaultEntryPoint().openForCurrentMarketDetailed(), ConsoleCommandOutput.INSTANCE);
    }

    KmuOpenMarketConditionManagerCommand(
            Supplier<KmuConditionEditorOpenResult> openEditor,
            CommandOutput output) {
        super(output);
        this.openEditor = Objects.requireNonNull(openEditor, "openEditor");
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        Objects.requireNonNull(context, "context");

        // Defer the campaign-context guard to KMLib's shared validator so this
        // command's precondition check and feedback match every other KM
        // console command rather than maintaining a parallel inline check.
        var parsed = readInput(context, args)
                .requireCampaign()
                .parseArguments(SPEC);
        if (!parsed.isValid()) {
            return parsed.getResult();
        }

        KmuConditionEditorOpenResult result;
        try {
            result = openEditor.get();
        } catch (RuntimeException exception) {
            output.showMessage("Failed to open Market Condition Manager: " + exception.getMessage());
            return CommandResult.ERROR;
        }

        output.showMessage(messageFor(result));
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
        var causeMessage = result.getCause()
                .map(RuntimeException::getMessage)
                .map(message -> normalizeText(message))
                .orElse(null);
        if (result.getStatus() == KmuConditionEditorOpenStatus.FAILED
                && causeMessage != null) {
            var message = result.getMessage();
            if (message.endsWith(".")) {
                message = message.substring(0, message.length() - 1);
            }
            return message + ": " + causeMessage;
        }
        return result.getMessage();
    }

    private static KmuConditionEditorEntryPoint createDefaultEntryPoint() {
        var conditionService = new KmuConditionService(
                new StarsectorConditionRepository(),
                KmuErrorReporter.noop(),
                new StarsectorConditionOfferPolicy(KmuLunaSettings::shouldOfferAllConditions));
        return new KmuConditionEditorEntryPoint(
                new StarsectorMarketUiContextResolver(),
                new KmuConditionPickerEditor(
                        conditionService,
                        new StarsectorInteractionDialogPickerOpener()));
    }
}
